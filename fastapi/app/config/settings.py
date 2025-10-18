# app/config/settings.py
from pathlib import Path
from pydantic_settings import BaseSettings
from typing import Optional

class Settings(BaseSettings):
    huggingface_token: Optional[str] = None
    huggingface_model: str = "meta-llama/Llama-3.2-3B-Instruct"
    mongodb_uri: Optional[str] = None
    mongodb_database: str = "audynce"
    story_mode_threshold: int = 80
    max_scenes: int = 5
    port: int = 8000

    model_config = {
        "env_file": ".env",
        "env_file_encoding": "utf-8",
        "protected_namespaces": ()
    }

settings = Settings()