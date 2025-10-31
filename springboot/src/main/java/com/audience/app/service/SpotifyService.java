package com.audience.app.service;

// REMOVED: import com.audience.app.dto.spotify.AudioFeatures;
// REMOVED: import com.audience.app.dto.spotify.MoodProfile;
// REMOVED: import com.audience.app.dto.spotify.SpotifyRecommendationRequest;
import com.audience.app.entity.MoodType;
import com.audience.app.entity.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpotifyService {

    private final WebClient.Builder webClientBuilder;

    // REMOVED: private final Map<MoodType, MoodProfile> moodProfiles;

    @Value("${app.spotify.api-url}")
    @Getter
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

    // REMOVED: All tolerance constants

    public static final Set<String> VALID_SPOTIFY_GENRES = Set.of(
            // ... (your existing set of genres)
            "acoustic", "afrobeat", "alt-rock", "alternative", "ambient", "anime", "black-metal",
            "bluegrass", "blues", "bossanova", "brazil", "breakbeat", "british", "cantopop",
            "chicago-house", "children", "chill", "classical", "club", "comedy", "country",
            "dance", "dancehall", "death-metal", "deep-house", "detroit-techno", "disco", "disney",
            "drum-and-bass", "dub", "dubstep", "edm", "electro", "electronic", "emo", "folk",
            "forro", "french", "funk", "garage", "german", "gospel", "goth", "grindcore", "groove",
            "grunge", "guitar", "happy", "hard-rock", "hardcore", "hardstyle", "heavy-metal",
            "hip-hop", "holidays", "honky-tonk", "house", "idm", "indian", "indie", "indie-pop",
            "industrial", "iranian", "j-dance", "j-idol", "j-pop", "j-rock", "jazz", "k-pop",
            "kids", "latin", "latino", "malay", "mandopop", "metal", "metal-misc", "metalcore",
            "minimal-techno", "movies", "mpb", "new-age", "new-release", "opera", "pagode",
            "party", "philippines-opm", "piano", "pop", "pop-film", "post-dubstep", "power-pop",
            "progressive-house", "psych-rock", "punk", "punk-rock", "r-n-b", "rainy-day", "reggae",
            "reggaeton", "road-trip", "rock", "rock-n-roll", "rockabilly", "romance", "sad",
            "salsa", "samba", "sertanejo", "show-tunes", "singer-songwriter", "ska", "sleep",
            "songwriter", "soul", "soundtracks", "spanish", "study", "summer", "swedish",
            "synth-pop", "tango", "techno", "trance", "trip-hop", "turkish", "work-out", "world-music"
    );


    public Mono<String> getClientAccessToken() {
        // ... (existing method, no changes)
        WebClient webClient = webClientBuilder.baseUrl(spotifyAccountsUrl).build();
        String credentials = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes());
        return webClient.post()
                .uri("/token")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .bodyValue("grant_type=client_credentials")
                .retrieve()
                .onStatus(status -> status.isError(),
                        clientResponse -> clientResponse.bodyToMono(String.class)
                                .switchIfEmpty(Mono.just("{ \"error\": \"Empty error body\" }"))
                                .flatMap(body -> {
                                    log.error("Spotify API error getting client access token: {} - Body: {}", clientResponse.statusCode(), body);
                                    return Mono.error(new RuntimeException("Failed to get client access token, status: " + clientResponse.statusCode()));
                                }))
                .bodyToMono(Map.class)
                .map(response -> (response != null && response.get("access_token") instanceof String) ? (String) response.get("access_token") : null)
                .doOnError(e -> log.error("Error processing client access token response", e));
    }


    public List<String> getUserTopArtists(User user, int limit) {
        // ... (existing method, no changes)
        if (user.getTopArtistsCache() != null &&
                user.getTopArtistsCacheUpdatedAt() != null &&
                user.getTopArtistsCacheUpdatedAt().plusHours(topArtistsCacheHours).isAfter(LocalDateTime.now())) {
            log.info("Using cached top artists for user: {}", user.getSpotifyId());
            return parseTopArtistsCache(user.getTopArtistsCache(), limit);
        }
        log.info("Fetching fresh top artists for user: {}", user.getSpotifyId());
        WebClient webClient = webClientBuilder.baseUrl(spotifyApiUrl).build();
        try {
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/me/top/artists")
                            .queryParam("limit", Math.min(limit, 50))
                            .queryParam("time_range", "medium_term")
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.getAccessToken())
                    .retrieve()
                    .onStatus(status -> status.isError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .switchIfEmpty(Mono.just("{ \"error\": \"Empty error body\" }"))
                                    .flatMap(body -> {
                                        log.error("Spotify API error fetching top artists: {} - Body: {}", clientResponse.statusCode(), body);
                                        return Mono.error(new RuntimeException("Failed to fetch top artists, status: " + clientResponse.statusCode()));
                                    }))
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(10));

            if (response == null || !(response.get("items") instanceof List<?> itemsRaw)) {
                log.warn("No 'items' list found in top artists response for user: {}", user.getSpotifyId());
                return Collections.emptyList();
            }
            if (CollectionUtils.isEmpty(itemsRaw)) {
                log.info("User {} has no top artists in the medium term.", user.getSpotifyId());
                return Collections.emptyList();
            }
            List<String> artistNames = itemsRaw.stream()
                    .filter(item -> item instanceof Map)
                    .map(item -> (Map<String, Object>) item)
                    .filter(itemMap -> itemMap.get("name") instanceof String)
                    .map(itemMap -> (String) itemMap.get("name"))
                    .filter(StringUtils::hasText)
                    .limit(limit)
                    .collect(Collectors.toList());
            if (!artistNames.isEmpty()) {
                user.setTopArtistsCache(String.join(",", artistNames));
                user.setTopArtistsCacheUpdatedAt(LocalDateTime.now());
                log.info("Fetched and cached {} top artists for user: {}", artistNames.size(), user.getSpotifyId());
            } else {
                log.info("Found 0 valid top artists for user: {}", user.getSpotifyId());
            }
            return artistNames;
        } catch (WebClientResponseException e) {
            if (e.getStatusCode().value() == 401) {
                log.error("Unauthorized (401) fetching top artists for user: {}. Access token likely expired.", user.getSpotifyId());
            } else {
                log.error("WebClientResponseException fetching top artists for user: {}: {} - Body: {}",
                        user.getSpotifyId(), e.getStatusCode(), e.getResponseBodyAsString(), e);
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Unexpected error fetching top artists for user: {}: {}", user.getSpotifyId(), e.getMessage(), e);
            return Collections.emptyList();
        }
    }


    /**
     * **REMOVED**: The getRecommendations method. It was based on deprecated endpoints.
     */
    // public List<Map<String, Object>> getRecommendations(...) { ... }

    /**
     * **REMOVED**: The isGenericTrack helper.
     */
    // private boolean isGenericTrack(...) { ... }

    /**
     * **REMOVED**: The isTrackMoodMatch helper.
     */
    // private boolean isTrackMoodMatch(...) { ... }

    /**
     * **REMOVED**: The getAudioFeatures method.
     */
    // private Map<String, AudioFeatures> getAudioFeatures(...) { ... }


    /**
     * **REMOVED**: The getMoodKeywords helper.
     */
    // private String getMoodKeywords(MoodProfile mood) { ... }


    // REMOVED: This method is no longer needed as the AI now returns the search query.
    /*
    private MoodType getMoodTypeFromProfile(MoodProfile profile) {
        // ... (existing method, no changes)
        if (profile == null) return MoodType.BALANCED;
        double targetValence = Optional.ofNullable(profile.getTargetValence()).orElse(0.5);
        double targetEnergy = Optional.ofNullable(profile.getTargetEnergy()).orElse(0.5);
        MoodType bestMatch = MoodType.BALANCED;
        double minDistance = Double.MAX_VALUE;
        for (Map.Entry<MoodType, MoodProfile> entry : moodProfiles.entrySet()) {
            MoodProfile currentProfile = entry.getValue();
            if (currentProfile == null) continue;
            double currentValence = Optional.ofNullable(currentProfile.getTargetValence()).orElse(0.5);
            double currentEnergy = Optional.ofNullable(currentProfile.getTargetEnergy()).orElse(0.5);
            double distance = Math.sqrt(Math.pow(targetValence - currentValence, 2) + Math.pow(targetEnergy - currentEnergy, 2));
            if (distance < minDistance) {
                minDistance = distance;
                bestMatch = entry.getKey();
            }
        }
        log.debug("Mapped input profile (V:{}, E:{}) to MoodType: {}", String.format("%.2f", targetValence), String.format("%.2f", targetEnergy), bestMatch);
        return bestMatch;
    }
    */

    public List<String> getSimilarArtists(String seedArtist, int limit) {
        // ... (existing method, no changes)
        if (!StringUtils.hasText(lastFmApiKey)) {
            log.warn("Last.fm API key not configured. Skipping similar artist search.");
            return Collections.emptyList();
        }
        if (!StringUtils.hasText(seedArtist)) {
            log.debug("Cannot get similar artists: seedArtist is empty.");
            return Collections.emptyList();
        }
        int effectiveLimit = Math.max(1, limit);
        WebClient webClient = webClientBuilder.baseUrl("https://ws.audioscrobbler.com/2.0/").build();
        log.debug("Fetching up to {} similar artists for '{}' from Last.fm", effectiveLimit, seedArtist);
        try {
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("method", "artist.getSimilar")
                            .queryParam("artist", seedArtist)
                            .queryParam("api_key", lastFmApiKey)
                            .queryParam("format", "json")
                            .queryParam("limit", effectiveLimit)
                            .build())
                    .retrieve()
                    .onStatus(status -> status.isError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .switchIfEmpty(Mono.just("{ \"error\": \"Empty error body\" }"))
                                    .flatMap(body -> {
                                        log.error("Last.fm API error fetching similar artists for '{}': {} - Body: {}", seedArtist, clientResponse.statusCode(), body);
                                        return Mono.empty();
                                    }))
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(5));
            if (response == null || !(response.get("similarartists") instanceof Map similarMap)) {
                log.warn("Invalid or missing 'similarartists' map in Last.fm response for {}", seedArtist);
                return Collections.emptyList();
            }
            if (!(similarMap.get("artist") instanceof List<?> artistRawList)) {
                log.info("No similar artists list found on Last.fm for {}", seedArtist);
                return Collections.emptyList();
            }
            return artistRawList.stream()
                    .filter(item -> item instanceof Map)
                    .map(item -> (Map<String, Object>) item)
                    .filter(a -> {
                        try {
                            Object matchObj = a.get("match");
                            return matchObj != null && Double.parseDouble(matchObj.toString()) >= 0.4;
                        } catch (Exception e) { return false; }
                    })
                    .map(a -> a.get("name"))
                    .filter(name -> name instanceof String && StringUtils.hasText((String) name))
                    .map(name -> (String) name)
                    .limit(effectiveLimit)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error fetching/processing similar artists from Last.fm for '{}': {}", seedArtist, e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    public String createSpotifyPlaylist(User user, String title, String description,
                                        List<String> trackUris, boolean isPublic) {
        // ... (existing method, no changes)
        if (user == null || !StringUtils.hasText(user.getSpotifyId()) || !StringUtils.hasText(user.getAccessToken())) {
            log.error("Cannot create Spotify playlist: Invalid user data.");
            return null;
        }
        if (CollectionUtils.isEmpty(trackUris)) {
            log.warn("Cannot create Spotify playlist for user {}: No track URIs.", user.getSpotifyId());
            return null;
        }
        title = StringUtils.hasText(title) ? title : "Audiance Generated Playlist";
        description = (description != null) ? description : "";
        WebClient webClient = webClientBuilder.baseUrl(spotifyApiUrl).build();
        log.info("Creating Spotify playlist for user {}: title='{}', public={}, {} tracks", user.getSpotifyId(), title, isPublic, trackUris.size());
        String playlistId = null;
        try {
            Map<String, Object> createRequest = Map.of("name", title, "description", description, "public", isPublic);
            log.debug("Spotify Create Playlist Request Body: {}", createRequest);
            Map<String, Object> playlistResponse = webClient.post()
                    .uri("/users/" + user.getSpotifyId() + "/playlists")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.getAccessToken())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(createRequest)
                    .retrieve()
                    .onStatus(status -> status.isError(), resp -> resp.bodyToMono(String.class)
                            .switchIfEmpty(Mono.just("Empty error body"))
                            .flatMap(body -> Mono.error(new WebClientResponseException(
                                    "Error creating playlist shell: " + resp.statusCode(),
                                    resp.statusCode().value(), resp.statusCode().toString(), resp.headers().asHttpHeaders(), body.getBytes(), null))))
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(15));
            if (playlistResponse == null || !(playlistResponse.get("id") instanceof String pId) || !StringUtils.hasText(pId)) {
                log.error("Failed to create Spotify playlist shell for user {} - invalid ID in response: {}", user.getSpotifyId(), playlistResponse);
                return null;
            }
            playlistId = pId;
            log.info("Created Spotify playlist shell with ID: {}", playlistId);
            int batchSize = 100;
            boolean allBatchesSuccessful = true;
            for (int i = 0; i < trackUris.size(); i += batchSize) {
                List<String> batchUris = trackUris.subList(i, Math.min(i + batchSize, trackUris.size()));
                log.debug("Adding batch {}/{} ({} tracks) to playlist {}", (i/batchSize + 1), (trackUris.size() + batchSize - 1)/batchSize, batchUris.size(), playlistId);
                Map<String, Object> addTracksRequest = Map.of("uris", batchUris);
                try {
                    String finalPlaylistId = playlistId;
                    Map<String, Object> addResponse = webClient.post()
                            .uri("/playlists/" + playlistId + "/tracks")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.getAccessToken())
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .bodyValue(addTracksRequest)
                            .retrieve()
                            .onStatus(status -> status.isError(), resp -> resp.bodyToMono(String.class)
                                    .switchIfEmpty(Mono.just("Empty error body"))
                                    .flatMap(body -> {
                                        log.error("Spotify API error adding tracks batch to playlist {}: {} - Body: {}", finalPlaylistId, resp.statusCode(), body);
                                        return Mono.error(new WebClientResponseException(
                                                "Error adding tracks batch: " + resp.statusCode(),
                                                resp.statusCode().value(), resp.statusCode().toString(), resp.headers().asHttpHeaders(), body.getBytes(), null));
                                    }))
                            .bodyToMono(Map.class)
                            .block(Duration.ofSeconds(15));
                    if (addResponse == null || !addResponse.containsKey("snapshot_id")) {
                        log.error("Failed to add batch {} of tracks to playlist {} - invalid response: {}", (i/batchSize + 1), playlistId, addResponse);
                        allBatchesSuccessful = false;
                    } else {
                        log.info("Successfully added batch {} of tracks to playlist: {}", (i/batchSize + 1), playlistId);
                    }
                } catch (Exception batchEx) {
                    log.error("Exception adding track batch {} to playlist {}: {}", (i/batchSize + 1), playlistId, batchEx.getMessage(), batchEx);
                    allBatchesSuccessful = false;
                }
            }
            if (!allBatchesSuccessful) {
                log.warn("One or more batches failed to add tracks to playlist {}. Playlist created but may be incomplete.", playlistId);
            }
            return playlistId;
        } catch (Exception e) {
            log.error("Error during Spotify playlist creation process for user {}: {}", user.getSpotifyId(), e.getMessage(), e);
            return null;
        }
    }


    // REMOVED: getMoodProfile is no longer needed.
    // public MoodProfile getMoodProfile(MoodType moodType) { ... }


    public List<Map<String, Object>> searchTracks(String query, int limit, String accessToken) {
        // ... (existing method, no changes)
        if (!StringUtils.hasText(query)) {
            log.warn("Search query is empty, skipping search.");
            return Collections.emptyList();
        }
        if (!StringUtils.hasText(accessToken)) {
            log.error("Cannot search tracks: Access token is missing.");
            return Collections.emptyList();
        }
        WebClient webClient = webClientBuilder.baseUrl(spotifyApiUrl).build();
        int effectiveLimit = Math.max(1, Math.min(limit, 50));
        log.debug("Searching Spotify tracks with query='{}', limit={}", query, effectiveLimit);
        try {
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", query)
                            .queryParam("type", "track")
                            .queryParam("limit", effectiveLimit)
                            .queryParam("market", "from_token")
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .onStatus(status -> status.isError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .switchIfEmpty(Mono.just("{ \"error\": \"Empty error body\" }"))
                                    .flatMap(body -> {
                                        log.error("Spotify API error during search: {} - Query: '{}' - Body: {}", clientResponse.statusCode(), query, body);
                                        return Mono.empty();
                                    }))
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            if (response == null) {
                log.warn("Spotify search failed or returned error status for query '{}'", query);
                return Collections.emptyList();
            }
            if (response.get("tracks") instanceof Map tracksMap && tracksMap.get("items") instanceof List<?> trackItemsRaw) {
                log.debug("Spotify search found {} raw items for query '{}'", trackItemsRaw.size(), query);
                List<Map<String, Object>> validTracks = trackItemsRaw.stream()
                        .filter(item -> item instanceof Map)
                        .map(item -> (Map<String, Object>) item)
                        .filter(trackMap -> trackMap.get("id") instanceof String && StringUtils.hasText((String) trackMap.get("id")))
                        .collect(Collectors.toList());
                if (validTracks.size() < trackItemsRaw.size()) {
                    log.warn("Filtered out {} invalid items from Spotify search results for query '{}'", trackItemsRaw.size() - validTracks.size(), query);
                }
                return validTracks;
            } else {
                log.debug("No valid 'tracks' -> 'items' list found in Spotify search response for query '{}'", query);
            }
        } catch (Exception e) {
            log.error("Error during Spotify search for query '{}': {}", query, e.getMessage(), e);
        }
        return Collections.emptyList();
    }


    private List<String> parseTopArtistsCache(String cache, int limit) {
        // ... (existing method, no changes)
        if (!StringUtils.hasText(cache)) {
            return Collections.emptyList();
        }
        return Arrays.stream(cache.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .limit(limit)
                .collect(Collectors.toList());
    }
}