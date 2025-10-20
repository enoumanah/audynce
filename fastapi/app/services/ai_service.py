# app/services/ai_service.py
import json
import logging
import time
import os
from typing import Dict, List
from huggingface_hub import InferenceClient
from app.config.settings import settings
from app.models.schemas import (
    AnalysisMode, MoodType, SceneAnalysis, 
    DirectModeAnalysis, AIAnalysisResponse
)
from app.utils.prompt_builder import build_story_prompt, build_direct_prompt

logger = logging.getLogger(__name__)

class AIService:
    def __init__(self):
        # Get token from environment
        token = os.environ.get('HUGGINGFACE_TOKEN')
        
        if not token:
            logger.warning("No HUGGINGFACE_TOKEN found in environment!")
        else:
            logger.info(f"HuggingFace token found: {token[:10]}...")
        
        # Initialize client with token
        self.client = InferenceClient(token=token)

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
        """Call HuggingFace using the official InferenceClient."""
        
        try:
            logger.info(f"Calling HuggingFace model: {settings.huggingface_model}")
            
            # Try multiple approaches
            try:
                # Approach 1: text_generation (synchronous call)
                response = self.client.text_generation(
                    prompt=prompt,
                    model=settings.huggingface_model,
                    max_new_tokens=600,
                    temperature=0.7,
                    top_p=0.9,
                    do_sample=True,
                    return_full_text=False
                )
                
                logger.info("HuggingFace API call successful (text_generation)")
                return response.strip()
                
            except Exception as e1:
                logger.warning(f"text_generation failed: {e1}, trying chat_completion...")
                
                # Approach 2: Try chat_completion format
                try:
                    messages = [{"role": "user", "content": prompt}]
                    response = self.client.chat_completion(
                        messages=messages,
                        model=settings.huggingface_model,
                        max_tokens=600,
                        temperature=0.7
                    )
                    
                    content = response.choices[0].message.content
                    logger.info("HuggingFace API call successful (chat_completion)")
                    return content.strip()
                    
                except Exception as e2:
                    logger.error(f"chat_completion also failed: {e2}")
                    raise e1  # Raise the original error
                    
        except Exception as e:
            error_msg = str(e)
            logger.error(f"Error calling HuggingFace API: {error_msg}")
            
            # Check for specific error types
            if "404" in error_msg:
                logger.error(f"Model not found: {settings.huggingface_model}")
                logger.error("This could mean:")
                logger.error("1. Model name is incorrect")
                logger.error("2. Model requires gated access (check HuggingFace)")
                logger.error("3. Model is not available on serverless API")
            elif "401" in error_msg or "403" in error_msg:
                logger.error("Authentication error - check HUGGINGFACE_TOKEN")
            elif "503" in error_msg or "loading" in error_msg.lower():
                logger.warning("Model is loading, this is temporary")
            
            raise

    def _parse_story_response(self, response: str, selected_genres: List[str]) -> List[SceneAnalysis]:
        """Parse AI response into scene objects"""
        try:
            # Try to find JSON in the response
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
                
                if scenes:
                    return scenes
                    
        except json.JSONDecodeError as e:
            logger.warning(f"Failed to parse JSON response: {e}. Response: {response[:200]}")
        except Exception as e:
            logger.warning(f"Error parsing response: {e}")
            
        return self._fallback_story_scenes("", selected_genres)

    def _parse_direct_response(self, response: str, selected_genres: List[str]) -> DirectModeAnalysis:
        """Parse AI response for direct mode"""
        try:
            # Try to find JSON in the response
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
                
        except json.JSONDecodeError as e:
            logger.warning(f"Failed to parse JSON response: {e}. Response: {response[:200]}")
        except Exception as e:
            logger.warning(f"Error parsing response: {e}")
            
        return self._fallback_direct_analysis("", selected_genres)

    def _fallback_story_scenes(self, prompt: str, selected_genres: List[str]) -> List[SceneAnalysis]:
        """Fallback scenes when AI fails"""
        logger.info("Using fallback story scenes")
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
        logger.info("Using fallback direct analysis")
        return DirectModeAnalysis(
            mood=MoodType.BALANCED,
            extracted_genres=selected_genres if selected_genres else ["pop", "indie"],
            keywords=["music", "playlist"],
            theme=prompt[:50] if prompt else "General playlist"
        )