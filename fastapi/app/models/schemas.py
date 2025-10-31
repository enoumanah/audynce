# app/models/schemas.py
from pydantic import BaseModel, Field
from typing import List, Optional
from enum import Enum

class MoodType(str, Enum):
    UPBEAT = "UPBEAT"
    MELANCHOLIC = "MELANCHOLIC"
    ROMANTIC = "ROMANTIC"
    ADVENTUROUS = "ADVENTUROUS"
    PEACEFUL = "PEACEFUL"
    ENERGETIC = "ENERGETIC"
    INTENSE = "INTENSE"
    NOSTALGIC = "NOSTALGIC"
    DREAMY = "DREAMY"
    CHILL = "CHILL"
    BALANCED = "BALANCED"

class AnalysisMode(str, Enum):
    STORY = "STORY"
    DIRECT = "DIRECT"

class AnalysisRequest(BaseModel):
    prompt: str = Field(..., min_length=10, max_length=2000)
    selected_genres: List[str] = Field(default_factory=list)
    story_threshold: int = 100

class SceneAnalysis(BaseModel):
    scene_number: int
    description: str
    
    search_query: str = Field(..., description="The final Spotify search query for this scene")

class DirectModeAnalysis(BaseModel):
    theme: str 
    search_query: str = Field(..., description="The final Spotify search query for this request")

class AIAnalysisResponse(BaseModel):
    analysis_id: str
    mode: AnalysisMode
    scenes: Optional[List[SceneAnalysis]] = None
    direct_analysis: Optional[DirectModeAnalysis] = None