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
import com.audience.app.repository.SceneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaylistOrchestrationService {

    private final PlaylistRepository playlistRepository;
    private final SceneRepository sceneRepository;
    private final UserService userService;
    private final AIServiceClient aiServiceClient;
    private final SpotifyService spotifyService;

    @Value("${app.playlist.default-tracks-per-scene:5}")
    private int defaultTracksPerScene;

    @Value("${app.playlist.max-total-tracks:50}")
    private int maxTotalTracks;

    /**
     * Main method: Generate complete playlist from user prompt
     */
    @Transactional
    public PlaylistResponse generatePlaylist(String spotifyId, PlaylistGenerateRequest request) {
        long startTime = System.currentTimeMillis();

        log.info("Starting playlist generation for user: {}", spotifyId);

        // 1. Get user
        User user = userService.findBySpotifyId(spotifyId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 2. Call AI service to analyze prompt
        AIAnalysisResponse aiAnalysis = aiServiceClient.analyzePrompt(
                request.getPrompt(),
                request.getSelectedGenres()
        );

        // 3. Get user's top artists for personalization (if enabled)
        List<String> topArtists = new ArrayList<>();
        if (request.getUsePersonalization()) {
            topArtists = spotifyService.getUserTopArtists(user, 2);
            log.info("Using {} top artists for personalization", topArtists.size());
        }

        // 4. Create playlist entity
        Playlist playlist = createPlaylistEntity(user, request, aiAnalysis);

        // 5. Generate scenes and tracks based on AI analysis mode
        if (aiAnalysis.getMode() == AIAnalysisResponse.AnalysisMode.STORY) {
            generateStoryModePlaylist(playlist, aiAnalysis, request, topArtists, user);
        } else {
            generateDirectModePlaylist(playlist, aiAnalysis, request, topArtists, user);
        }

        // 6. Save playlist
        Playlist savedPlaylist = playlistRepository.save(playlist);

        // 7. Create Spotify playlist if requested
        if (request.getCreateSpotifyPlaylist()) {
            createSpotifyPlaylistForUser(savedPlaylist, user);
        }

        // 8. Calculate generation time
        long endTime = System.currentTimeMillis();
        savedPlaylist.setGenerationTimeMs(endTime - startTime);
        savedPlaylist = playlistRepository.save(savedPlaylist);

        log.info("Playlist generation complete. ID: {}, Scenes: {}, Total tracks: {}, Time: {}ms",
                savedPlaylist.getId(),
                savedPlaylist.getScenes().size(),
                savedPlaylist.getScenes().stream().mapToInt(s -> s.getTracks().size()).sum(),
                savedPlaylist.getGenerationTimeMs());

        return mapToPlaylistResponse(savedPlaylist);
    }

    /**
     * Generate playlist for STORY mode (multiple scenes)
     */
    private void generateStoryModePlaylist(Playlist playlist, AIAnalysisResponse aiAnalysis,
                                           PlaylistGenerateRequest request, List<String> topArtists, User user) {

        log.info("Generating STORY mode playlist with {} scenes", aiAnalysis.getScenes().size());

        int tracksPerScene = request.getTracksPerScene() != null
                ? request.getTracksPerScene()
                : defaultTracksPerScene;

        String accessToken = user.getAccessToken();

        for (SceneAnalysis sceneAnalysis : aiAnalysis.getScenes()) {
            Scene scene = Scene.builder()
                    .playlist(playlist)
                    .sceneNumber(sceneAnalysis.getSceneNumber())
                    .mood(sceneAnalysis.getMood())
                    .description(sceneAnalysis.getDescription())
                    .build();

            // Get tracks for this scene
            List<Track> tracks = getTracksForScene(
                    scene,
                    sceneAnalysis,
                    request.getSelectedGenres(),
                    topArtists,
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
                                            PlaylistGenerateRequest request, List<String> topArtists, User user) {

        log.info("Generating DIRECT mode playlist");

        DirectModeAnalysis directAnalysis = aiAnalysis.getDirectAnalysis();

        int totalTracks = Math.min(
                request.getTracksPerScene() != null ? request.getTracksPerScene() * 2 : 10,
                maxTotalTracks
        );

        Scene scene = Scene.builder()
                .playlist(playlist)
                .sceneNumber(1)
                .mood(directAnalysis.getMood())
                .description(directAnalysis.getTheme())
                .build();

        // Get tracks
        List<Track> tracks = getTracksForDirectMode(
                scene,
                directAnalysis,
                request.getSelectedGenres(),
                topArtists,
                totalTracks,
                user.getAccessToken()
        );

        scene.setTracks(tracks);
        playlist.getScenes().add(scene);
    }

    /**
     * Get tracks for a story scene using mood profiles
     */
    private List<Track> getTracksForScene(Scene scene, SceneAnalysis sceneAnalysis,
                                          List<String> selectedGenres, List<String> topArtists,
                                          int limit, String accessToken) {

        // Get mood profile for this scene
        MoodProfile moodProfile = spotifyService.getMoodProfile(sceneAnalysis.getMood());

        // Determine genres (prefer AI suggestions, fallback to user selection)
        List<String> genres = sceneAnalysis.getSuggestedGenres() != null
                && !sceneAnalysis.getSuggestedGenres().isEmpty()
                ? sceneAnalysis.getSuggestedGenres()
                : selectedGenres;

        // Extract prompt keywords (using scene description as proxy for prompt context)
        String promptKeywords = extractKeywordsFromPrompt(sceneAnalysis.getDescription());

        // Build recommendation request (now used for Last.fm + search)
        SpotifyRecommendationRequest recommendationRequest = SpotifyRecommendationRequest.builder()
                .seedGenres(genres != null ? genres : List.of())
                .seedArtists(topArtists)
                .moodProfile(moodProfile)
                .limit(limit)
                .promptKeywords(promptKeywords)
                .build();

        // Get recommendations using new flow
        List<Map<String, Object>> spotifyTracks = spotifyService.getRecommendations(
                recommendationRequest,
                accessToken
        );

        // Convert to Track entities
        List<Track> tracks = new ArrayList<>();
        for (int i = 0; i < spotifyTracks.size(); i++) {
            Map<String, Object> spotifyTrack = spotifyTracks.get(i);
            Track track = mapSpotifyTrackToEntity(spotifyTrack, scene, i + 1);
            tracks.add(track);
        }

        log.info("Generated {} tracks for scene {} ({})",
                tracks.size(), scene.getSceneNumber(), scene.getMood());

        return tracks;
    }

    /**
     * Get tracks for direct mode
     */
    private List<Track> getTracksForDirectMode(Scene scene, DirectModeAnalysis directAnalysis,
                                               List<String> selectedGenres, List<String> topArtists,
                                               int limit, String accessToken) {

        MoodProfile moodProfile = spotifyService.getMoodProfile(directAnalysis.getMood());

        List<String> genres = directAnalysis.getExtractedGenres() != null
                && !directAnalysis.getExtractedGenres().isEmpty()
                ? directAnalysis.getExtractedGenres()
                : selectedGenres;

        // Extract prompt keywords from the original prompt or theme
        String promptKeywords = extractKeywordsFromPrompt(directAnalysis.getTheme());

        SpotifyRecommendationRequest recommendationRequest = SpotifyRecommendationRequest.builder()
                .seedGenres(genres)
                .seedArtists(topArtists)
                .moodProfile(moodProfile)
                .limit(limit)
                .promptKeywords(promptKeywords)
                .build();

        List<Map<String, Object>> spotifyTracks = spotifyService.getRecommendations(
                recommendationRequest,
                accessToken
        );

        List<Track> tracks = new ArrayList<>();
        for (int i = 0; i < spotifyTracks.size(); i++) {
            Map<String, Object> spotifyTrack = spotifyTracks.get(i);
            Track track = mapSpotifyTrackToEntity(spotifyTrack, scene, i + 1);
            tracks.add(track);
        }

        log.info("Generated {} tracks for direct mode", tracks.size());

        return tracks;
    }

    /**
     * New helper: Extract keywords from prompt or theme
     */
    private String extractKeywordsFromPrompt(String text) {
        // Simple: split, filter common words, join
        String[] words = text.toLowerCase().split("\\s+");
        List<String> keywords = Arrays.stream(words)
                .filter(w -> !w.matches("a|an|the|with|in|to|and|or|for|i|my")) // Stopwords
                .limit(5)
                .collect(Collectors.toList());
        return String.join(" ", keywords);
    }

    /**
     * Map Spotify API response to Track entity
     */
    private Track mapSpotifyTrackToEntity(Map<String, Object> spotifyTrack, Scene scene, int position) {
        List<Map<String, Object>> artists = (List<Map<String, Object>>) spotifyTrack.get("artists");
        Map<String, Object> album = (Map<String, Object>) spotifyTrack.get("album");
        List<Map<String, Object>> images = (List<Map<String, Object>>) album.get("images");
        Map<String, Object> externalUrls = (Map<String, Object>) spotifyTrack.get("external_urls");

        return Track.builder()
                .spotifyId((String) spotifyTrack.get("id"))
                .name((String) spotifyTrack.get("name"))
                .artist(artists.get(0).get("name").toString())
                .album((String) album.get("name"))
                .imageUrl(!images.isEmpty() ? (String) images.get(0).get("url") : null)
                .previewUrl((String) spotifyTrack.get("preview_url"))
                .externalUrl((String) externalUrls.get("spotify"))
                .durationMs((Integer) spotifyTrack.get("duration_ms"))
                .position(position)
                .scene(scene)
                .build();
    }

    /**
     * Create Spotify playlist for user
     */
    private void createSpotifyPlaylistForUser(Playlist playlist, User user) {
        try {
            // Collect all track URIs
            List<String> trackUris = playlist.getScenes().stream()
                    .flatMap(scene -> scene.getTracks().stream())
                    .map(track -> "spotify:track:" + track.getSpotifyId())
                    .collect(Collectors.toList());

            log.info("Attempting Spotify export: {} tracks for user {}", trackUris.size(), user.getSpotifyId());

            if (trackUris.isEmpty()) {
                log.warn("No tracks to add—skipping Spotify playlist creation");
                return;
            }

            // Validate token first (optional: call /me)
            if (!isValidAccessToken(user.getAccessToken())) {
                log.error("Invalid/expired access token—skipping Spotify export");
                return;
            }

            String spotifyPlaylistId = spotifyService.createSpotifyPlaylist(
                    user,
                    playlist.getTitle(),
                    playlist.getDescription(),
                    trackUris,
                    playlist.getIsPublic()  // Ensure this matches input true
            );

            if (spotifyPlaylistId != null) {
                playlist.setSpotifyPlaylistId(spotifyPlaylistId);
                log.info("Created Spotify playlist: {}", spotifyPlaylistId);
            } else {
                log.error("Spotify creation failed—check service logs");
            }

        } catch (Exception e) {
            log.error("Failed to create Spotify playlist", e);
        }
    }

    // New helper: Quick token validation
    private boolean isValidAccessToken(String token) {
        if (token == null || token.isEmpty()) return false;
        WebClient webClient = WebClient.builder().baseUrl("https://api.spotify.com/v1").build();
        try {
            webClient.get()
                    .uri("/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .block(Duration.ofSeconds(5));  // Timeout
            return true;
        } catch (Exception e) {
            log.warn("Access token invalid: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Create playlist entity from request
     */
    private Playlist createPlaylistEntity(User user, PlaylistGenerateRequest request,
                                          AIAnalysisResponse aiAnalysis) {
        String title = generatePlaylistTitle(request.getPrompt(), aiAnalysis);
        String description = generatePlaylistDescription(request.getPrompt(), aiAnalysis);

        return Playlist.builder()
                .title(title)
                .description(description)
                .originalNarrative(request.getPrompt())
                .aiAnalysisId(aiAnalysis.getAnalysisId())
                .user(user)
                .isPublic(request.getIsPublic())
                .build();
    }

    /**
     * Generate playlist title from prompt
     */
    private String generatePlaylistTitle(String prompt, AIAnalysisResponse aiAnalysis) {
        if (aiAnalysis.getMode() == AIAnalysisResponse.AnalysisMode.STORY) {
            return "Story Soundtrack: " + truncate(prompt, 30);
        } else {
            return truncate(prompt, 50);
        }
    }

    /**
     * Generate playlist description
     */
    private String generatePlaylistDescription(String prompt, AIAnalysisResponse aiAnalysis) {
        if (aiAnalysis.getMode() == AIAnalysisResponse.AnalysisMode.STORY) {
            return String.format("A %d-scene musical journey through your story",
                    aiAnalysis.getScenes().size());
        } else {
            return "Your personalized playlist created by Audiance";
        }
    }

    /**
     * Map Playlist entity to response DTO
     */
    public PlaylistResponse mapToPlaylistResponse(Playlist playlist) {
        List<SceneResponse> sceneResponses = playlist.getScenes().stream()
                .map(this::mapToSceneResponse)
                .collect(Collectors.toList());

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
        List<TrackResponse> trackResponses = scene.getTracks().stream()
                .map(this::mapToTrackResponse)
                .collect(Collectors.toList());

        return SceneResponse.builder()
                .id(scene.getId())
                .sceneNumber(scene.getSceneNumber())
                .mood(scene.getMood())
                .description(scene.getDescription())
                .tracks(trackResponses)
                .build();
    }

    private TrackResponse mapToTrackResponse(Track track) {
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
        return text.length() > maxLength
                ? text.substring(0, maxLength) + "..."
                : text;
    }
}