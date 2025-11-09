# Audynce AI Service (FastAPI)

This is the AI microservice for the Audynce application. It is a lightweight, high-performance API built with FastAPI.

Its **sole purpose** is to act as the "creative translator" for the application. It receives a raw, natural language prompt from the Spring Boot backend and translates it into a precise, optimized Spotify search query.

-----

## 🚀 Core Responsibilities

  * **Vibe-to-Query Translation:** This is the service's primary function. It takes a prompt like *"A chill, rainy afternoon for reading a book"* and, using a Large Language Model (LLM), converts it into a query string like `"chill rainy day acoustic instrumental"`.
  * **Story Mode Detection:** Analyzes the length of a prompt to determine if it's a simple "Direct Mode" request or a complex "Story Mode" narrative.
  * **Scene Generation (for Story Mode):** If Story Mode is detected, this service breaks the narrative into multiple scenes and generates a *separate, unique search query* for each one.
  * **Response Caching:** (Optional) Connects to a MongoDB database to cache AI responses, dramatically speeding up results for repeated or popular prompts.

-----

## 🛠️ How It Works

1.  **Receive Request:** The service exposes a single main endpoint: `/ai/analyze`. It receives a JSON request from the Spring Boot backend containing the user's `prompt` and `selected_genres`.
2.  **Check Cache:** (If enabled) It hashes the prompt and checks the MongoDB database to see if this exact prompt has been analyzed before. If so, it returns the cached result instantly.
3.  **Build Prompt:** The service uses `prompt_builder.py` to construct a detailed "meta-prompt" to send to the LLM. This prompt instructs the AI to *only* return a JSON object with a specific structure.
4.  **Call LLM:** The `ai_service.py` uses the `openai` client to send this meta-prompt to a Hugging Face model (like Llama-3).
5.  **Parse Response:** The service parses the LLM's JSON-formatted text response.
6.  **Return Query:** It sends the final, clean JSON object—containing the `theme` and `search_query`—back to the Spring Boot backend.

-----

## API Endpoints

| Method | Endpoint | Description |
| :--- | :--- | :--- |
| `POST` | `/ai/analyze` | The main endpoint. Takes a prompt and returns an `AIAnalysisResponse` containing the `theme` and `search_query`. |
| `GET` | `/` / `/health` | Standard health-check endpoints to confirm the service is running. |

### Data Models (`schemas.py`)

  * **Request:** `AnalysisRequest`
    ```json
    {
      "prompt": "Music for a late-night drive",
      "selected_genres": ["electronic", "hip-hop"],
      "story_threshold": 30
    }
    ```
  * **Response (Direct Mode):** `AIAnalysisResponse`
    ```json
    {
      "analysis_id": "ai-123456789",
      "mode": "DIRECT",
      "scenes": null,
      "direct_analysis": {
        "theme": "Late-Night Drive",
        "search_query": "late-night drive electronic hip-hop"
      }
    }
    ```

-----

## 🚀 Setup & Run

### 1\. Prerequisites

  * Python 3.10+
  * A Hugging Face API Token (or other LLM provider token)
  * (Optional) A running MongoDB instance for caching

### 2\. Environment Configuration

Create a `.env` file in the `/fastapi` directory.

```
# Your API token for the LLM
HF_TOKEN="YOUR_HUGGINGFACE_API_TOKEN"

# (Optional) MongoDB connection details
MONGO_URI="mongodb://localhost:27017/"
MONGO_DB_NAME="audynce-cache"
```

### 3\. Installation & Running

```bash
# From the root /fastapi directory
cd fastapi

# 1. Create a virtual environment (Recommended)
python3 -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# 2. Install dependencies
pip install -r requirements.txt

# 3. Run the server
uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
```

The AI service will now be running on `http://localhost:8000`. You can view the auto-generated documentation at `http://localhost:8000/docs`.
