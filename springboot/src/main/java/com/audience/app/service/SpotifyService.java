package com.audience.app.service;

import com.audience.app.dto.spotify.MoodProfile;
import com.audience.app.dto.spotify.SpotifyRecommendationRequest;
import com.audience.app.entity.MoodType;
import com.audience.app.entity.User;
import lombok.Getter; // Add Getter for spotifyApiUrl
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils; // Use Spring's CollectionUtils
import org.springframework.util.StringUtils; // Use Spring's StringUtils
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono; // Import Mono for error handling

import java.time.Duration;
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
    @Getter // Expose getter for PlaylistOrchestrationService token validation
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

    // Make static and public for use in PlaylistOrchestrationService
    public static final Set<String> VALID_SPOTIFY_GENRES = Set.of(
            // More comprehensive list based on Spotify API documentation / common usage
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


    /**
     * Get Spotify access token using Client Credentials flow
     * (Generally not needed for user-specific actions, but useful for generic searches)
     */
    public Mono<String> getClientAccessToken() { // Return Mono for async
        WebClient webClient = webClientBuilder.baseUrl(spotifyAccountsUrl).build();

        String credentials = Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes());

        return webClient.post()
                .uri("/token")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + credentials)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                .bodyValue("grant_type=client_credentials")
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> response != null ? (String) response.get("access_token") : null)
                .doOnError(e -> log.error("Error getting client access token", e));
    }


    /**
     * Get user's top artists for personalization (returns names instead of IDs for Last.fm compatibility)
     */
    public List<String> getUserTopArtists(User user, int limit) {
        // Cache Check (no change needed here)
        if (user.getTopArtistsCache() != null &&
                user.getTopArtistsCacheUpdatedAt() != null &&
                user.getTopArtistsCacheUpdatedAt().plusHours(topArtistsCacheHours).isAfter(LocalDateTime.now())) {

            log.info("Using cached top artists for user: {}", user.getSpotifyId());
            return parseTopArtistsCache(user.getTopArtistsCache(), limit);
        }

        log.info("Fetching fresh top artists for user: {}", user.getSpotifyId());
        WebClient webClient = webClientBuilder.baseUrl(spotifyApiUrl).build();

        try {
            // Using block() here is acceptable if the calling method is @Transactional
            // but consider async propagation if performance becomes an issue.
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/me/top/artists")
                            .queryParam("limit", Math.min(limit, 50)) // Spotify limit is 50
                            .queryParam("time_range", "medium_term") // Last 6 months
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.getAccessToken())
                    .retrieve()
                    // Add error handling for 4xx/5xx responses
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        log.error("Spotify API error fetching top artists: {} - Body: {}", clientResponse.statusCode(), body);
                                        return Mono.error(new RuntimeException("Failed to fetch top artists, status: " + clientResponse.statusCode()));
                                    }))
                    .bodyToMono(Map.class)
                    .block(); // block() can throw WebClientResponseException on errors


            if (response == null || !response.containsKey("items")) {
                log.warn("No top artists found or unexpected response for user: {}", user.getSpotifyId());
                return Collections.emptyList();
            }

            // Ensure items is actually a List
            Object itemsObj = response.get("items");
            if (!(itemsObj instanceof List<?>)) {
                log.warn("Spotify response for top artists field 'items' was not a list for user {}. Response: {}", user.getSpotifyId(), response);
                return Collections.emptyList();
            }

            List<Map<String, Object>> items = (List<Map<String, Object>>) itemsObj;

            if (CollectionUtils.isEmpty(items)) {
                log.info("User {} has no top artists in the medium term.", user.getSpotifyId());
                return Collections.emptyList();
            }

            List<String> artistNames = items.stream()
                    // Defensive checks for item structure
                    .filter(item -> item != null && item.get("name") instanceof String)
                    .map(item -> (String) item.get("name"))
                    .filter(StringUtils::hasText) // Ensure name is not null or empty
                    .limit(limit)
                    .collect(Collectors.toList());

            // Cache the result
            if (!artistNames.isEmpty()) {
                user.setTopArtistsCache(String.join(",", artistNames));
                user.setTopArtistsCacheUpdatedAt(LocalDateTime.now());
                // Note: Saving the user entity should happen in the calling service (@Transactional context)
                log.info("Fetched and cached {} top artists for user: {}", artistNames.size(), user.getSpotifyId());
            } else {
                log.info("Found 0 valid top artists for user: {}", user.getSpotifyId());
            }

            return artistNames;

        } catch (WebClientResponseException e) {
            // Specific handling for common errors like 401 Unauthorized (token expired)
            if (e.getStatusCode().value() == 401) {
                log.error("Unauthorized (401) fetching top artists for user: {}. Access token likely expired.", user.getSpotifyId());
                // Consider throwing a custom exception to trigger token refresh logic
                // throw new SpotifyTokenExpiredException("Access token expired for user " + user.getSpotifyId());
            } else {
                log.error("WebClientResponseException fetching top artists for user: {}: {} - Body: {}",
                        user.getSpotifyId(), e.getStatusCode(), e.getResponseBodyAsString(), e);
            }
            return Collections.emptyList(); // Return empty on error
        } catch (Exception e) {
            log.error("Unexpected error fetching top artists for user: {}", user.getSpotifyId(), e);
            return Collections.emptyList();
        }
    }


    /**
     * Updated: Get track recommendations using Spotify search, optionally boosted by artists/genres.
     */
    public List<Map<String, Object>> getRecommendations(
            SpotifyRecommendationRequest request,
            String accessToken) {

        List<String> seedArtistsInput = request.getSeedArtists() != null ? request.getSeedArtists() : Collections.emptyList();
        // Ensure genres from request are valid Spotify genres
        List<String> seedGenresInput = request.getSeedGenres() != null ? request.getSeedGenres().stream()
                .map(String::trim).map(String::toLowerCase)
                .filter(VALID_SPOTIFY_GENRES::contains)
                .distinct() // Avoid duplicate genres
                .limit(5) // Spotify search has a limit on genre seeds (implicitly)
                .collect(Collectors.toList()) : Collections.emptyList();

        MoodProfile mood = request.getMoodProfile();
        int limit = request.getLimit() != null && request.getLimit() > 0 ? Math.min(request.getLimit(), 50) : 10; // Default 10, max 50
        String promptKeywords = request.getPromptKeywords() != null ? request.getPromptKeywords().trim() : "";

        // Use LinkedHashSet to maintain insertion order and ensure uniqueness
        Set<Map<String, Object>> candidateTracksSet = new LinkedHashSet<>();


        // --- Strategy: Build a flexible search query ---
        StringBuilder baseQueryBuilder = new StringBuilder();

        // 1. Add Prompt Keywords (most important)
        if (StringUtils.hasText(promptKeywords)) {
            // Simple keyword cleanup - remove quotes that might break search
            String cleanedKeywords = promptKeywords.replace("\"", "");
            baseQueryBuilder.append(cleanedKeywords).append(" ");
        }


        // 2. Add Mood Keywords (optional, less strict than genre)
        String moodQuery = getMoodKeywords(mood);
        if (StringUtils.hasText(moodQuery)) {
            baseQueryBuilder.append(moodQuery).append(" ");
        }

        // 3. Add Year Range (less restrictive)
        baseQueryBuilder.append(" year:2000-").append(LocalDateTime.now().getYear()); // Broaden year range

        String baseQuery = baseQueryBuilder.toString().trim();

        // --- Execute Searches ---

        // Search 1: Base Query + Genres (if any genres provided)
        if (!seedGenresInput.isEmpty()) {
            String genreFilter = seedGenresInput.stream()
                    .map(g -> "genre:\"" + g + "\"")
                    .collect(Collectors.joining(" ")); // Use space for OR behavior in Spotify search
            String query1 = (baseQuery + " " + genreFilter).trim();
            log.info("Search 1 (Keywords + Mood + Year + Genres): {}", query1);
            candidateTracksSet.addAll(searchTracks(query1, limit * 2, accessToken)); // Get more initially
        } else if (StringUtils.hasText(baseQuery)) {
            // Search 1 variation: Base Query only (if no genres)
            log.info("Search 1 (Keywords + Mood + Year only, no genres specified): {}", baseQuery);
            candidateTracksSet.addAll(searchTracks(baseQuery, limit * 2, accessToken));
        } else {
            log.warn("Cannot perform initial search: No keywords, mood, or genres provided.");
        }


        // Search 2: Boost with Artists (if personalization on and needed)
        if (candidateTracksSet.size() < limit && !seedArtistsInput.isEmpty()) {
            List<String> artistsToSearch = new ArrayList<>(seedArtistsInput);
            // Limit similar artist lookup to avoid excessive calls / potential rate limits
            if (StringUtils.hasText(lastFmApiKey)) { // Only call Last.fm if API key is present
                for (String seedArtist : seedArtistsInput.subList(0, Math.min(1, seedArtistsInput.size()))) { // Only use top 1 seed artists for similarity
                    artistsToSearch.addAll(getSimilarArtists(seedArtist, 2)); // Get only 2 similar
                }
            }


            for (String artist : artistsToSearch.stream().distinct().limit(3).toList()) { // Limit artist boosting to 3 distinct artists max
                String artistFilter = "artist:\"" + artist.replace("\"", "") + "\""; // Remove quotes from artist name
                String query2 = (artistFilter + " " + baseQuery).trim(); // Artist + keywords/mood/year
                log.info("Search 2 (Artist Boost): {}", query2);
                candidateTracksSet.addAll(searchTracks(query2, limit / 2, accessToken)); // Get fewer per artist
                if (candidateTracksSet.size() >= limit * 1.5) {
                    log.debug("Reached sufficient candidates after artist boost for '{}'. Stopping artist search.", artist);
                    break; // Stop if we have enough candidates
                }
            }
        }

        // Search 3: Fallback - Only Keywords + Year (if still not enough and keywords exist)
        if (candidateTracksSet.size() < limit / 2 && StringUtils.hasText(promptKeywords)) {
            log.warn("Few tracks found ({}); broadening search (Keywords + Year only)", candidateTracksSet.size());
            String query3 = (promptKeywords.replace("\"", "") + " year:2000-" + LocalDateTime.now().getYear()).trim(); // Clean keywords
            candidateTracksSet.addAll(searchTracks(query3, limit * 2, accessToken));
        }

        // Search 4: Final Fallback - Only Genres (if still desperate and genres exist)
        if (candidateTracksSet.size() < limit / 3 && !seedGenresInput.isEmpty()) {
            log.warn("Very few tracks found ({}); broadening search (Genres only)", candidateTracksSet.size());
            String genreFilterOnly = seedGenresInput.stream()
                    .map(g -> "genre:\"" + g + "\"")
                    .collect(Collectors.joining(" "));
            String query4 = genreFilterOnly.trim();
            candidateTracksSet.addAll(searchTracks(query4, limit * 2, accessToken));
        }


        if (candidateTracksSet.isEmpty()) {
            log.warn("No tracks found after all searches for request: {}", request);
            return Collections.emptyList();
        }

        // Convert Set to List, shuffle, and limit
        List<Map<String, Object>> finalTracks = new ArrayList<>(candidateTracksSet);
        Collections.shuffle(finalTracks); // Shuffle to mix results from different searches

        // TODO: Optional - Add Audio Features Filtering here if needed
        // Requires fetching audio features for track IDs and comparing with moodProfile targets

        finalTracks = finalTracks.subList(0, Math.min(limit, finalTracks.size()));

        log.info("Returned {} track recommendations after filtering and shuffling.", finalTracks.size());
        return finalTracks;
    }

    /** Helper to get mood-related keywords for search query */
    private String getMoodKeywords(MoodProfile mood) {
        if (mood == null) return "";
        MoodType moodType = getMoodTypeFromProfile(mood); // Assumes getMoodTypeFromProfile exists
        // Keep keywords simple and general for broader search matching
        return switch (moodType) {
            case UPBEAT -> "upbeat happy dance";
            case MELANCHOLIC -> "sad melancholic reflective";
            case ROMANTIC -> "romantic love slow";
            case ADVENTUROUS -> "epic adventurous powerful";
            case PEACEFUL -> "peaceful calm serene ambient";
            case ENERGETIC -> "energetic electronic fast";
            case INTENSE -> "intense powerful dark electronic";
            case NOSTALGIC -> "nostalgic retro";
            case DREAMY -> "dreamy ambient ethereal";
            case CHILL -> "chill lofi relaxed";
            default -> ""; // BALANCED or null
        };
    }


    // Add helper to get MoodType (assume you inject moodProfiles map)
    private MoodType getMoodTypeFromProfile(MoodProfile profile) {
        if (profile == null) return MoodType.BALANCED;
        // Find the closest match based on simplified criteria (e.g., valence and energy)
        // This is a basic example; a more sophisticated distance metric could be used.
        double targetValence = profile.getTargetValence() != null ? profile.getTargetValence() : 0.5;
        double targetEnergy = profile.getTargetEnergy() != null ? profile.getTargetEnergy() : 0.5;

        MoodType bestMatch = MoodType.BALANCED;
        double minDistance = Double.MAX_VALUE;

        for (Map.Entry<MoodType, MoodProfile> entry : moodProfiles.entrySet()) {
            MoodProfile currentProfile = entry.getValue();
            // Handle potential nulls in stored profiles
            double currentValence = currentProfile.getTargetValence() != null ? currentProfile.getTargetValence() : 0.5;
            double currentEnergy = currentProfile.getTargetEnergy() != null ? currentProfile.getTargetEnergy() : 0.5;


            // Simple Euclidean distance in Valence-Energy space
            double distance = Math.sqrt(Math.pow(targetValence - currentValence, 2) + Math.pow(targetEnergy - currentEnergy, 2));

            if (distance < minDistance) {
                minDistance = distance;
                bestMatch = entry.getKey();
            }
        }
        log.debug("Mapped input profile (V:{}, E:{}) to MoodType: {}", String.format("%.2f", targetValence), String.format("%.2f", targetEnergy), bestMatch);
        return bestMatch;

    }


    /**
     * Get similar artists from Last.fm (Keep as is, maybe reduce limit further)
     */
    public List<String> getSimilarArtists(String seedArtist, int limit) {
        if (!StringUtils.hasText(lastFmApiKey)) {
            log.warn("Last.fm API key not configured. Skipping similar artist search.");
            return Collections.emptyList();
        }
        if (!StringUtils.hasText(seedArtist)) {
            log.debug("Cannot get similar artists: seedArtist is empty.");
            return Collections.emptyList();
        }

        WebClient webClient = webClientBuilder.baseUrl("https://ws.audioscrobbler.com/2.0/").build();
        log.debug("Fetching up to {} similar artists for '{}' from Last.fm", limit, seedArtist);

        try {
            // Using block() here again, assuming @Transactional context allows it.
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .queryParam("method", "artist.getSimilar")
                            .queryParam("artist", seedArtist)
                            .queryParam("api_key", lastFmApiKey)
                            .queryParam("format", "json")
                            .queryParam("limit", Math.max(1, limit)) // Ensure limit is at least 1
                            .build())
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        log.error("Last.fm API error fetching similar artists for '{}': {} - Body: {}", seedArtist, clientResponse.statusCode(), body);
                                        // Don't throw, just return empty list from this method on Last.fm error
                                        return Mono.empty(); // Causes block() to return null
                                    }))
                    .bodyToMono(Map.class)
                    .block(); // Can throw, or return null if onStatus handled error with Mono.empty()

            if (response == null) {
                log.warn("Last.fm response was null after potential error for {}", seedArtist);
                return Collections.emptyList();
            }

            if (!response.containsKey("similarartists")) {
                log.warn("No 'similarartists' key in Last.fm response for {}. Response: {}", seedArtist, response);
                return Collections.emptyList();
            }

            // Handle case where similarartists might be something other than a Map (e.g., error string)
            Object similarObj = response.get("similarartists");
            if (!(similarObj instanceof Map)) {
                log.warn("Expected 'similarartists' to be a Map, but got {} for {}. Response: {}", similarObj.getClass().getSimpleName(), seedArtist, response);
                return Collections.emptyList();
            }
            Map<String, Object> similar = (Map<String, Object>) similarObj;


            // Last.fm sometimes returns just a string description instead of the artist list if none found or error
            Object artistObj = similar.get("artist");
            if (!(artistObj instanceof List)) {
                log.info("No similar artists list found on Last.fm for {} (field 'artist' was not a list). Value: {}", seedArtist, artistObj);
                return Collections.emptyList();
            }
            List<Map<String, Object>> artists = (List<Map<String, Object>>) artistObj;

            // Filter by match >= 0.4 (slightly looser) and extract names
            return artists.stream()
                    .filter(Objects::nonNull) // Filter out null maps in the list
                    .filter(a -> {
                        try {
                            // Match can be String or Number, ensure 'a' and 'match' are not null
                            Object matchObj = a.get("match");
                            if (matchObj == null) return false;
                            double matchValue = Double.parseDouble(matchObj.toString());
                            return matchValue >= 0.4;
                        } catch (NumberFormatException | NullPointerException e) {
                            log.warn("Could not parse match value for artist in Last.fm response: {}", a.get("name"), e);
                            return false;
                        }
                    })
                    .map(a -> a.get("name"))
                    .filter(name -> name instanceof String && StringUtils.hasText((String) name)) // Ensure name is a non-empty String
                    .map(name -> (String) name)
                    .limit(limit) // Apply limit after filtering
                    .collect(Collectors.toList());

        } catch (WebClientResponseException e) {
            // Already logged in onStatus, log again if block throws
            log.error("WebClientResponseException fetching similar artists from Last.fm for: {}: {}",
                    seedArtist, e.getStatusCode(), e);
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Unexpected error fetching similar artists from Last.fm for: {}", seedArtist, e);
            return Collections.emptyList();
        }
    }


    // ---- keep createSpotifyPlaylist simple and separate from recommendations ----
    public String createSpotifyPlaylist(User user, String title, String description,
                                        List<String> trackUris, boolean isPublic) {
        WebClient webClient = webClientBuilder.baseUrl(spotifyApiUrl).build();

        // Validate inputs
        if (user == null || !StringUtils.hasText(user.getSpotifyId()) || !StringUtils.hasText(user.getAccessToken())) {
            log.error("Cannot create Spotify playlist: Invalid user data provided.");
            return null;
        }
        if (CollectionUtils.isEmpty(trackUris)) {
            log.warn("Cannot create Spotify playlist for user {}: No track URIs provided.", user.getSpotifyId());
            // Decide: return null or create empty playlist? Returning null for now.
            return null;
        }
        if (!StringUtils.hasText(title)) {
            title = "Audiance Generated Playlist"; // Default title
            log.warn("Playlist title was empty, using default: '{}'", title);
        }
        // Sanitize description
        description = (description != null) ? description : "";


        log.info("Creating Spotify playlist for user {}: title='{}', public={}, {} tracks", user.getSpotifyId(), title, isPublic, trackUris.size());

        try {
            Map<String, Object> createRequest = Map.of(
                    "name", title,
                    "description", description,
                    "public", isPublic
            );

            log.debug("Spotify Create Playlist Request Body: {}", createRequest);

            Map<String, Object> playlistResponse = webClient.post()
                    .uri("/users/" + user.getSpotifyId() + "/playlists")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.getAccessToken())
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .bodyValue(createRequest)
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .flatMap(body -> {
                                        log.error("Spotify API error creating playlist for user {}: {} - Body: {}", user.getSpotifyId(), clientResponse.statusCode(), body);
                                        return Mono.error(new RuntimeException("Failed to create playlist, status: " + clientResponse.statusCode()));
                                    }))
                    .bodyToMono(Map.class)
                    // Add timeout
                    // .timeout(Duration.ofSeconds(15))
                    .block(); // Can throw


            if (playlistResponse == null || !(playlistResponse.get("id") instanceof String playlistId) || !StringUtils.hasText(playlistId)) {
                log.error("Failed to create Spotify playlist for user {} - invalid ID in response: {}", user.getSpotifyId(), playlistResponse);
                return null;
            }

            log.info("Created Spotify playlist with ID: {}", playlistId);

            // Add tracks in batches (Spotify limit is 100 per request)
            int batchSize = 100;
            boolean allBatchesSuccessful = true; // Track if any batch fails
            for (int i = 0; i < trackUris.size(); i += batchSize) {
                List<String> batchUris = trackUris.subList(i, Math.min(i + batchSize, trackUris.size()));
                log.debug("Adding batch of {} tracks ({} to {}) to playlist {}", batchUris.size(), i + 1, Math.min(i + batchSize, trackUris.size()), playlistId);
                Map<String, Object> addTracksRequest = Map.of("uris", batchUris);

                try {
                    Map<String, Object> addResponse = webClient.post()
                            .uri("/playlists/" + playlistId + "/tracks")
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.getAccessToken())
                            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                            .bodyValue(addTracksRequest)
                            .retrieve()
                            .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                                    clientResponse -> clientResponse.bodyToMono(String.class)
                                            .flatMap(body -> {
                                                log.error("Spotify API error adding tracks batch to playlist {}: {} - Body: {}", playlistId, clientResponse.statusCode(), body);
                                                // Fail the batch but allow loop to continue if desired, or rethrow
                                                return Mono.error(new RuntimeException("Failed to add track batch, status: " + clientResponse.statusCode()));
                                            }))
                            .bodyToMono(Map.class)
                            // Add retry logic here if desired
                            // .timeout(Duration.ofSeconds(15))
                            .block(); // Can throw

                    if (addResponse == null || !addResponse.containsKey("snapshot_id")) {
                        log.error("Failed to add batch of tracks to playlist {} - invalid response: {}", playlistId, addResponse);
                        allBatchesSuccessful = false; // Mark failure
                    } else {
                        log.info("Successfully added batch of {} tracks to playlist: {}", batchUris.size(), playlistId);
                    }
                } catch (WebClientResponseException batchEx) {
                    log.error("WebClientResponseException adding track batch {} to playlist {}: {} - Body: {}",
                            (i/batchSize + 1), playlistId, batchEx.getStatusCode(), batchEx.getResponseBodyAsString(), batchEx);
                    allBatchesSuccessful = false; // Mark failure
                    // Optionally break or continue based on severity
                } catch (Exception batchEx) {
                    log.error("Unexpected error adding track batch {} to playlist {}: {}", (i/batchSize + 1), playlistId, batchEx.getMessage(), batchEx);
                    allBatchesSuccessful = false; // Mark failure
                    // Optionally break or continue
                }
            }
            if (!allBatchesSuccessful) {
                log.warn("One or more batches failed to add tracks to playlist {}", playlistId);
                // Decide if playlistId should still be returned or if null indicates partial failure
            }


            return playlistId;

        } catch (WebClientResponseException e) {
            log.error("WebClientResponseException during playlist creation phase for user {}: {} - Body: {}",
                    user.getSpotifyId(), e.getStatusCode(), e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            log.error("Unexpected error creating Spotify playlist shell for user {}", user.getSpotifyId(), e);
        }
        return null; // Return null if creation failed at any critical step
    }


    /**
     * Get mood profile for a given mood type
     */
    public MoodProfile getMoodProfile(MoodType moodType) {
        if (moodType == null) {
            log.warn("MoodType was null, returning BALANCED profile.");
            // Ensure BALANCED exists or provide a default fallback
            return moodProfiles.getOrDefault(MoodType.BALANCED, MoodProfile.builder().targetValence(0.5).targetEnergy(0.5).build());
        }
        // Ensure the requested mood exists, otherwise fallback to BALANCED
        return moodProfiles.getOrDefault(moodType, moodProfiles.get(MoodType.BALANCED));

    }


    /**
     * Search for tracks using Spotify API. Returns a List of track objects (Map).
     */
    public List<Map<String, Object>> searchTracks(String query, int limit, String accessToken) {
        if (!StringUtils.hasText(query)) {
            log.warn("Search query is empty, skipping search.");
            return Collections.emptyList();
        }
        if (!StringUtils.hasText(accessToken)) {
            log.error("Cannot search tracks: Access token is missing.");
            return Collections.emptyList();
        }

        WebClient webClient = webClientBuilder.baseUrl(spotifyApiUrl).build();
        // Ensure limit is within Spotify's valid range (1-50)
        int effectiveLimit = Math.max(1, Math.min(limit, 50));
        log.debug("Searching Spotify tracks with query='{}', limit={}", query, effectiveLimit);

        try {
            Map<String, Object> response = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/search")
                            .queryParam("q", query)
                            .queryParam("type", "track")
                            .queryParam("limit", effectiveLimit)
                            .queryParam("market", "from_token") // Use user's market if available
                            .build())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    // Centralized error logging for search
                    .onStatus(status -> status.isError(), // Handles 4xx and 5xx
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    // Use switchIfEmpty in case body is empty
                                    .switchIfEmpty(Mono.just("{ \"error\": \"Empty error body\" }"))
                                    .flatMap(body -> {
                                        log.error("Spotify API error during search: {} - Query: '{}' - Body: {}", clientResponse.statusCode(), query, body);
                                        // Return an empty Mono to signal error without throwing for search
                                        return Mono.empty();
                                    }))
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            // Check if response is null due to error handling in onStatus
            if (response == null) {
                log.warn("Spotify search failed or returned error status for query '{}'", query);
                return Collections.emptyList();
            }

            // More robust extraction of tracks list
            Object tracksObj = response.get("tracks");
            if (tracksObj instanceof Map tracksMap) {
                Object itemsObj = tracksMap.get("items");
                if (itemsObj instanceof List trackItems) {
                    log.debug("Spotify search found {} raw items for query '{}'", trackItems.size(), query);
                    // Filter results more safely
                    List<Map<String, Object>> validTracks = new ArrayList<>();
                    for(Object item : trackItems) {
                        if (item instanceof Map trackMap) {
                            if (trackMap.get("id") instanceof String && StringUtils.hasText((String) trackMap.get("id"))) {
                                validTracks.add(trackMap);
                            } else {
                                log.warn("Spotify search result item missing or invalid 'id': {}", trackMap);
                            }
                        } else {
                            log.warn("Spotify search result item was not a Map: {}", item);
                        }
                    }
                    return validTracks;

                } else {
                    log.debug("Spotify search response 'tracks' object did not contain an 'items' list for query '{}'", query);
                }
            } else {
                log.debug("No 'tracks' map found in Spotify search response for query '{}'", query);
            }


        } catch (WebClientResponseException e) {
            // Should be caught by onStatus now, but keep as fallback
            log.error("WebClientResponseException during Spotify search (should have been handled by onStatus): {} - Query: '{}' - Body: {}",
                    e.getStatusCode(), query, e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            // Catch other potential exceptions like timeout
            log.error("Unexpected error during Spotify search with query '{}': {}", query, e.getMessage(), e);
        }

        return Collections.emptyList(); // Return empty list on any error or no results
    }


    private List<String> parseTopArtistsCache(String cache, int limit) {
        if (!StringUtils.hasText(cache)) {
            return Collections.emptyList();
        }
        // Simple comma-separated cache format
        return Arrays.stream(cache.split(","))
                .map(String::trim) // Trim whitespace
                .filter(StringUtils::hasText) // Filter out empty strings
                .limit(limit)
                .collect(Collectors.toList());
    }
}

