# app/utils/prompt_builder.py
from typing import List

def build_story_prompt(narrative: str, genres: List[str]) -> str:
    """Build prompt for story mode analysis"""
    genre_str = ", ".join(genres) if genres else "various genres"
    
    return f"""Analyze this story and break it down into 3-6 musical scenes.

Story: {narrative}

User's preferred genres: {genre_str}

Respond ONLY with valid JSON in this exact format:
{{
  "scenes": [
    {{
      "description": "brief scene description",
      "search_query": "A precise Spotify search query for this scene. Combine musical keywords, moods, and relevant genres from the user's preferences. Example: 'slow ambient hopeful sunrise' or 'chaotic high-energy world music market'"
    }}
  ]
}}

Rules:
- Maximum 6 scenes
- Keep descriptions under 100 characters
- The "search_query" MUST be a string optimized for the Spotify search API.
- The "search_query" should incorporate the scene's mood AND the user's preferred genres."""

def build_direct_prompt(prompt: str, genres: List[str]) -> str:
    """Build prompt for direct mode analysis"""
    genre_str = ", ".join(genres) if genres else "any genre"
    
    return f"""Analyze this music request and extract key information.

Request: {prompt}

User's preferred genres: {genre_str}

Respond ONLY with valid JSON in this exact format:
{{
  "theme": "A short, catchy theme or title for this playlist. Example: 'Rainy Day Reading' or 'Cyberpunk Chase'",
  "search_query": "A precise Spotify search query to find this vibe. Combine the user's prompt keywords, mood, and preferred genres. Example: 'chill rainy afternoon acoustic jazz' or 'dark futuristic electronic' or 'afrobeats for driving'"
}}

Rules:
- "theme" should be under 50 characters.
- "search_query" MUST be a string optimized for the Spotify search API.
- "search_query" MUST include relevant user preferred genres.
- "search_query" MUST NOT include non-musical keywords like 'Lekki' or 'traffic'. Infer the vibe (e.g., 'driving' or 'frustrated') instead."""