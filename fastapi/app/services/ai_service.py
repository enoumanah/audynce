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

    # MODIFIED signature to accept top_artists
    async def analyze_prompt(self, prompt: str, selected_genres: List[str], story_threshold: int, top_artists: List[str]) -> AIAnalysisResponse:
        word_count = len(prompt.split())
        mode = AnalysisMode.STORY if word_count >= story_threshold else AnalysisMode.DIRECT
        logger.info(f"Analyzing prompt ({word_count} words) in {mode} mode")

        analysis_id = f"ai-{hash(prompt)}-{int(time.time())}"

        if mode == AnalysisMode.STORY:
            scenes = await self._analyze_story(prompt, selected_genres, top_artists) # Pass top_artists
            return AIAnalysisResponse(analysis_id=analysis_id, mode=mode, scenes=scenes)
        else:
            direct = await self._analyze_direct(prompt, selected_genres, top_artists) # Pass top_artists
            return AIAnalysisResponse(analysis_id=analysis_id, mode=mode, direct_analysis=direct)

    # MODIFIED signature
    async def _analyze_story(self, prompt: str, genres: List[str], top_artists: List[str]) -> List[SceneAnalysis]:
        system_prompt = build_story_prompt(prompt, genres, top_artists) # Pass top_artists
        try:
            response = self._call_huggingface(system_prompt)
            return self._parse_story_response(response, genres)
        except Exception as e:
            logger.error(f"Story analysis failed: {e}")
            return self._fallback_story_scenes(prompt, genres, top_artists) # Pass to fallback

    # MODIFIED signature
    async def _analyze_direct(self, prompt: str, genres: List[str], top_artists: List[str]) -> DirectModeAnalysis:
        system_prompt = build_direct_prompt(prompt, genres, top_artists) # Pass top_artists
        try:
            response = self._call_huggingface(system_prompt)
            return self._parse_direct_response(response, genres)
        except Exception as e:
            logger.error(f"Direct analysis failed: {e}")
            return self._fallback_direct_analysis(prompt, genres, top_artists) # Pass to fallback

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
            # Pass empty list for top_artists to fallback if parsing fails
            return self._fallback_story_scenes("", genres, [])

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
            # Pass empty list for top_artists to fallback if parsing fails
            return self._fallback_direct_analysis("", genres, [])

    def _extract_json(self, text: str) -> dict:
        start, end = text.find('{'), text.rfind('}') + 1
        if start == -1 or end <= start:
            raise ValueError("No JSON detected in model output")
        return json.loads(text[start:end])

    # MODIFIED fallbacks to use top_artists
    def _fallback_story_scenes(self, prompt: str, selected_genres: List[str], top_artists: List[str]) -> List[SceneAnalysis]:
        logger.info("Using rule-based fallback for story scenes")
        genre_str = " ".join(selected_genres) if selected_genres else "pop"
        return [
            SceneAnalysis(scene_number=1, description="Opening - Setting the mood", search_query=f"peaceful {genre_str}"),
            SceneAnalysis(scene_number=2, description="Development - Rising action", search_query=f"upbeat {genre_str}"),
            SceneAnalysis(scene_number=3, description="Climax - Emotional high point", search_query=f"intense {genre_str} epic"),
            SceneAnalysis(scene_number=4, description="Resolution - Calm ending", search_query=f"nostalgic {genre_str} chill")
        ]

    def _fallback_direct_analysis(self, prompt: str, selected_genres: List[str], top_artists: List[str]) -> DirectModeAnalysis:
        logger.info("Using rule-based fallback for direct analysis")
        genre_str = " ".join(selected_genres) if selected_genres else "pop indie"
        return DirectModeAnalysis(
            theme=prompt[:50] if prompt else "General playlist",
            search_query=f"{prompt} {genre_str}"
        )
