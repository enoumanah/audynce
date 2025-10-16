import httpx
import json
import logging
import time
import os
from typing import Dict, List
from app.config.settings import settings
from app.models.schemas import (
    AnalysisMode, MoodType, SceneAnalysis, 
    DirectModeAnalysis, AIAnalysisResponse
)
from app.utils.prompt_builder import build_story_prompt, build_direct_prompt

logger = logging.getLogger(__name__)

class AIService:
    def __init__(self):
        self.api_url = "https://api-inference.huggingface.co/v1/chat/completions"
        self.headers = {
            "Authorization": f"Bearer {os.environ.get('HUGGINGFACE_TOKEN')}",
            "Content-Type": "application/json",
        }

    async def analyze_prompt(self, prompt: str, selected_genres: List[str], story_threshold: int) -> AIAnalysisResponse:
        """Main analysis method - determines mode and analyzes accordingly"""
        word_count = len(prompt.split())
        mode = AnalysisMode.STORY if word_count >= story_threshold else AnalysisMode.DIRECT
        
        logger.info(f"Analyzing prompt in {mode} mode (words: {word_count})")
        
        analysis_id = f"ai-{hash(prompt)}-{int(time.time())}"
        
        if mode == AnalysisMode.STORY:
            scenes = await self._analyze_story(prompt, selected_genres)
            return AIAnalysisResponse(
                analysis_id=analysis_id,
                mode=mode,
                scenes=scenes
            )
        else:
            direct_analysis = await self._analyze_direct(prompt, selected_genres)
            return AIAnalysisResponse(
                analysis_id=analysis_id,
                mode=mode,
                direct_analysis=direct_analysis
            )

    async def _analyze_story(self, prompt: str, selected_genres: List[str]) -> List[SceneAnalysis]:
        """Analyze a long story into multiple scenes"""
        system_prompt = build_story_prompt(prompt, selected_genres)
        try:
            response = await self._call_huggingface(system_prompt)
            return self._parse_story_response(response, selected_genres)
        except Exception as e:
            logger.error(f"Error in story analysis: {e}")
            return self._fallback_story_scenes(prompt, selected_genres)

    async def _analyze_direct(self, prompt: str, selected_genres: List[str]) -> DirectModeAnalysis:
        """Analyze a short prompt for direct playlist generation"""
        system_prompt = build_direct_prompt(prompt, selected_genres)
        try:
            response = await self._call_huggingface(system_prompt)
            return self._parse_direct_response(response, selected_genres)
        except Exception as e:
            logger.error(f"Error in direct analysis: {e}")
            return self._fallback_direct_analysis(prompt, selected_genres)

    async def _call_huggingface(self, prompt: str) -> str:
        """Call Hugging Face Inference API (text generation endpoint)."""
        api_url = f"https://api-inference.huggingface.co/models/{settings.model_name}"

        payload = {
            "inputs": prompt,
            "parameters": {
                "max_new_tokens": 512,
                "temperature": 0.7,
                "top_p": 0.9,
                "return_full_text": False
            },
            "options": {"wait_for_model": True}
        }

        async with httpx.AsyncClient(timeout=90.0) as client:
            response = await client.post(api_url, headers=self.headers, json=payload)

            # Raise for non-2xx responses
            response.raise_for_status()

            result = response.json()

            # Handle expected result structure
            if isinstance(result, list) and len(result) > 0 and "generated_text" in result[0]:
                return result[0]["generated_text"].strip()

            # Handle inference errors
            elif isinstance(result, dict) and "error" in result:
                raise Exception(f"HuggingFace error: {result['error']}")

            else:
                return str(result)


    def _parse_story_response(self, response: str, selected_genres: List[str]) -> List[SceneAnalysis]:
        """Parse AI response into scene objects"""
        try:
            json_start = response.find('{')
            json_end = response.rfind('}') + 1
            if json_start != -1 and json_end > json_start:
                json_str = response[json_start:json_end]
                data = json.loads(json_str)
                
                scenes = []
                for i, scene_data in enumerate(data.get("scenes", [])[:settings.max_scenes], 1):
                    scenes.append(SceneAnalysis(
                        scene_number=i,
                        description=scene_data.get("description", f"Scene {i}"),
                        mood=MoodType(scene_data.get("mood", "BALANCED")),
                        suggested_genres=scene_data.get("genres", selected_genres[:3]),
                        energy_level=scene_data.get("energy", "medium")
                    ))
                return scenes
        except Exception as e:
            logger.warning(f"Failed to parse JSON response: {e}")
        return self._fallback_story_scenes("", selected_genres)

    def _parse_direct_response(self, response: str, selected_genres: List[str]) -> DirectModeAnalysis:
        """Parse AI response for direct mode"""
        try:
            json_start = response.find('{')
            json_end = response.rfind('}') + 1
            if json_start != -1 and json_end > json_start:
                json_str = response[json_start:json_end]
                data = json.loads(json_str)
                
                return DirectModeAnalysis(
                    mood=MoodType(data.get("mood", "BALANCED")),
                    extracted_genres=data.get("genres", selected_genres),
                    keywords=data.get("keywords", []),
                    theme=data.get("theme", "Music playlist")
                )
        except Exception as e:
            logger.warning(f"Failed to parse direct response: {e}")
        return self._fallback_direct_analysis("", selected_genres)

    def _fallback_story_scenes(self, prompt: str, selected_genres: List[str]) -> List[SceneAnalysis]:
        """Fallback scenes when AI fails"""
        return [
            SceneAnalysis(
                scene_number=1,
                description="Opening - Setting the mood",
                mood=MoodType.PEACEFUL,
                suggested_genres=selected_genres[:3] if selected_genres else ["pop"],
                energy_level="low"
            ),
            SceneAnalysis(
                scene_number=2,
                description="Development - Building energy",
                mood=MoodType.UPBEAT,
                suggested_genres=selected_genres[:3] if selected_genres else ["pop"],
                energy_level="high"
            ),
            SceneAnalysis(
                scene_number=3,
                description="Resolution - Bringing it home",
                mood=MoodType.NOSTALGIC,
                suggested_genres=selected_genres[:3] if selected_genres else ["indie"],
                energy_level="medium"
            )
        ]

    def _fallback_direct_analysis(self, prompt: str, selected_genres: List[str]) -> DirectModeAnalysis:
        """Fallback for direct mode when AI fails"""
        return DirectModeAnalysis(
            mood=MoodType.BALANCED,
            extracted_genres=selected_genres if selected_genres else ["pop", "indie"],
            keywords=["music", "playlist"],
            theme=prompt[:50] if prompt else "General playlist"
        )
