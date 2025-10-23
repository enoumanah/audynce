package com.audience.app.service;

import com.audience.app.dto.spotify.MoodProfile;
import com.audience.app.dto.spotify.SpotifyRecommendationRequest;
import com.audience.app.entity.MoodType;
import com.audience.app.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpotifyService {

    private final WebClient.Builder webClientBuilder;
    private final Map<MoodType, MoodProfile> moodProfiles;

    @Value("${app.spotify.api-url}")
    private String spotifyApiUrl;

    @Value("${app.spotify.accounts-url}")
    private String spotifyAccountsUrl;

    @Value("${app.spotify.client-id}")
    private String clientId;

    @Value("${app.spotify.client-secret}")
    private String clientSecret;

    @Value("${app.lastfm.api-key}")
    private String lastFmApiKey;

    @Value("${app.spotify.top-artists-cache-hours:24}")
    private int topArtistsCacheHours;

    private static final Set<String> VALID_SPOTIFY_GENRES = Set.of(
            "pop", "r-n-b", "jazz", "rock", "hip-hop", "dance", "afrobeat", "indie", "electronic", "soul"
    );

    /**
     * Get Spotify access token using Client Credentials flow
     */
    public String getClientAccessToken() {
        WebClient webClient = webClientBuilder.baseUrl(spotifyAccountsUrl).build();

        String credentials = Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes());

        Map<String, String> response = webClient.post()
                .uri("/token")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .bodyValue("grant_type=client_credentials")
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        return response != null ? (String) response.get("access_token") : null;
    }

    /**
     * Get user's top artists for personalization (returns names instead of IDs for Last.fm compatibility)
     */
    public List<String> getUserTopArtists(User user, int limit) {
        // Check cache first
        if (user.getTopArtistsCache() != null &&
                user.getTopArtistsCacheUpdatedAt() != null &&
                user.getTopArtistsCacheUpdatedAt().plusHours(topArtistsCacheHours).isAfter(LocalDateTime.now())) {

            log.info("Using cached top artists for user: {}", user.getSpotifyId());
            return parseTopArtistsCache(user.getTopArtistsCache(), limit);
        }

        // Fetch from Spotify
        log.info("Fetching fresh top artists for user: {}", user.getSpotifyId());

        WebClient webClient = webClientBuilder.baseUrl(spotifyApiUrl).build();

        try {
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/me/top/artists")
                            .queryParam("limit", limit)
                            .queryParam("time_range", "medium_term") // Last 6 months
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.getAccessToken())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !response.containsKey("items")) {
                log.warn("No top artists found for user: {}", user.getSpotifyId());
                return Collections.emptyList();
            }

            List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
            List<String> artistNames = items.stream()
                    .map(item -> (String) item.get("name"))
                    .limit(limit)
                    .collect(Collectors.toList());

            log.info("Found {} top artists for user: {}", artistNames.size(), user.getSpotifyId());
            return artistNames;

        } catch (Exception e) {
            log.error("Error fetching top artists for user: {}", user.getSpotifyId(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Updated: Get track recommendations using Last.fm similarity + Spotify search + audio features filtering
     */
    public List<Map<String, Object>> getRecommendations(
            SpotifyRecommendationRequest request,
            String accessToken) {

        List<String> seedArtists = request.getSeedArtists() != null ? request.getSeedArtists() : Collections.emptyList();
        List<String> seedGenres = request.getSeedGenres() != null ? request.getSeedGenres().stream()
                .map(g -> g.trim().toLowerCase())
                .filter(VALID_SPOTIFY_GENRES::contains)
                .limit(3)
                .collect(Collectors.toList()) : Collections.emptyList();
        MoodProfile mood = request.getMoodProfile();
        int limit = request.getLimit();

        List<Map<String, Object>> candidateTracks = new ArrayList<>();

        // If no seeds, return empty
        if (seedArtists.isEmpty() && seedGenres.isEmpty()) {
            log.warn("No valid seeds provided — skipping recommendations");
            return Collections.emptyList();
        }

        // Use Last.fm for similar artists if seeds provided
        List<String> allArtists = new ArrayList<>(seedArtists);
        if (!seedArtists.isEmpty()) {
            for (String seedArtist : seedArtists) {
                List<String> similar = getSimilarArtists(seedArtist, 5); // Get 5 similar per seed
                allArtists.addAll(similar);
            }
        }

        // Approximate mood in query (keywords based on MoodType)
        String moodQuery = "";
        if (mood != null) {
            MoodType moodType = getMoodTypeFromProfile(mood);
            moodQuery = switch (moodType) {
                case UPBEAT -> " upbeat energetic happy";
                case MELANCHOLIC -> " sad reflective melancholic";
                case ROMANTIC -> " romantic warm tender";
                case ADVENTUROUS -> " adventurous bold exciting";
                case PEACEFUL -> " peaceful calm serene";
                case ENERGETIC -> " energetic high-intensity";
                case INTENSE -> " intense dark powerful";
                case NOSTALGIC -> " nostalgic wistful reminiscent";
                case DREAMY -> " dreamy ethereal floating";
                case CHILL -> " chill relaxed laid-back";
                default -> "";
            };
        }

        String yearRange = " year:2010-2025"; // Add to get recent tracks

        // Fallback to genres-only if no artists
        if (allArtists.isEmpty()) {
            String genreQuery = seedGenres.stream().map(g -> "genre:\"" + g + "\"").collect(Collectors.joining(" ")) + moodQuery + yearRange;
            log.info("Searching with query: {}", genreQuery);
            candidateTracks.addAll(searchTracks(genreQuery, limit * 2, accessToken)); // Double to allow variety
        } else {
            // Search Spotify for tracks from these artists + genres + mood
            for (String artist : allArtists) {
                String query = "artist:\"" + artist + "\"";
                if (!seedGenres.isEmpty()) {
                    query += " " + seedGenres.stream().map(g -> "genre:\"" + g + "\"").collect(Collectors.joining(" "));
                }
                query += moodQuery + yearRange;
                log.info("Searching with query: {}", query);
                List<Map<String, Object>> tracks = searchTracks(query, Math.max(5, limit / allArtists.size()), accessToken);
                candidateTracks.addAll(tracks);
            }
        }

        // If no tracks, retry without moodQuery
        if (candidateTracks.isEmpty()) {
            log.warn("No tracks with mood; retrying without mood keywords");
            candidateTracks.addAll(getRecommendationsWithoutMood(request, accessToken, seedArtists, seedGenres, limit, yearRange));
        }

        // If still no tracks found, fallback to pure genre search without mood
        if (candidateTracks.isEmpty() && !seedGenres.isEmpty()) {
            String fallbackQuery = seedGenres.stream().map(g -> "genre:\"" + g + "\"").collect(Collectors.joining(" ")) + yearRange;
            log.info("Fallback search with query: {}", fallbackQuery);
            candidateTracks.addAll(searchTracks(fallbackQuery, limit * 2, accessToken));
        }

        if (candidateTracks.isEmpty()) {
            log.warn("No tracks found after all searches");
            return Collections.emptyList();
        }

        // Shuffle and limit
        Collections.shuffle(candidateTracks);
        candidateTracks = candidateTracks.subList(0, Math.min(limit, candidateTracks.size()));

        log.info("Returned {} track recommendations", candidateTracks.size());
        return candidateTracks;
    }

    // Helper for retry without mood
    private List<Map<String, Object>> getRecommendationsWithoutMood(
            SpotifyRecommendationRequest request,
            String accessToken,
            List<String> seedArtists,
            List<String> seedGenres,
            int limit,
            String yearRange) {

        List<Map<String, Object>> candidates = new ArrayList<>();
        List<String> allArtists = new ArrayList<>(seedArtists);
        if (!seedArtists.isEmpty()) {
            for (String seedArtist : seedArtists) {
                List<String> similar = getSimilarArtists(seedArtist, 5);
                allArtists.addAll(similar);
            }
        }

        if (allArtists.isEmpty()) {
            String genreQuery = seedGenres.stream().map(g -> "genre:\"" + g + "\"").collect(Collectors.joining(" ")) + yearRange;
            log.info("Retry search with query: {}", genreQuery);
            candidates.addAll(searchTracks(genreQuery, limit * 2, accessToken));
        } else {
            for (String artist : allArtists) {
                String query = "artist:\"" + artist + "\"";
                if (!seedGenres.isEmpty()) {
                    query += " " + seedGenres.stream().map(g -> "genre:\"" + g + "\"").collect(Collectors.joining(" "));
                }
                query += yearRange;
                log.info("Retry search with query: {}", query);
                candidates.addAll(searchTracks(query, Math.max(5, limit / allArtists.size()), accessToken));
            }
        }
        return candidates;
    }

    // MoodType helper
    private MoodType getMoodTypeFromProfile(MoodProfile mood) {
        for (Map.Entry<MoodType, MoodProfile> entry : moodProfiles.entrySet()) {
            if (entry.getValue().equals(mood)) {
                return entry.getKey();
            }
        }
        return MoodType.BALANCED;
    }

    /**
     * Get similar artists from Last.fm
     */
    public List<String> getSimilarArtists(String seedArtist, int limit) {
        WebClient webClient = webClientBuilder.baseUrl("https://ws.audioscrobbler.com/2.0/").build();

        try {
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("method", "artist.getSimilar")
                            .queryParam("artist", seedArtist)
                            .queryParam("api_key", lastFmApiKey)
                            .queryParam("format", "json")
                            .queryParam("limit", limit)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !response.containsKey("similarartists")) {
                return Collections.emptyList();
            }

            Map<String, Object> similar = (Map<String, Object>) response.get("similarartists");
            List<Map<String, Object>> artists = (List<Map<String, Object>>) similar.get("artist");

            // Filter by match >= 0.5 and extract names
            return artists.stream()
                    .filter(a -> Double.parseDouble((String) a.get("match")) >= 0.5)
                    .map(a -> (String) a.get("name"))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Error fetching similar artists from Last.fm for: {}", seedArtist, e);
            return Collections.emptyList();
        }
    }

    // ---- keep createSpotifyPlaylist simple and separate from recommendations ----
    public String createSpotifyPlaylist(User user, String title, String description,
                                        List<String> trackUris, boolean isPublic) {
        WebClient webClient = webClientBuilder.baseUrl(spotifyApiUrl).build();

        try {
            Map<String, Object> createRequest = Map.of(
                    "name", title,
                    "description", description,
                    "public", isPublic
            );

            Map<String, Object> playlistResponse = webClient.post()
                    .uri("/users/" + user.getSpotifyId() + "/playlists")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.getAccessToken())
                    .bodyValue(createRequest)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (playlistResponse == null || !playlistResponse.containsKey("id")) {
                log.error("Failed to create Spotify playlist");
                return null;
            }

            String playlistId = (String) playlistResponse.get("id");
            log.info("Created Spotify playlist: {}", playlistId);

            if (!trackUris.isEmpty()) {
                Map<String, Object> addTracksRequest = Map.of("uris", trackUris);

                webClient.post()
                        .uri("/playlists/" + playlistId + "/tracks")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.getAccessToken())
                        .bodyValue(addTracksRequest)
                        .retrieve()
                        .bodyToMono(Map.class)
                        .block();

                log.info("Added {} tracks to playlist: {}", trackUris.size(), playlistId);
            }

            return playlistId;

        } catch (WebClientResponseException e) {
            log.error("Spotify API error while creating playlist: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Error creating Spotify playlist", e);
        }
        return null;
    }

    /**
     * Get mood profile for a given mood type
     */
    public MoodProfile getMoodProfile(MoodType moodType) {
        return moodProfiles.getOrDefault(moodType, moodProfiles.get(MoodType.BALANCED));
    }

    /**
     * Search for tracks (fallback if recommendations fail)
     */
    public List<Map<String, Object>> searchTracks(String query, int limit, String accessToken) {
        WebClient webClient = webClientBuilder.baseUrl(spotifyApiUrl).build();

        try {
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", query)
                            .queryParam("type", "track")
                            .queryParam("limit", limit)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("tracks")) {
                Map<String, Object> tracks = (Map<String, Object>) response.get("tracks");
                return (List<Map<String, Object>>) tracks.get("items");
            }

        } catch (Exception e) {
            log.error("Error searching tracks", e);
        }

        return Collections.emptyList();
    }

    private List<String> parseTopArtistsCache(String cache, int limit) {
        // Simple comma-separated cache format
        return Arrays.stream(cache.split(","))
                .limit(limit)
                .collect(Collectors.toList());
    }
}