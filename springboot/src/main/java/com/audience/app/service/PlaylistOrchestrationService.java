package com.audience.app.service;

import com.audience.app.dto.ai.AIAnalysisResponse;
import com.audience.app.dto.ai.DirectModeAnalysis;
import com.audience.app.dto.ai.SceneAnalysis;
import com.audience.app.dto.request.PlaylistGenerateRequest;
import com.audience.app.dto.response.PlaylistResponse;
import com.audience.app.dto.response.SceneResponse;
import com.audience.app.dto.response.TrackResponse;
// REMOVED: import com.audience.app.dto.spotify.MoodProfile;
// REMOVED: import com.audience.app.dto.spotify.SpotifyRecommendationRequest;
import com.audience.app.entity.*;
import com.audience.app.repository.PlaylistRepository;
// SceneRepository might not be needed directly if using Cascade correctly
// import com.audience.app.repository.SceneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils; // Import StringUtils
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
    // SceneRepository might not be needed if relying on cascade from Playlist
    // private final SceneRepository sceneRepository;
    private final UserService userService;
    private final AIServiceClient aiServiceClient;
    private final SpotifyService spotifyService;

    @Value("${app.playlist.default-tracks-per-scene:5}")
    private int defaultTracksPerScene;

    @Value("${app.playlist.max-total-tracks:50}")
    private int maxTotalTracks;

    // REMOVED: Stopwords are no longer needed, AI handles this
    // private static final List<String> STOPWORDS = List.of(...);


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
        // ** NOTE: This is no longer used for seeding, but we can keep it for future logic.
        // ** For now, we will comment out the call to simplify.
        /*
        List<String> topArtists = new ArrayList<>();
        if (Boolean.TRUE.equals(request.getUsePersonalization())) { // Check boolean value correctly
            topArtists = spotifyService.getUserTopArtists(user, 2); // Limit to 2 for less bias
            log.info("Using {} top artists for personalization", topArtists.size());
        } else {
            log.info("Personalization disabled by request.");
        }
        */


        // 4. Create playlist entity
        Playlist playlist = createPlaylistEntity(user, request, aiAnalysis);

        // 5. Generate scenes and tracks based on AI analysis mode
        if (aiAnalysis != null && aiAnalysis.getMode() == AIAnalysisResponse.AnalysisMode.STORY) { // Add null check for aiAnalysis
            generateStoryModePlaylist(playlist, aiAnalysis, request, user);
        } else if (aiAnalysis != null && aiAnalysis.getMode() == AIAnalysisResponse.AnalysisMode.DIRECT) { // Add null check for aiAnalysis
            generateDirectModePlaylist(playlist, aiAnalysis, request, user);
        } else {
            log.error("AI Analysis response was null or mode was not set. Cannot generate tracks.");
            // Create an empty scene or handle as appropriate
            Scene fallbackScene = Scene.builder()
                    .playlist(playlist) // Set relationship
                    .sceneNumber(1)
                    .mood(MoodType.BALANCED) // Mood can still be used for the DB, even if not for search
                    .description("Could not generate tracks due to AI analysis error.")
                    .tracks(new ArrayList<>()) // Initialize tracks list
                    .build();
            playlist.getScenes().add(fallbackScene);
        }

        // 6. Save playlist (includes scenes and tracks due to cascade)

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
            createSpotifyPlaylistForUser(savedPlaylist, user);
        } else {
            log.info("Spotify playlist creation not requested (create_spotify_playlist={})", request.getCreateSpotifyPlaylist());
        }


        // 8. Calculate generation time and save potentially updated Spotify ID
        long endTime = System.currentTimeMillis();
        savedPlaylist.setGenerationTimeMs(endTime - startTime);
        savedPlaylist = playlistRepository.save(savedPlaylist);

        log.info("Playlist generation complete. ID: {}, Scenes: {}, Total tracks: {}, Spotify ID: {}, Time: {}ms",
                savedPlaylist.getId(),
                savedPlaylist.getScenes() != null ? savedPlaylist.getScenes().size() : 0,
                savedPlaylist.getScenes() != null ? savedPlaylist.getScenes().stream()
                        .filter(Objects::nonNull)
                        .mapToInt(s -> s.getTracks() != null ? s.getTracks().size() : 0).sum()
                        : 0,
                savedPlaylist.getSpotifyPlaylistId() != null ? savedPlaylist.getSpotifyPlaylistId() : "N/A", // Log spotify ID
                savedPlaylist.getGenerationTimeMs());

        Playlist finalPlaylist = playlistRepository.findByIdWithScenes(savedPlaylist.getId())
                .orElse(savedPlaylist);

        return mapToPlaylistResponse(finalPlaylist);
    }

    /**
     * Generate playlist for STORY mode (multiple scenes)
     */
    private void generateStoryModePlaylist(Playlist playlist, AIAnalysisResponse aiAnalysis,
                                           PlaylistGenerateRequest request, User user) {

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
                    .sceneNumber(sceneAnalysis.getSceneNumber() != null ? sceneAnalysis.getSceneNumber() : playlist.getScenes().size() + 1)
                    .description(sceneAnalysis.getDescription() != null ? sceneAnalysis.getDescription() : "Scene")
                    .tracks(new ArrayList<>())
                    .build();

            List<Track> tracks = getTracksForScene(
                    scene,
                    sceneAnalysis,
                    tracksPerScene,
                    accessToken
            );

            scene.setTracks(tracks);
            playlist.getScenes().add(scene);
        }
    }

    /**
     * Generate playlist for DIRECT mode (single scene, simpler)
     */
    private void generateDirectModePlaylist(Playlist playlist, AIAnalysisResponse aiAnalysis,
                                            PlaylistGenerateRequest request, User user) {

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
                .sceneNumber(1)
                .description(directAnalysis.getTheme() != null ? directAnalysis.getTheme() : "Playlist")
                .tracks(new ArrayList<>())
                .build();

        List<Track> tracks = getTracksForDirectMode(
                scene,
                directAnalysis,
                totalTracks,
                user.getAccessToken()
        );

        scene.setTracks(tracks);
        playlist.getScenes().add(scene);
    }

    /**
     * Get tracks for a story scene using AI search query
     */
    private List<Track> getTracksForScene(Scene scene, SceneAnalysis sceneAnalysis,
                                          int limit, String accessToken) {

        String searchQuery = sceneAnalysis.getSearchQuery();
        if (!StringUtils.hasText(searchQuery)) {
            log.warn("AI returned empty search query for scene {}. Skipping track search.", scene.getSceneNumber());
            return Collections.emptyList();
        }

        // Add personalization if enabled
        // ** NOTE: This logic is simplified. A better way would be to get top artist IDs
        // ** and append " artist:\"Artist Name\"" to the query, but that requires
        // ** another API call. This is a simpler implementation.
        // if (Boolean.TRUE.equals(request.getUsePersonalization()) && !topArtists.isEmpty()) {
        //     searchQuery = searchQuery + " " + String.join(" ", topArtists);
        // }

        log.info("Calling Spotify search for scene {}: '{}'", scene.getSceneNumber(), searchQuery);

        List<Map<String, Object>> spotifyTracks = spotifyService.searchTracks(
                searchQuery,
                limit,
                accessToken
        );

        List<Track> tracks = mapAndAssociateTracks(spotifyTracks, scene);

        log.info("Generated {} tracks for scene {}",
                tracks.size(), scene.getSceneNumber());

        return tracks;
    }

    /**
     * Get tracks for direct mode using AI search query
     */
    private List<Track> getTracksForDirectMode(Scene scene, DirectModeAnalysis directAnalysis,
                                               int limit, String accessToken) {

        String searchQuery = directAnalysis.getSearchQuery();
        if (!StringUtils.hasText(searchQuery)) {
            log.warn("AI returned empty search query for direct mode. Skipping track search.");
            return Collections.emptyList();
        }

        log.info("Calling Spotify search for direct mode: '{}'", searchQuery);

        List<Map<String, Object>> spotifyTracks = spotifyService.searchTracks(
                searchQuery,
                limit,
                accessToken
        );

        List<Track> tracks = mapAndAssociateTracks(spotifyTracks, scene);

        log.info("Generated {} tracks for direct mode", tracks.size());

        return tracks;
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
                        track.setScene(scene);
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
            return;
        }

        boolean isPublicPlaylist = Boolean.TRUE.equals(playlist.getIsPublic());

        String spotifyPlaylistId = spotifyService.createSpotifyPlaylist(
                user,
                playlist.getTitle(),
                playlist.getDescription(),
                trackUris,
                isPublicPlaylist
        );

        if (spotifyPlaylistId != null) {
            playlist.setSpotifyPlaylistId(spotifyPlaylistId);
            log.info("Successfully requested Spotify playlist creation: {}. Spotify ID set on Playlist entity.", spotifyPlaylistId);
        } else {
            log.error("Spotify creation returned null ID—check SpotifyService logs for details for playlist ID {}", playlist.getId());
        }
    }


    /** Helper: Quick token validation */
    private boolean isValidAccessToken(String token) {
        if (!StringUtils.hasText(token)) {
            log.warn("isValidAccessToken called with null or empty token.");
            return false;
        }
        WebClient webClient = WebClient.builder().baseUrl(spotifyService.getSpotifyApiUrl()).build();
        try {
            webClient.get()
                    .uri("/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .onStatus(status -> status.isError(),
                            clientResponse ->
                                    clientResponse.bodyToMono(String.class)
                                            .switchIfEmpty(Mono.just("{ \"error\": \"Empty error body from Spotify /me\" }"))
                                            .flatMap(body -> {
                                                log.warn("Token validation failed via /me endpoint with status: {}. Body: {}", clientResponse.statusCode(), body);
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
        String description = generatePlaylistDescription(request.getPrompt(), aiAnalysis, request.getSelectedGenres());

        boolean isPublicRequest = Boolean.TRUE.equals(request.getIsPublic());

        return Playlist.builder()
                .title(title)
                .description(description)
                .originalNarrative(request.getPrompt())
                .aiAnalysisId(aiAnalysis != null ? aiAnalysis.getAnalysisId() : "fallback-or-error")
                .user(user)
                .isPublic(isPublicRequest)
                .selectedGenres(request.getSelectedGenres() != null ? new ArrayList<>(request.getSelectedGenres()) : new ArrayList<>())
                .scenes(new ArrayList<>())
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
            if (StringUtils.hasText(firstSceneDesc)) {
                return "Soundtrack: " + truncate(firstSceneDesc, 40);
            } else {
                return "Story Soundtrack: " + truncate(prompt, 30);
            }
        } else if (aiAnalysis != null && aiAnalysis.getDirectAnalysis() != null && StringUtils.hasText(aiAnalysis.getDirectAnalysis().getTheme())) {
            return "Audynce: " + aiAnalysis.getDirectAnalysis().getTheme();
        } else {
            return "Audynce: " + truncate(prompt, 50);
        }
    }


    /**
     * Generate playlist description
     */
    private String generatePlaylistDescription(String prompt, AIAnalysisResponse aiAnalysis, List<String> selectedGenres) {
        if (aiAnalysis != null && aiAnalysis.getMode() == AIAnalysisResponse.AnalysisMode.STORY && !CollectionUtils.isEmpty(aiAnalysis.getScenes())) {
            int sceneCount = (int) aiAnalysis.getScenes().stream().filter(Objects::nonNull).count();
            return String.format("A %d-scene musical journey based on your story. Generated by Audynce.", sceneCount);
        } else {
            String genresString = "";
            if (!CollectionUtils.isEmpty(selectedGenres)) {
                List<String> validUserGenres = selectedGenres.stream()
                        .filter(g -> g != null && SpotifyService.VALID_SPOTIFY_GENRES.contains(g.trim().toLowerCase()))
                        .collect(Collectors.toList());
                if (!CollectionUtils.isEmpty(validUserGenres)){
                    genresString = " Genres: " + String.join(", ", validUserGenres) + ".";
                }
            }
            return "Your personalized playlist created by Audynce based on: \"" + truncate(prompt, 80) + "\"." + genresString;
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