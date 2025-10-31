# app/config/settings.py
from pathlib import Path
from pydantic_settings import BaseSettings
from typing import Optional

class Settings(BaseSettings):
    huggingface_token: Optional[str] = None
    
    # Best model that works on HuggingFace Inference API (Free tier)
    # This model is well-supported and works without providers
    huggingface_model: str = "meta-llama/Llama-3.3-70B-Instruct"
    
    # Alternative models if the above doesn't work (will auto-fallback):
    # - "meta-llama/Llama-3.2-3B-Instruct" (smaller, faster)
    # - "microsoft/phi-2" (very reliable)
    # - "mistralai/Mistral-7B-Instruct-v0.2" (solid backup)
    
    mongodb_uri: Optional[str] = None
    mongodb_database: str = "audynce"
    story_mode_threshold: int = 30
    max_scenes: int = 5
    port: int = 8000

    model_config = {
        "env_file": ".env",
        "env_file_encoding": "utf-8",
        "protected_namespaces": ()
    }

settings = Settings()