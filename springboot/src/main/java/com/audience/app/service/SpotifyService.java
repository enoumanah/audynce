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

    @Value("${app.spotify.top-artists-cache-hours:24}")
    private int topArtistsCacheHours;

    private static final Set<String> VALID_SPOTIFY_GENRES = Set.of(
            "pop", "rnb", "jazz", "rock", "hip-hop", "dance", "afrobeat", "indie", "electronic", "soul"
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
     * Get user's top artists for personalization
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
            List<String> artistIds = items.stream()
                    .map(item -> (String) item.get("id"))
                    .limit(limit)
                    .collect(Collectors.toList());

            log.info("Found {} top artists for user: {}", artistIds.size(), user.getSpotifyId());
            return artistIds;

        } catch (Exception e) {
            log.error("Error fetching top artists for user: {}", user.getSpotifyId(), e);
            return Collections.emptyList();
        }
    }

    /**
     * Get track recommendations based on mood, genres, and user personalization
     */
    public List<Map<String, Object>> getRecommendations(
            SpotifyRecommendationRequest request,
            String accessToken) {

        WebClient webClient = webClientBuilder.baseUrl(spotifyApiUrl).build();
        MoodProfile mood = request.getMoodProfile();

        Map<String, Object> queryParams = new HashMap<>();
        queryParams.put("limit", request.getLimit());

        if (request.getSeedGenres() != null && !request.getSeedGenres().isEmpty()) {
            String genres = request.getSeedGenres().stream()
                    .filter(VALID_SPOTIFY_GENRES::contains) // ✅ filter invalid
                    .limit(3)
                    .collect(Collectors.joining(","));
            queryParams.put("seed_genres", genres);
        }
        
        // Add seed artists for personalization (max 2)
        if (request.getSeedArtists() != null && !request.getSeedArtists().isEmpty()) {
            String artists = request.getSeedArtists().stream()
                    .limit(2)
                    .collect(Collectors.joining(","));
            queryParams.put("seed_artists", artists);
        }

        // Add mood profile attributes
        if (mood != null) {
            if (mood.getTargetValence() != null) {
                queryParams.put("target_valence", mood.getTargetValence());
            }
            if (mood.getTargetEnergy() != null) {
                queryParams.put("target_energy", mood.getTargetEnergy());
            }
            if (mood.getTargetDanceability() != null) {
                queryParams.put("target_danceability", mood.getTargetDanceability());
            }
            if (mood.getTargetTempo() != null) {
                queryParams.put("target_tempo", mood.getTargetTempo());
            }
            if (mood.getTargetAcousticness() != null) {
                queryParams.put("target_acousticness", mood.getTargetAcousticness());
            }
        }

        log.info("Requesting recommendations with params: {}", queryParams);

        try {
            //  Guard clause — ensure seeds exist before calling Spotify API
            if ((!queryParams.containsKey("seed_artists") ||
                    ((String) queryParams.get("seed_artists")).isBlank()) &&
                    (!queryParams.containsKey("seed_genres") ||
                            ((String) queryParams.get("seed_genres")).isBlank())) {

                log.warn("No valid seeds provided — skipping Spotify recommendations call");
                return Collections.emptyList();
            }

            //  Make the API call
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> {
                        var builder = uriBuilder.path("/recommendations");
                        queryParams.forEach(builder::queryParam);
                        return builder.build();
                    })
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("tracks")) {
                List<Map<String, Object>> tracks = (List<Map<String, Object>>) response.get("tracks");
                log.info("Received {} track recommendations", tracks.size());
                return tracks;
            }

        } catch (WebClientResponseException e) {
            log.error("Spotify API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("Error getting recommendations", e);
        }

        return Collections.emptyList();
    }

    /**
     * Create a Spotify playlist for the user
     */
    public String createSpotifyPlaylist(User user, String title, String description,
                                        List<String> trackUris, boolean isPublic) {
        WebClient webClient = webClientBuilder.baseUrl(spotifyApiUrl).build();

        try {
            // 1. Create playlist
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

            // 2. Add tracks to playlist
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

        } catch (Exception e) {
            log.error("Error creating Spotify playlist", e);
            return null;
        }
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