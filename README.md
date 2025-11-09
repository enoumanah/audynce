# Audynce

Audynce is an intelligent music recommendation engine designed to translate any abstract "vibe," story, or prompt into a high-quality, curated Spotify playlist.

It uses a microservice architecture, combining a Spring Boot backend, a FastAPI AI service, and a React frontend to deliver a seamless user experience.

-----

## 🚀 Core Features

  * **AI-Powered Vibe Translation:** Leverages a Large Language Model (LLM) to analyze natural language prompts and generate optimized Spotify search queries.
  * **Direct Spotify Integration:** Full OAuth2 authentication flow for users to securely connect their Spotify accounts.
  * **Automated Playlist Creation:** Automatically creates and populates new playlists in a user's Spotify library.
  * **Story Mode:** Capable of interpreting longer "story" prompts and breaking them down into multiple musical "scenes," each with its own set of tracks.
  * **Backend Quality Filtering:** A robust post-processing step that filters out generic "type beats," duplicates, and low-quality instrumentals to ensure a premium listening experience.
  * **User Profile Management:** Users can view their profile and manage their previously generated playlists.

-----

## 🛠️ How It Works: The V1 Architecture

The application's logic is split into three distinct services that work in concert:

1.  **Frontend (React):** The user logs in via Spotify and enters a prompt (e.g., *"90s west coast rap tupac vibe"*).
2.  **Backend (Spring Boot):** The backend receives the prompt from the user.
3.  **AI Service (FastAPI):** The backend sends the raw prompt to the FastAPI service. This AI service analyzes the text and translates it into a single, optimized Spotify search query string (e.g., `"90s west coast rap artist:2Pac"`).
4.  **Backend (Spring Boot):** The backend receives this search query *from the AI*.
5.  **Spotify API:** The backend uses the AI-generated query to search Spotify, intentionally fetching *more* tracks than needed (e.g., 40-50 tracks) to create a "search pool."
6.  **Backend Quality Filter:** This is the most critical step. The backend filters this noisy pool by:
      * **De-duplicating:** Skipping any track ID it has already added.
      * **Filtering Generics:** Removing tracks with spammy names (e.g., "Instrumental," "Beat," `(2007)`) or from known "type beat" artists.
7.  **Response:** The final, clean, and high-quality list of tracks is sent to the React frontend to be displayed. If the user requested it, this list is also used to create a new playlist on their Spotify account.

-----

## 💻 Tech Stack

| Service | Language/Framework | Key Technologies |
| :--- | :--- | :--- |
| **Frontend** | TypeScript, React (Vite) | `shadcn-ui`, Tailwind CSS, `react-router-dom` |
| **Backend** | Java 17, Spring Boot 3 | Spring Security (OAuth2), PostgreSQL, JPA, JWT, WebClient |
| **AI Service**| Python 3, FastAPI | Pydantic, `openai` client (for Hugging Face), MongoDB (for caching) |

-----

## 📁 Project Structure

```
/audynce
|
|-- /fastapi    (Python AI Microservice)
|   |-- /app
|   |-- requirements.txt
|   `-- Dockerfile
|
|-- /frontend   (React User Interface)
|   |-- /src
|   |-- package.json
|   `-- vite.config.ts
|
|-- /springboot (Java Backend API & Orchestrator)
|   |-- /src
|   |-- pom.xml
|   `-- Dockerfile
|
`-- README.md   (You are here)
```

-----

## 🚀 Getting Started

To run this project locally, you will need to set up all three services.

### Prerequisites

  * **Java 17+ (JDK)** and **Maven 3+**
  * **Python 3.10+** and `pip`
  * **Node.js 18+** (or **Bun**)
  * **A PostgreSQL Database:** A running instance for the Spring Boot backend to store user and playlist data.
  * **A MongoDB Database:** (Optional, for AI caching) A running instance for the FastAPI service.
  * **Spotify for Developers Application:**
    1.  Go to the [Spotify Developer Dashboard](https://www.google.com/search?q=http://developer.spotify.com/dashboard).
    2.  Create a new application.
    3.  Find your **Client ID** and **Client Secret**.
    4.  You *must* add a **Redirect URI** in the app's settings. For local development, this will be: `http://localhost:8080/login/oauth2/code/spotify`

### Environment Variables

You must set up environment variables for all three services.

#### 1\. Spring Boot Backend (`/springboot/src/main/resources/application.yml`)

```yaml
server:
  port: 8080

app:
  frontend-url: "http://localhost:5173" # URL of your running React app
  fastapi:
    url: "http://localhost:8000" # URL of your running FastAPI app
  jwt:
    secret: "YOUR_VERY_STRONG_AND_SECRET_JWT_KEY" # A long, random string
  encryption:
    key: "YOUR_32_BYTE_ENCRYPTION_SECRET_KEY" # Must be 32 bytes (32 characters)

spring:
  datasource:
    url: "jdbc:postgresql://localhost:5432/audynce_db"
    username: "your_db_user"
    password: "your_db_password"
  jpa:
    hibernate:
      ddl-auto: update
    
  security:
    oauth2:
      client:
        registration:
          spotify:
            client-id: "YOUR_SPOTIFY_CLIENT_ID"
            client-secret: "YOUR_SPOTIFY_CLIENT_SECRET"
            scope:
              - user-read-private
              - user-read-email
              - playlist-read-private
              - playlist-modify-public
              - playlist-modify-private
              - user-top-read
```

#### 2\. FastAPI AI Service (`/fastapi/.env`)

Create a `.env` file in the `/fastapi` directory.

```
HF_TOKEN="YOUR_HUGGINGFACE_API_TOKEN"
MONGO_URI="mongodb://localhost:27017/"
MONGO_DB_NAME="audynce-cache"
```

#### 3\. React Frontend (`/frontend/.env`)

Create a `.env` file in the `/frontend` directory.

```
VITE_API_BASE_URL="http://localhost:8080"
```

### Installation & Running

1.  **Run the Backend (Spring Boot)**

    ```bash
    # From the root directory
    cd springboot
    mvn spring-boot:run
    ```

    *(The backend will be running on `http://localhost:8080`)*

2.  **Run the AI Service (FastAPI)**

    ```bash
    # From the root directory, in a new terminal
    cd fastapi
    pip install -r requirements.txt
    uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload
    ```

    *(The AI service will be running on `http://localhost:8000`)*

3.  **Run the Frontend (React)**

    ```bash
    # From the root directory, in a new terminal
    cd frontend
    npm install  # or 'bun install'
    npm run dev  # or 'bun dev'
    ```

    *(The frontend will be running on `http://localhost:5173`)*

Once all three services are running, you can open **`http://localhost:5173`** in your browser to use the application.
