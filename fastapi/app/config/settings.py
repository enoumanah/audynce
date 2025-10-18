# app/config/settings.py
from pathlib import Path
from pydantic_settings import BaseSettings
from typing import Optional

class Settings(BaseSettings):
    huggingface_token: Optional[str] = None
    huggingface_model: str = "mistralai/Mistral-7B-Instruct-v0.3"
    mongodb_uri: Optional[str] = None
    mongodb_database: str = "audynce"
    story_mode_threshold: int = 80
    max_scenes: int = 5
    port: int = 8000

    model_config = {
        "env_file": ".env",
        "env_file_encoding": "utf-8",
        # This tells Pydantic to not protect the 'model_' namespace
        "protected_namespaces": ()
    }

settings = Settings()