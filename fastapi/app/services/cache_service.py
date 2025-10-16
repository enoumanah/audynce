# app/services/cache_service.py
from motor.motor_asyncio import AsyncIOMotorClient
from app.config.settings import settings
import logging
from datetime import datetime

logger = logging.getLogger(__name__)

class CacheService:
    def __init__(self):
        self.client = None
        self.db = None
        self.collection = None
    
    async def connect(self):
        """Connect to MongoDB"""
        try:
            self.client = AsyncIOMotorClient(settings.mongodb_uri)
            self.db = self.client[settings.mongodb_database]
            self.collection = self.db["ai_analysis_cache"]
            logger.info("Connected to MongoDB")
        except Exception as e:
            logger.error(f"MongoDB connection failed: {e}")
    
    async def close(self):
        """Close MongoDB connection"""
        if self.client is not None:
            self.client.close()
            logger.info("Closed MongoDB connection")
    
    async def get_cached_analysis(self, prompt_hash: str):
        """Retrieve cached analysis"""
        if self.collection is None:
            return None
        
        try:
            result = await self.collection.find_one({"prompt_hash": prompt_hash})
            return result
        except Exception as e:
            logger.error(f"Error retrieving from cache: {e}")
            return None
    
    async def cache_analysis(self, prompt_hash: str, analysis: dict):
        """Store analysis in cache"""
        if self.collection is None:
            return
        
        try:
            await self.collection.update_one(
                {"prompt_hash": prompt_hash},
                {"$set": {**analysis, "cached_at": datetime.utcnow()}},
                upsert=True
            )
            logger.info(f"Cached analysis for hash: {prompt_hash}")
        except Exception as e:
            logger.error(f"Error caching analysis: {e}")

cache_service = CacheService()
