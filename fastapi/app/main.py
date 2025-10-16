# app/main.py
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from contextlib import asynccontextmanager
import logging
import os
import uvicorn

from app.models.schemas import AnalysisRequest, AIAnalysisResponse
from app.services.ai_service import AIService
from app.services.cache_service import cache_service
from app.config.settings import settings

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

@asynccontextmanager
async def lifespan(app: FastAPI):
    # Startup
    logger.info("Starting FastAPI service...")
    await cache_service.connect()
    yield
    # Shutdown
    logger.info("Shutting down FastAPI service...")
    await cache_service.close()

app = FastAPI(
    title="Audynce AI Service",
    description="AI-powered story analysis for music playlist generation",
    version="1.0.0",
    lifespan=lifespan
)

# CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

ai_service = AIService()

@app.get("/")
async def root():
    return {
        "service": "Audynce AI",
        "status": "running",
        "model": settings.model_name
    }

@app.get("/health")
async def health_check():
    return {
        "status": "healthy",
        "model": settings.model_name,
        "mongodb": "connected" if cache_service.collection else "disconnected"
    }

@app.post("/ai/analyze", response_model=AIAnalysisResponse)
async def analyze_story(request: AnalysisRequest):
    """
    Analyze a story or prompt and return structured scene breakdown
    """
    try:
        logger.info(f"Received analysis request: {len(request.prompt)} chars")
        
        # Check cache first
        prompt_hash = str(hash(request.prompt))
        cached = await cache_service.get_cached_analysis(prompt_hash)
        
        if cached:
            logger.info("Returning cached analysis")
            return AIAnalysisResponse(**cached)
        
        # Analyze with AI
        analysis = await ai_service.analyze_prompt(
            request.prompt,
            request.selected_genres,
            request.story_threshold
        )
        
        # Cache the result
        await cache_service.cache_analysis(prompt_hash, analysis.dict())
        
        return analysis
        
    except Exception as e:
        logger.error(f"Error in analyze_story: {e}")
        raise HTTPException(status_code=500, detail=str(e))

if __name__ == "__main__":
    port = int(os.environ.get("PORT", 8000))
    uvicorn.run(app, host="0.0.0.0", port=port)