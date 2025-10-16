# app/config/settings.py
from pathlib import Path
from pydantic_settings import BaseSettings
from typing import Optional

class Settings(BaseSettings):
    huggingface_token: Optional[str] = None
    model_name: str = "mistralai/Mistral-7B-Instruct-v0.2"
    mongodb_uri: Optional[str] = None
    mongodb_database: str = "audynce"
    story_mode_threshold: int = 80
    max_scenes: int = 5
    port: int = 8000

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"

settings = Settings()
