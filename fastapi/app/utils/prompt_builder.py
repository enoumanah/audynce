# app/utils/prompt_builder.py
from typing import List

def build_story_prompt(narrative: str, genres: List[str], top_artists: List[str]) -> str: # Added top_artists
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
- **Match genres to the scene mood and User's preferred genres.**""" # <-- Added rule

def build_direct_prompt(prompt: str, genres: List[str], top_artists: List[str]) -> str: # Added top_artists
    """Build prompt for direct mode analysis"""
    genre_str = ", ".join(genres) if genres else "any genre"
    
    return f"""Analyze this music request and extract key information for playlist generation.

Request: {prompt}

User's preferred genres: {genre_str}{personalization_str}

Respond ONLY with valid JSON in this exact format:
{{
  "mood": "UPBEAT|MELANCHOLIC|ROMANTIC|ADVENTUROUS|PEACEFUL|ENERGETIC|INTENSE|NOSTALGIC|DREAMY|CHILL|BALANCED",
  "genres": ["genre1", "genre2", "genre3"],
  "keywords": ["keyword1", "keyword2", "keyword3"],
  "theme": "brief description"
}}

Rules:
- Mood must be ONE of the listed options in CAPS
- **The 'genres' list MUST prioritize the User's preferred genres if they are relevant to the Request.**
- **The 'keywords' list MUST contain 3-5 MUSICAL keywords, adjectives, or vibes from the Request (e.g., 'chill', 'soulful', 'party'). Do NOT include locations (like 'Lekki') or non-musical nouns (like 'traffic').**
- Theme should be under 50 characters""" 