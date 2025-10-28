package com.audience.app.service;

import com.audience.app.dto.ai.AIAnalysisResponse;
import com.audience.app.dto.ai.DirectModeAnalysis;
import com.audience.app.dto.ai.SceneAnalysis;
import com.audience.app.dto.request.PlaylistGenerateRequest;
import com.audience.app.dto.response.PlaylistResponse;
import com.audience.app.dto.response.SceneResponse;
import com.audience.app.dto.response.TrackResponse;
import com.audience.app.dto.spotify.MoodProfile;
import com.audience.app.dto.spotify.SpotifyRecommendationRequest;
import com.audience.app.entity.*;
import com.audience.app.repository.PlaylistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaylistOrchestrationService {

    private final PlaylistRepository playlistRepository;
    private final UserService userService;
    private final AIServiceClient aiServiceClient;
    private final SpotifyService spotifyService;

    @Value("${app.playlist.default-tracks-per-scene:5}")
    private int defaultTracksPerScene;

    @Value("${app.playlist.max-total-tracks:50}")
    private int maxTotalTracks;
    
    private static final List<String> STOPWORDS = List.of(
            "a", "an", "the", "with", "in", "to", "and", "or", "for", "i", "my", "me", "is", "are",
            "music", "playlist", "song", "songs", "track", "tracks", "vibe", "mood", "feeling"
    );


    /**
     * Main method: Generate complete playlist from user prompt
     */
    @Transactional
    public PlaylistResponse generatePlaylist(String spotifyId, PlaylistGenerateRequest request) {
        long startTime = System.currentTimeMillis();

        log.info("Starting playlist generation for user: {}", spotifyId);

        // 1. Get user
        User user = userService.findBySpotifyId(spotifyId)
                .orElseThrow(() -> new RuntimeException("User not found: " + spotifyId));

        // 2. Call AI service to analyze prompt
        AIAnalysisResponse aiAnalysis = aiServiceClient.analyzePrompt(
                request.getPrompt(),
                request.getSelectedGenres()
        );

        // 3. Get user's top artists for personalization (if enabled)
        List<String> topArtists = new ArrayList<>();
        if (Boolean.TRUE.equals(request.getUsePersonalization())) { // Check boolean value correctly
            topArtists = spotifyService.getUserTopArtists(user, 2); // Limit to 2 for less bias
            log.info("Using {} top artists for personalization", topArtists.size());
        } else {
            log.info("Personalization disabled by request.");
        }


        // 4. Create playlist entity
        Playlist playlist = createPlaylistEntity(user, request, aiAnalysis);

        // 5. Generate scenes and tracks based on AI analysis mode
        if (aiAnalysis != null && aiAnalysis.getMode() == AIAnalysisResponse.AnalysisMode.STORY) {
            generateStoryModePlaylist(playlist, aiAnalysis, request, topArtists, user);
        } else if (aiAnalysis != null && aiAnalysis.getMode() == AIAnalysisResponse.AnalysisMode.DIRECT) {
            generateDirectModePlaylist(playlist, aiAnalysis, request, topArtists, user);
        } else {
            log.error("AI Analysis response was null or mode was not set. Cannot generate tracks.");
            Scene fallbackScene = Scene.builder()
                    .playlist(playlist) // Set relationship
                    .sceneNumber(1)
                    .mood(MoodType.BALANCED)
                    .description("Could not generate tracks due to AI analysis error.")
                    .tracks(new ArrayList<>()) // Initialize tracks list
                    .build();
            playlist.getScenes().add(fallbackScene);
        }

        // 6. Save playlist (includes scenes and tracks due to cascade)
        // Ensure bidirectional relationships are set *before* saving
        for (Scene scene : playlist.getScenes()) {
            scene.setPlaylist(playlist);
            if (scene.getTracks() != null) {
                for (Track track : scene.getTracks()) {
                    track.setScene(scene);
                }
            }
        }
        Playlist savedPlaylist = playlistRepository.save(playlist);


        // 7. Create Spotify playlist if requested
        if (Boolean.TRUE.equals(request.getCreateSpotifyPlaylist())) {
            // Pass the SAVED playlist which now has IDs
            createSpotifyPlaylistForUser(savedPlaylist, user);
            // Re-save might be needed if spotifyPlaylistId was updated within the transaction
            // Saving again at the end handles this.
        } else {
            log.info("Spotify playlist creation not requested (create_spotify_playlist={})", request.getCreateSpotifyPlaylist());
        }


        // 8. Calculate generation time and save potentially updated Spotify ID
        long endTime = System.currentTimeMillis();
        savedPlaylist.setGenerationTimeMs(endTime - startTime);
        // spotifyPlaylistId might have been set in createSpotifyPlaylistForUser
        savedPlaylist = playlistRepository.save(savedPlaylist); // Save again

        log.info("Playlist generation complete. ID: {}, Scenes: {}, Total tracks: {}, Spotify ID: {}, Time: {}ms",
                savedPlaylist.getId(),
                savedPlaylist.getScenes() != null ? savedPlaylist.getScenes().size() : 0,
                savedPlaylist.getScenes() != null ? savedPlaylist.getScenes().stream()
                        .filter(Objects::nonNull)
                        .mapToInt(s -> s.getTracks() != null ? s.getTracks().size() : 0).sum()
                        : 0,
                savedPlaylist.getSpotifyPlaylistId() != null ? savedPlaylist.getSpotifyPlaylistId() : "N/A", // Log spotify ID
                savedPlaylist.getGenerationTimeMs());

        // Fetch again with scenes and tracks for the response DTO mapping
        // Using findByIdWithScenes ensures lazy collections are loaded within the transaction
        Playlist finalPlaylist = playlistRepository.findByIdWithScenes(savedPlaylist.getId())
                .orElse(savedPlaylist); // Fallback

        return mapToPlaylistResponse(finalPlaylist);
    }

    /**
     * Generate playlist for STORY mode (multiple scenes)
     */
    private void generateStoryModePlaylist(Playlist playlist, AIAnalysisResponse aiAnalysis,
                                           PlaylistGenerateRequest request, List<String> topArtists, User user) {

        log.info("Generating STORY mode playlist with {} scenes", aiAnalysis.getScenes() != null ? aiAnalysis.getScenes().size() : 0);

        if (CollectionUtils.isEmpty(aiAnalysis.getScenes())) {
            log.warn("AI returned STORY mode but no scenes. Aborting track generation.");
            return;
        }

        int tracksPerScene = request.getTracksPerScene() != null && request.getTracksPerScene() > 0
                ? request.getTracksPerScene()
                : defaultTracksPerScene;

        String accessToken = user.getAccessToken();

        for (SceneAnalysis sceneAnalysis : aiAnalysis.getScenes()) {
            if (sceneAnalysis == null) {
                log.warn("Skipping null scene analysis object in story mode list.");
                continue;
            }
            Scene scene = Scene.builder()
                    // Playlist relationship set before main save
                    .sceneNumber(sceneAnalysis.getSceneNumber() != null ? sceneAnalysis.getSceneNumber() : playlist.getScenes().size() + 1)
                    .mood(sceneAnalysis.getMood() != null ? sceneAnalysis.getMood() : MoodType.BALANCED)
                    .description(sceneAnalysis.getDescription() != null ? sceneAnalysis.getDescription() : "Scene")
                    .tracks(new ArrayList<>()) // Initialize tracks list
                    .build();

            List<Track> tracks = getTracksForScene(
                    scene,
                    sceneAnalysis,
                    request,
                    topArtists,
                    tracksPerScene,
                    accessToken
            );

            scene.setTracks(tracks); // Tracks have scene set inside getTracksForScene
            playlist.getScenes().add(scene);
        }
    }

    /**
     * Generate playlist for DIRECT mode (single scene, simpler)
     */
    private void generateDirectModePlaylist(Playlist playlist, AIAnalysisResponse aiAnalysis,
                                            PlaylistGenerateRequest request, List<String> topArtists, User user) {

        log.info("Generating DIRECT mode playlist");

        DirectModeAnalysis directAnalysis = aiAnalysis.getDirectAnalysis();
        if (directAnalysis == null) {
            log.error("AI returned DIRECT mode but no directAnalysis object. Adding fallback scene.");
            Scene fallbackScene = Scene.builder()
                    .playlist(playlist)
                    .sceneNumber(1)
                    .mood(MoodType.BALANCED)
                    .description("Generated based on fallback due to AI analysis error.")
                    .tracks(new ArrayList<>())
                    .build();
            playlist.getScenes().add(fallbackScene);
            return;
        }

        int totalTracks = Math.min(
                request.getTracksPerScene() != null && request.getTracksPerScene() > 0 ? request.getTracksPerScene() * 2 : 10,
                maxTotalTracks
        );
        if (totalTracks <= 0) totalTracks = defaultTracksPerScene;

        Scene scene = Scene.builder()
                // Playlist relationship set before main save
                .sceneNumber(1)
                .mood(directAnalysis.getMood() != null ? directAnalysis.getMood() : MoodType.BALANCED)
                .description(directAnalysis.getTheme() != null ? directAnalysis.getTheme() : "Playlist") // Fallback theme
                .tracks(new ArrayList<>()) // Initialize tracks list
                .build();

        List<Track> tracks = getTracksForDirectMode(
                scene,
                directAnalysis,
                request,
                topArtists,
                totalTracks,
                user.getAccessToken()
        );

        scene.setTracks(tracks); // Tracks have scene set inside getTracksForDirectMode
        playlist.getScenes().add(scene);
    }

    /**
     * Get tracks for a story scene using mood profiles
     */
    private List<Track> getTracksForScene(Scene scene, SceneAnalysis sceneAnalysis,
                                          PlaylistGenerateRequest request,
                                          List<String> topArtists,
                                          int limit, String accessToken) {

        MoodProfile moodProfile = spotifyService.getMoodProfile(sceneAnalysis.getMood());

        // Reliable Genre Fallback
        List<String> genres = determineGenres(sceneAnalysis.getSuggestedGenres(), request.getSelectedGenres());
        log.debug("Using genres for scene {}: {}", scene.getSceneNumber(), genres);

        // Extract keywords ONLY from the scene description for story mode
        String promptKeywords = extractKeywords(sceneAnalysis.getDescription());
        log.debug("Extracted keywords for scene {}: '{}'", scene.getSceneNumber(), promptKeywords);

        SpotifyRecommendationRequest recommendationRequest = SpotifyRecommendationRequest.builder()
                .seedGenres(genres)
                .seedArtists(topArtists != null ? topArtists : List.of())
                .moodProfile(moodProfile)
                .limit(limit)
                .promptKeywords(promptKeywords)
                .build();

        List<Map<String, Object>> spotifyTracks = spotifyService.getRecommendations(
                recommendationRequest,
                accessToken
        );

        List<Track> tracks = mapAndAssociateTracks(spotifyTracks, scene);

        log.info("Generated {} tracks for scene {} ({})",
                tracks.size(), scene.getSceneNumber(), scene.getMood());

        return tracks;
    }

    /**
     * Get tracks for direct mode
     */
    private List<Track> getTracksForDirectMode(Scene scene, DirectModeAnalysis directAnalysis,
                                               PlaylistGenerateRequest request,
                                               List<String> topArtists,
                                               int limit, String accessToken) {

        MoodProfile moodProfile = spotifyService.getMoodProfile(directAnalysis.getMood());

        // Reliable Genre Fallback
        List<String> genres = determineGenres(directAnalysis.getExtractedGenres(), request.getSelectedGenres());
        log.debug("Using genres for direct mode: {}", genres);

        // Extract keywords from BOTH original prompt AND AI theme for direct mode
        String promptKeywords = extractKeywords(request.getPrompt() + " " + directAnalysis.getTheme());
        log.debug("Extracted keywords for direct mode: '{}'", promptKeywords);

        SpotifyRecommendationRequest recommendationRequest = SpotifyRecommendationRequest.builder()
                .seedGenres(genres)
                .seedArtists(topArtists != null ? topArtists : List.of())
                .moodProfile(moodProfile)
                .limit(limit)
                .promptKeywords(promptKeywords)
                .build();

        List<Map<String, Object>> spotifyTracks = spotifyService.getRecommendations(
                recommendationRequest,
                accessToken
        );

        // Convert and associate tracks
        List<Track> tracks = mapAndAssociateTracks(spotifyTracks, scene);

        log.info("Generated {} tracks for direct mode", tracks.size());

        return tracks;
    }

    /** Helper to determine genres, preferring valid AI genres, falling back to valid user genres */
    private List<String> determineGenres(List<String> aiGenres, List<String> userGenres) {
        List<String> validAiGenres = Optional.ofNullable(aiGenres).orElse(List.of()).stream()
                .filter(g -> g != null && SpotifyService.VALID_SPOTIFY_GENRES.contains(g.trim().toLowerCase()))
                .collect(Collectors.toList());

        if (!CollectionUtils.isEmpty(validAiGenres)) {
            return validAiGenres;
        } else {

            return Optional.ofNullable(userGenres).orElse(List.of()).stream()
                    .filter(g -> g != null && SpotifyService.VALID_SPOTIFY_GENRES.contains(g.trim().toLowerCase()))
                    .collect(Collectors.toList());
        }
    }

    /** Helper to map Spotify track data and associate with the Scene */
    private List<Track> mapAndAssociateTracks(List<Map<String, Object>> spotifyTracks, Scene scene) {
        List<Track> tracks = new ArrayList<>();
        if (spotifyTracks != null) {
            for (int i = 0; i < spotifyTracks.size(); i++) {
                Map<String, Object> spotifyTrack = spotifyTracks.get(i);
                if (spotifyTrack != null && spotifyTrack.get("id") != null) {
                    Track track = mapSpotifyTrackToEntity(spotifyTrack, scene, i + 1);
                    if (track != null) {
                        track.setScene(scene); // Set the owning side relationship
                        tracks.add(track);
                    }
                } else {
                    log.warn("Skipping invalid track data received at index {}", i);
                }
            }
        }
        return tracks;
    }


    /**
     * Improved Keyword Extraction - More selective.
     */
    private String extractKeywords(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalizedText = text.toLowerCase().replaceAll("[^a-z0-9\\s]", "");
        List<String> keywords = Arrays.stream(normalizedText.split("\\s+"))
                .map(String::trim)
                .filter(word -> !word.isBlank() && !STOPWORDS.contains(word))
                .distinct()
                .limit(5)
                .collect(Collectors.toList());
        return String.join(" ", keywords);
    }


    /**
     * Map Spotify API response to Track entity. Added null checks.
     */
    private Track mapSpotifyTrackToEntity(Map<String, Object> spotifyTrack, Scene scene, int position) {
        try {
            if (spotifyTrack == null || spotifyTrack.get("id") == null || spotifyTrack.get("name") == null) {
                log.warn("Missing essential track data (id or name), cannot map entity.");
                return null;
            }

            Object artistsObj = spotifyTrack.get("artists");
            List<Map<String, Object>> artists = (artistsObj instanceof List) ? (List<Map<String, Object>>) artistsObj : null;
            Object albumObj = spotifyTrack.get("album");
            Map<String, Object> album = (albumObj instanceof Map) ? (Map<String, Object>) albumObj : null;
            Object imagesObj = (album != null) ? album.get("images") : null;
            List<Map<String, Object>> images = (imagesObj instanceof List) ? (List<Map<String, Object>>) imagesObj : null;
            Object externalUrlsObj = spotifyTrack.get("external_urls");
            Map<String, Object> externalUrls = (externalUrlsObj instanceof Map) ? (Map<String, Object>) externalUrlsObj : null;

            String artistName = "Unknown Artist";
            if (!CollectionUtils.isEmpty(artists) && artists.get(0) != null && artists.get(0).get("name") != null) {
                artistName = artists.get(0).get("name").toString();
            }

            String albumName = "Unknown Album";
            if (album != null && album.get("name") instanceof String) {
                albumName = (String) album.get("name");
            }

            String imageUrl = null;
            if (!CollectionUtils.isEmpty(images) && images.get(0) != null && images.get(0).get("url") instanceof String) {
                imageUrl = (String) images.get(0).get("url");
            }

            String spotifyUrl = null;
            if (externalUrls != null && externalUrls.get("spotify") instanceof String) {
                spotifyUrl = (String) externalUrls.get("spotify");
            }
            String trackId = (String) spotifyTrack.get("id");
            if (spotifyUrl == null) {
                spotifyUrl = "https://open.spotify.com/track/" + trackId;
            }

            Integer durationMs = null;
            Object durationObj = spotifyTrack.get("duration_ms");
            if (durationObj instanceof Integer) {
                durationMs = (Integer) durationObj;
            } else if (durationObj != null) {
                try {
                    durationMs = (int) Double.parseDouble(durationObj.toString());
                } catch (NumberFormatException e) {
                    log.warn("Could not parse duration_ms: {}", durationObj);
                }
            }

            return Track.builder()
                    .spotifyId(trackId)
                    .name((String) spotifyTrack.get("name"))
                    .artist(artistName)
                    .album(albumName)
                    .imageUrl(imageUrl)
                    .previewUrl((String) spotifyTrack.get("preview_url"))
                    .externalUrl(spotifyUrl)
                    .durationMs(durationMs)
                    .position(position)
                    // Scene is set in the calling method (mapAndAssociateTracks)
                    .build();
        } catch (ClassCastException cce) {
            log.error("Type casting error mapping Spotify track data: {}. Data: {}", cce.getMessage(), spotifyTrack, cce);
            return null;
        } catch (Exception e) {
            log.error("Unexpected error mapping Spotify track data to entity: {}", spotifyTrack, e);
            return null;
        }
    }


    /**
     * Create Spotify playlist for user
     */
    private void createSpotifyPlaylistForUser(Playlist playlist, User user) {
        // Use the playlist entity to get tracks
        List<String> trackUris = List.of();
        if (playlist.getScenes() != null) {
            trackUris = playlist.getScenes().stream()
                    .filter(scene -> scene != null && scene.getTracks() != null)
                    .flatMap(scene -> scene.getTracks().stream())
                    .filter(track -> track != null && track.getSpotifyId() != null)
                    .map(track -> "spotify:track:" + track.getSpotifyId())
                    .collect(Collectors.toList());
        }

        log.info("Attempting Spotify export: {} tracks for playlist ID {} / user {}", trackUris.size(), playlist.getId(), user.getSpotifyId());

        if (trackUris.isEmpty()) {
            log.warn("No tracks found in the playlist entity (ID: {}) to add to Spotify.", playlist.getId());
            return;
        }

        if (!isValidAccessToken(user.getAccessToken())) {
            log.error("Invalid/expired access token—skipping Spotify export for playlist ID {}", playlist.getId());
            // TODO: Implement refresh token logic here or throw specific exception
            return;
        }

        // **FIX**: Use the isPublic value from the *Playlist entity*
        boolean isPublicPlaylist = Boolean.TRUE.equals(playlist.getIsPublic());

        String spotifyPlaylistId = spotifyService.createSpotifyPlaylist(
                user,
                playlist.getTitle(),
                playlist.getDescription(),
                trackUris,
                isPublicPlaylist // Use the value from the playlist entity
        );

        if (spotifyPlaylistId != null) {
            // Update the managed playlist entity (will be saved by @Transactional)
            playlist.setSpotifyPlaylistId(spotifyPlaylistId);
            log.info("Successfully requested Spotify playlist creation: {}. Spotify ID set on Playlist entity.", spotifyPlaylistId);
        } else {
            log.error("Spotify creation returned null ID—check SpotifyService logs for details for playlist ID {}", playlist.getId());
        }
    }


    /** Helper: Quick token validation */
    private boolean isValidAccessToken(String token) {
        if (!StringUtils.hasText(token)) { // Use StringUtils
            log.warn("isValidAccessToken called with null or empty token.");
            return false;
        }
        WebClient webClient = WebClient.builder().baseUrl(spotifyService.getSpotifyApiUrl()).build();
        try {
            webClient.get()
                    .uri("/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    // Corrected onStatus handler
                    .onStatus(status -> status.isError(), // Handles 4xx and 5xx
                            clientResponse -> clientResponse.bodyToMono(String.class)
                                    .switchIfEmpty(Mono.just("{ \"error\": \"Empty error body from Spotify /me\" }"))
                                    .flatMap(body -> {
                                        log.warn("Token validation failed via /me endpoint with status: {}. Body: {}", clientResponse.statusCode(), body);
                                        // Return Mono<Throwable>
                                        return Mono.error(new RuntimeException("Token validation failed with status: " + clientResponse.statusCode()));
                                    })
                    )
                    .bodyToMono(Void.class)
                    .block(Duration.ofSeconds(5));
            log.debug("Access token validated successfully via /me endpoint.");
            return true;
        } catch (Exception e) {
            log.warn("Access token validation failed: {} - {}", e.getClass().getSimpleName(), e.getMessage());
            if (log.isDebugEnabled()){
                log.debug("Stack trace for token validation failure:", e);
            }
            return false;
        }
    }


    /**
     * Create playlist entity from request
     */
    private Playlist createPlaylistEntity(User user, PlaylistGenerateRequest request,
                                          AIAnalysisResponse aiAnalysis) {
        String title = generatePlaylistTitle(request.getPrompt(), aiAnalysis);
        // Pass user's selected genres to description helper
        String description = generatePlaylistDescription(request.getPrompt(), aiAnalysis, request.getSelectedGenres());

        boolean isPublicRequest = Boolean.TRUE.equals(request.getIsPublic());

        return Playlist.builder()
                .title(title)
                .description(description)
                .originalNarrative(request.getPrompt())
                .aiAnalysisId(aiAnalysis != null ? aiAnalysis.getAnalysisId() : "fallback-or-error")
                .user(user)
                .isPublic(isPublicRequest)
                // Ensure list is modifiable if needed later, handle null
                .selectedGenres(request.getSelectedGenres() != null ? new ArrayList<>(request.getSelectedGenres()) : new ArrayList<>())
                .scenes(new ArrayList<>()) // Initialize scenes list
                .build();
    }


    /**
     * Generate playlist title from prompt
     */
    private String generatePlaylistTitle(String prompt, AIAnalysisResponse aiAnalysis) {
        if (aiAnalysis != null && aiAnalysis.getMode() == AIAnalysisResponse.AnalysisMode.STORY) {
            String firstSceneDesc = (!CollectionUtils.isEmpty(aiAnalysis.getScenes()) && aiAnalysis.getScenes().get(0) != null)
                    ? aiAnalysis.getScenes().get(0).getDescription()
                    : null;
            if (StringUtils.hasText(firstSceneDesc)) { // Use StringUtils
                return "Soundtrack: " + truncate(firstSceneDesc, 40);
            } else {
                return "Story Soundtrack: " + truncate(prompt, 30);
            }
        } else {
            return "Audiance: " + truncate(prompt, 50);
        }
    }


    /**
     * Generate playlist description
     */
    private String generatePlaylistDescription(String prompt, AIAnalysisResponse aiAnalysis, List<String> selectedGenres) { // Added selectedGenres
        if (aiAnalysis != null && aiAnalysis.getMode() == AIAnalysisResponse.AnalysisMode.STORY && !CollectionUtils.isEmpty(aiAnalysis.getScenes())) {
            int sceneCount = (int) aiAnalysis.getScenes().stream().filter(Objects::nonNull).count();
            return String.format("A %d-scene musical journey based on your story. Generated by Audiance.", sceneCount);
        } else {
            String genresString = "";
            // Prioritize AI extracted genres if available and valid
            if (aiAnalysis != null && aiAnalysis.getDirectAnalysis() != null && !CollectionUtils.isEmpty(aiAnalysis.getDirectAnalysis().getExtractedGenres())) {
                List<String> validAiGenres = aiAnalysis.getDirectAnalysis().getExtractedGenres().stream()
                        .filter(g -> g != null && SpotifyService.VALID_SPOTIFY_GENRES.contains(g.trim().toLowerCase()))
                        .collect(Collectors.toList());
                if (!CollectionUtils.isEmpty(validAiGenres)){
                    genresString = " Genres: " + String.join(", ", validAiGenres) + ".";
                }
            }
            // Fallback to user selected genres if AI genres weren't used or invalid
            if (genresString.isEmpty() && !CollectionUtils.isEmpty(selectedGenres)) {
                List<String> validUserGenres = selectedGenres.stream()
                        .filter(g -> g != null && SpotifyService.VALID_SPOTIFY_GENRES.contains(g.trim().toLowerCase()))
                        .collect(Collectors.toList());
                if (!CollectionUtils.isEmpty(validUserGenres)){
                    genresString = " Genres: " + String.join(", ", validUserGenres) + ".";
                }
            }
            return "Your personalized playlist created by Audiance based on: \"" + truncate(prompt, 80) + "\"." + genresString;
        }
    }


    /**
     * Map Playlist entity to response DTO
     */
    public PlaylistResponse mapToPlaylistResponse(Playlist playlist) {
        if (playlist == null) {
            log.warn("mapToPlaylistResponse called with null playlist");
            return null;
        }

        List<SceneResponse> sceneResponses = List.of();
        // Handle potential LazyInitializationException if not eagerly fetched
        try {
            if (playlist.getScenes() != null) {
                sceneResponses = playlist.getScenes().stream()
                        .filter(Objects::nonNull)
                        .map(this::mapToSceneResponse)
                        .collect(Collectors.toList());
            }
        } catch (org.hibernate.LazyInitializationException e) {
            log.warn("LazyInitializationException encountered while mapping scenes for Playlist ID {}. Returning empty scenes list. Consider using JOIN FETCH in repository.", playlist.getId());
        }

        String spotifyUrl = playlist.getSpotifyPlaylistId() != null
                ? "https://open.spotify.com/playlist/" + playlist.getSpotifyPlaylistId()
                : null;

        return PlaylistResponse.builder()
                .id(playlist.getId())
                .title(playlist.getTitle())
                .description(playlist.getDescription())
                .originalNarrative(playlist.getOriginalNarrative())
                .scenes(sceneResponses)
                .spotifyPlaylistId(playlist.getSpotifyPlaylistId())
                .spotifyPlaylistUrl(spotifyUrl)
                .isPublic(playlist.getIsPublic())
                .createdAt(playlist.getCreatedAt())
                .generationTimeMs(playlist.getGenerationTimeMs())
                .build();
    }

    private SceneResponse mapToSceneResponse(Scene scene) {
        if (scene == null) return null;

        List<TrackResponse> trackResponses = List.of();
        try {
            if (scene.getTracks() != null) {
                trackResponses = scene.getTracks().stream()
                        .filter(Objects::nonNull)
                        .map(this::mapToTrackResponse)
                        .collect(Collectors.toList());
            }
        } catch (org.hibernate.LazyInitializationException e) {
            log.warn("LazyInitializationException encountered while mapping tracks for Scene ID {}. Returning empty tracks list.", scene.getId());
        }

        return SceneResponse.builder()
                .id(scene.getId())
                .sceneNumber(scene.getSceneNumber())
                .mood(scene.getMood())
                .description(scene.getDescription())
                .tracks(trackResponses)
                .build();
    }

    private TrackResponse mapToTrackResponse(Track track) {
        if (track == null) return null;

        return TrackResponse.builder()
                .id(track.getId())
                .spotifyId(track.getSpotifyId())
                .name(track.getName())
                .artist(track.getArtist())
                .album(track.getAlbum())
                .imageUrl(track.getImageUrl())
                .previewUrl(track.getPreviewUrl())
                .spotifyUrl(track.getExternalUrl())
                .durationMs(track.getDurationMs())
                .position(track.getPosition())
                .build();
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (maxLength <= 3) return "...";
        if (text.length() <= maxLength) return text;

        int lastSpace = text.lastIndexOf(' ', maxLength - 3);
        if (lastSpace > 0 && lastSpace > maxLength / 2) {
            return text.substring(0, lastSpace) + "...";
        } else {
            return text.substring(0, maxLength - 3) + "...";
        }
    }
}

