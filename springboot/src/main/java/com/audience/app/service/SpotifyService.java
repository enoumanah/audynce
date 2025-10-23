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

        // Fallback to genres-only if no artists
        if (allArtists.isEmpty()) {
            String genreQuery = seedGenres.stream().map(g -> "genre:\"" + g + "\"").collect(Collectors.joining(" "));
            candidateTracks.addAll(searchTracks(genreQuery, limit * 2, accessToken)); // Double to allow filtering
        } else {
            // Search Spotify for tracks from these artists + genres
            for (String artist : allArtists) {
                String query = "artist:\"" + artist + "\"";
                if (!seedGenres.isEmpty()) {
                    query += " " + seedGenres.stream().map(g -> "genre:\"" + g + "\"").collect(Collectors.joining(" "));
                }
                List<Map<String, Object>> tracks = searchTracks(query, Math.max(5, limit / allArtists.size()), accessToken);
                candidateTracks.addAll(tracks);
            }
        }

        // If no tracks found, fallback to pure genre search
        if (candidateTracks.isEmpty() && !seedGenres.isEmpty()) {
            String fallbackQuery = seedGenres.stream().map(g -> "genre:\"" + g + "\"").collect(Collectors.joining(" "));
            candidateTracks.addAll(searchTracks(fallbackQuery, limit * 2, accessToken));
        }

        if (candidateTracks.isEmpty()) {
            log.warn("No tracks found after search");
            return Collections.emptyList();
        }

        // Fetch audio features for candidates
        List<String> trackIds = candidateTracks.stream()
                .map(t -> (String) t.get("id"))
                .collect(Collectors.toList());
        List<Map<String, Object>> features = getAudioFeatures(trackIds, accessToken);

        // Filter/sort by mood proximity if mood provided
        if (mood != null) {
            List<Map.Entry<Map<String, Object>, Double>> scoredTracks = new ArrayList<>();
            for (int i = 0; i < candidateTracks.size(); i++) {
                Map<String, Object> track = candidateTracks.get(i);
                Map<String, Object> feature = features.get(i);
                if (feature != null) {
                    double distance = calculateMoodDistance(mood, feature);
                    scoredTracks.add(new AbstractMap.SimpleEntry<>(track, distance));
                }
            }
            // Sort by ascending distance (closest first)
            scoredTracks.sort(Comparator.comparing(Map.Entry::getValue));
            candidateTracks = scoredTracks.stream()
                    .map(Map.Entry::getKey)
                    .limit(limit)
                    .collect(Collectors.toList());
        } else {
            // No mood: take random or first N
            Collections.shuffle(candidateTracks);
            candidateTracks = candidateTracks.subList(0, Math.min(limit, candidateTracks.size()));
        }

        log.info("Returned {} filtered track recommendations", candidateTracks.size());
        return candidateTracks;
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

    /**
     * Fetch audio features for multiple tracks
     */
    private List<Map<String, Object>> getAudioFeatures(List<String> trackIds, String accessToken) {
        if (trackIds.isEmpty()) {
            return Collections.emptyList();
        }

        WebClient webClient = webClientBuilder.baseUrl(spotifyApiUrl).build();
        String idsParam = String.join(",", trackIds);

        try {
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/audio-features")
                            .queryParam("ids", idsParam)
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response != null && response.containsKey("audio_features")) {
                return (List<Map<String, Object>>) response.get("audio_features");
            }
        } catch (Exception e) {
            log.error("Error fetching audio features", e);
        }
        return Collections.nCopies(trackIds.size(), null); // Align with track list
    }

    /**
     * Calculate Euclidean distance to mood targets
     */
    private double calculateMoodDistance(MoodProfile mood, Map<String, Object> feature) {
        double sumSq = 0.0;
        int count = 0;

        if (mood.getTargetValence() != null) {
            double val = (Double) feature.getOrDefault("valence", 0.5);
            sumSq += Math.pow(val - mood.getTargetValence(), 2);
            count++;
        }
        if (mood.getTargetEnergy() != null) {
            double val = (Double) feature.getOrDefault("energy", 0.5);
            sumSq += Math.pow(val - mood.getTargetEnergy(), 2);
            count++;
        }
        if (mood.getTargetDanceability() != null) {
            double val = (Double) feature.getOrDefault("danceability", 0.5);
            sumSq += Math.pow(val - mood.getTargetDanceability(), 2);
            count++;
        }
        if (mood.getTargetTempo() != null) {
            double val = (Double) feature.getOrDefault("tempo", 120.0);
            sumSq += Math.pow((val - mood.getTargetTempo()) / 100.0, 2); // Normalize tempo
            count++;
        }
        if (mood.getTargetAcousticness() != null) {
            double val = (Double) feature.getOrDefault("acousticness", 0.5);
            sumSq += Math.pow(val - mood.getTargetAcousticness(), 2);
            count++;
        }

        return count > 0 ? Math.sqrt(sumSq / count) : Double.MAX_VALUE;
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