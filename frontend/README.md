# Audynce React Frontend

This is the user interface for the Audynce application. It is a modern, responsive single-page application (SPA) built with **React**, **Vite**, and **TypeScript**.

It provides a clean, intuitive interface for users to authenticate, enter their creative prompts, and view their AI-generated playlists. The UI is built using the high-quality **shadcn-ui** component library and styled with **Tailwind CSS**.

-----

## 🚀 Core Features

  * **Spotify Authentication:** A seamless login flow that redirects to the Spring Boot backend to handle the entire OAuth2 process.
  * **JWT Session Management:** Securely receives a JWT from the backend upon login, stores it in `localStorage`, and automatically attaches it to all future API requests.
  * **Vibe Input Form:** A dynamic form (`/create`) where users can type their prompt, select genres, and toggle options like playlist privacy.
  * **Animated Loading:** A custom loading animation (`LoadingAnimation.tsx`) that provides a great user experience while the backend and AI are processing the request.
  * **Playlist Results:** A clean, multi-scene layout (`/results`) to display the final generated playlist, complete with track details and an "Open in Spotify" link.
  * **User Profile Page:** A dedicated route (`/profile`) that fetches and displays user information and a list of their previously generated playlists.

-----

## 🛠️ Key Components & User Flow

The frontend is built around a clear user flow managed by `react-router-dom`:

1.  **Login (`/`)**:

      * A new user lands on `Landing.tsx`.
      * Clicking "Connect with Spotify" navigates them *away* from the React app to the Spring Boot backend's `/oauth2/authorization/spotify` endpoint to begin the OAuth flow.

2.  **Auth Callback (`/auth/callback`)**:

      * After a successful Spotify login, the Spring Boot backend redirects the user back to this *exact* frontend route.
      * The `AuthCallback.tsx` component activates, grabs the **JWT** (app-specific, not the Spotify token) from the URL query parameters.
      * It saves this JWT to `localStorage` and immediately navigates the user to the `/create` page.

3.  **Playlist Creation (`/create`)**:

      * `Create.tsx` is the main form.
      * The form state (prompt, genres, options) is managed using React hooks.
      * On submit, it retrieves the JWT from `localStorage` and makes an authenticated `POST` request to the Spring Boot backend's `/api/playlists/generate` endpoint.
      * While waiting for the response, it navigates the user to the `/results` page and displays the `LoadingAnimation.tsx` component.

4.  **Results Display (`/results`)**:

      * The `Results.tsx` component loads the playlist data (passed via navigation state) and renders it.
      * It includes logic to display multiple scenes (for Story Mode) or a single list (for Direct Mode).
      * It displays the final "Open in Spotify" button if the backend provides a `spotifyPlaylistUrl`.

5.  **Profile Management (`/profile`)**:

      * `Profile.tsx` makes authenticated `GET` requests to `/api/users/me` and `/api/playlists` to fetch and display the user's data and their saved playlists.

-----

## 💻 Tech Stack

  * **Framework:** React 18
  * **Build Tool:** Vite
  * **Language:** TypeScript
  * **Routing:** `react-router-dom`
  * **UI:** `shadcn-ui`
  * **Styling:** Tailwind CSS
  * **API Communication:** `axios` (or `fetch`)

-----

## 🚀 Setup & Run

### 1\. Prerequisites

  * Node.js (v18+) or Bun

### 2\. Environment Configuration

Create a `.env` file in the `/frontend` directory. It needs one variable:

```
# The URL of your running Spring Boot backend
VITE_API_BASE_URL="http://localhost:8080"
```

### 3\. Installation & Running

```bash
# From the root /frontend directory
cd frontend

# Install dependencies
npm install
# or
bun install

# Run the development server
npm run dev
# or
bun dev
```

The frontend will start on `http://localhost:5173` and will be ready to connect to the backend services.
