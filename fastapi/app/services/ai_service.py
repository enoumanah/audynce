# app/services/ai_service.py
import os
import json
import logging
import time
from typing import List, Optional
from openai import OpenAI

from app.config.settings import settings
from app.models.schemas import (
    AnalysisMode, MoodType, SceneAnalysis,
    DirectModeAnalysis, AIAnalysisResponse
)
from app.utils.prompt_builder import build_story_prompt, build_direct_prompt

logger = logging.getLogger(__name__)

class AIService:
    def __init__(self):
        token = settings.huggingface_token or os.getenv("HF_TOKEN")
        if not token:
            logger.warning("⚠️ Missing HF_TOKEN in environment or .env file!")
        else:
            logger.info(f"✓ HuggingFace token loaded ({token[:10]}...)")

        self.client = OpenAI(
            base_url="https://router.huggingface.co/v1",
            api_key=token
        )
        self.model = "meta-llama/Llama-3.3-70B-Instruct:groq"

    async def analyze_prompt(self, prompt: str, selected_genres: List[str], story_threshold: int) -> AIAnalysisResponse:
        word_count = len(prompt.split())
        mode = AnalysisMode.STORY if word_count >= story_threshold else AnalysisMode.DIRECT
        logger.info(f"Analyzing prompt ({word_count} words) in {mode} mode")

        analysis_id = f"ai-{hash(prompt)}-{int(time.time())}"

        if mode == AnalysisMode.STORY:
            scenes = await self._analyze_story(prompt, selected_genres)
            return AIAnalysisResponse(analysis_id=analysis_id, mode=mode, scenes=scenes)
        else:
            direct = await self._analyze_direct(prompt, selected_genres)
            return AIAnalysisResponse(analysis_id=analysis_id, mode=mode, direct_analysis=direct)

    async def _analyze_story(self, prompt: str, genres: List[str]) -> List[SceneAnalysis]:
        system_prompt = build_story_prompt(prompt, genres)
        try:
            response = self._call_huggingface(system_prompt)
            return self._parse_story_response(response, genres)
        except Exception as e:
            logger.error(f"Story analysis failed: {e}")
            return self._fallback_story_scenes(prompt, genres)

    async def _analyze_direct(self, prompt: str, genres: List[str]) -> DirectModeAnalysis:
        system_prompt = build_direct_prompt(prompt, genres)
        try:
            response = self._call_huggingface(system_prompt)
            return self._parse_direct_response(response, genres)
        except Exception as e:
            logger.error(f"Direct analysis failed: {e}")
            return self._fallback_direct_analysis(prompt, genres)

    def _call_huggingface(self, prompt: str) -> str:
        try:
            logger.info(f"Calling HF router model: {self.model}")
            completion = self.client.chat.completions.create(
                model=self.model,
                messages=[
                    {"role": "system", "content": "You are a helpful AI that returns structured JSON only."},
                    {"role": "user", "content": prompt}
                ],
                max_tokens=800,
                temperature=0.7
            )

            content = completion.choices[0].message.content or ""
            logger.info(f"✓ API call successful ({len(content)} chars)")
            return content.strip()
        except Exception as e:
            logger.error(f"❌ API call failed: {e}")
            raise

    def _parse_story_response(self, response: str, genres: List[str]) -> List[SceneAnalysis]:
        try:
            json_data = self._extract_json(response)
            scenes = []
            for i, sc in enumerate(json_data.get("scenes", [])[:settings.max_scenes], 1):
                scenes.append(SceneAnalysis(
                    scene_number=i,
                    description=sc.get("description", f"Scene {i}"),
                    mood=MoodType(sc.get("mood", "BALANCED")),
                    suggested_genres=sc.get("genres", genres[:3]),
                    energy_level=sc.get("energy", "medium")
                ))
            logger.info(f"✓ Parsed {len(scenes)} scenes")
            return scenes
        except Exception as e:
            logger.warning(f"Failed to parse story response: {e}")
            return self._fallback_story_scenes("", genres)

    def _parse_direct_response(self, response: str, genres: List[str]) -> DirectModeAnalysis:
        try:
            json_data = self._extract_json(response)
            return DirectModeAnalysis(
                mood=MoodType(json_data.get("mood", "BALANCED")),
                extracted_genres=json_data.get("genres", genres),
                keywords=json_data.get("keywords", []),
                theme=json_data.get("theme", "Music playlist")
            )
        except Exception as e:
            logger.warning(f"Failed to parse direct response: {e}")
            return self._fallback_direct_analysis("", genres)

    def _extract_json(self, text: str) -> dict:
        start, end = text.find('{'), text.rfind('}') + 1
        if start == -1 or end <= start:
            raise ValueError("No JSON detected in model output")
        return json.loads(text[start:end])

    def _fallback_story_scenes(self, prompt: str, selected_genres: List[str]) -> List[SceneAnalysis]:
        logger.info("Using rule-based fallback for story scenes")
        return [
            SceneAnalysis(scene_number=1, description="Opening - Setting the mood", mood=MoodType.PEACEFUL, suggested_genres=selected_genres[:3] or ["pop"], energy_level="low"),
            SceneAnalysis(scene_number=2, description="Development - Rising action", mood=MoodType.UPBEAT, suggested_genres=selected_genres[:3] or ["pop"], energy_level="medium"),
            SceneAnalysis(scene_number=3, description="Climax - Emotional high point", mood=MoodType.INTENSE, suggested_genres=selected_genres[:3] or ["rock"], energy_level="high"),
            SceneAnalysis(scene_number=4, description="Resolution - Calm ending", mood=MoodType.NOSTALGIC, suggested_genres=selected_genres[:3] or ["indie"], energy_level="medium")
        ]

    def _fallback_direct_analysis(self, prompt: str, selected_genres: List[str]) -> DirectModeAnalysis:
        logger.info("Using rule-based fallback for direct analysis")
        return DirectModeAnalysis(
            mood=MoodType.BALANCED,
            extracted_genres=selected_genres or ["pop", "indie"],
            keywords=prompt.split()[:5],
            theme=prompt[:50] if prompt else "General playlist"
        )
