# app/utils/prompt_builder.py
from typing import List

def build_story_prompt(narrative: str, genres: List[str]) -> str:
    """Build prompt for story mode analysis"""
    genre_str = ", ".join(genres) if genres else "various genres"
    
    return f"""Analyze this story and break it down into 3-6 musical scenes. For each scene, identify the mood and suggest music that would match.

Story: {narrative}

Preferred genres: {genre_str}

Respond ONLY with valid JSON in this exact format:
{{
  "scenes": [
    {{
      "description": "brief scene description",
      "mood": "UPBEAT|MELANCHOLIC|ROMANTIC|ADVENTUROUS|PEACEFUL|ENERGETIC|INTENSE|NOSTALGIC|DREAMY|CHILL|BALANCED",
      "genres": ["genre1", "genre2"],
      "energy": "high|medium|low"
    }}
  ]
}}

Rules:
- Maximum 6 scenes
- Mood must be ONE of the listed options in CAPS
- Keep descriptions under 100 characters
- Match genres to the scene mood"""

def build_direct_prompt(prompt: str, genres: List[str]) -> str:
    """Build prompt for direct mode analysis"""
    genre_str = ", ".join(genres) if genres else "any genre"
    
    return f"""Analyze this music request and extract key information for playlist generation.

Request: {prompt}

User's preferred genres: {genre_str}

Respond ONLY with valid JSON in this exact format:
{{
  "mood": "UPBEAT|MELANCHOLIC|ROMANTIC|ADVENTUROUS|PEACEFUL|ENERGETIC|INTENSE|NOSTALGIC|DREAMY|CHILL|BALANCED",
  "genres": ["genre1", "genre2", "genre3"],
  "keywords": ["keyword1", "keyword2"],
  "theme": "brief description"
}}

Rules:
- Mood must be ONE of the listed options in CAPS
- Include 2-4 genres
- Extract 3-5 keywords
- Theme should be under 50 characters"""