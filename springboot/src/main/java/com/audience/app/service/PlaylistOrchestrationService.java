package com.audience.app.service;

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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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
    private final SpotifyService spotifyService;

    @Value("${app.playlist.default-tracks-per-scene:5}")
    private int defaultTracksPerScene;

    @Value("${app.playlist.max-total-tracks:50}")
    private int maxTotalTracks;

    /**
     * Main method: Generate complete playlist from user prompt
     * MOCK VERSION - No AI integration yet
     */
    @Transactional
    public PlaylistResponse generatePlaylist(String spotifyId, PlaylistGenerateRequest request) {
        long startTime = System.currentTimeMillis();

        log.info("Starting playlist generation for user: {}", spotifyId);

        // 1. Get user
        User user = userService.findBySpotifyId(spotifyId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // 2. Get user's top artists for personalization (if enabled)
        List<String> topArtists = new ArrayList<>();
        if (request.getUsePersonalization()) {
            topArtists = spotifyService.getUserTopArtists(user, 2);
            log.info("Using {} top artists for personalization", topArtists.size());
        }

        // 3. Create playlist entity
        Playlist playlist = createMockPlaylist(user, request);

        // 4. Generate scenes and tracks (MOCK - simple logic)
        generateMockScenes(playlist, request, topArtists, user);

        // 5. Save playlist
        Playlist savedPlaylist = playlistRepository.save(playlist);

        // 6. Create Spotify playlist if requested
        if (request.getCreateSpotifyPlaylist()) {
            createSpotifyPlaylistForUser(savedPlaylist, user);
        }

        // 7. Calculate generation time
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
     * MOCK: Create playlist entity without AI
     */
    private Playlist createMockPlaylist(User user, PlaylistGenerateRequest request) {
        String title = "Playlist: " + truncate(request.getPrompt(), 40);
        String description = "Generated from: " + truncate(request.getPrompt(), 80);

        return Playlist.builder()
                .title(title)
                .description(description)
                .originalNarrative(request.getPrompt())
                .aiAnalysisId("mock-" + System.currentTimeMillis())
                .user(user)
                .isPublic(request.getIsPublic())
                .build();
    }

    /**
     * MOCK: Generate scenes based on simple logic
     */
    private void generateMockScenes(Playlist playlist, PlaylistGenerateRequest request,
                                    List<String> topArtists, User user) {

        int tracksPerScene = request.getTracksPerScene() != null
                ? request.getTracksPerScene()
                : defaultTracksPerScene;

        // Determine number of scenes based on prompt length
        int wordCount = request.getPrompt().split("\\s+").length;
        int sceneCount = wordCount > 100 ? 3 : 1; // Story mode vs Direct mode

        // Create scenes
        for (int i = 1; i <= sceneCount; i++) {
            MoodType sceneMood = determineSceneMood(i, sceneCount, request.getOverallMood());

            Scene scene = Scene.builder()
                    .playlist(playlist)
                    .sceneNumber(i)
                    .mood(sceneMood)
                    .description(String.format("Scene %d - %s mood", i, sceneMood.toString().toLowerCase()))
                    .build();

            // Get tracks for this scene
            List<Track> tracks = getTracksForScene(
                    scene,
                    sceneMood,
                    request.getSelectedGenres(),
                    topArtists,
                    tracksPerScene,
                    user.getAccessToken()
            );

            scene.setTracks(tracks);
            playlist.getScenes().add(scene);
        }
    }

    /**
     * MOCK: Simple mood determination logic
     */
    private MoodType determineSceneMood(int sceneNumber, int totalScenes, MoodType requestedMood) {
        if (totalScenes == 1) {
            return requestedMood != null ? requestedMood : MoodType.BALANCED;
        }

        // For multi-scene: vary the mood
        switch (sceneNumber) {
            case 1: return MoodType.PEACEFUL;
            case 2: return requestedMood != null ? requestedMood : MoodType.UPBEAT;
            case 3: return MoodType.NOSTALGIC;
            default: return MoodType.BALANCED;
        }
    }

    /**
     * Get tracks for a scene using Spotify recommendations
     */
    private List<Track> getTracksForScene(Scene scene, MoodType mood,
                                          List<String> selectedGenres, List<String> topArtists,
                                          int limit, String accessToken) {

        // Get mood profile
        MoodProfile moodProfile = spotifyService.getMoodProfile(mood);

        // Build recommendation request
        SpotifyRecommendationRequest recommendationRequest = SpotifyRecommendationRequest.builder()
                .seedGenres(selectedGenres != null && !selectedGenres.isEmpty()
                        ? selectedGenres.stream().limit(3).collect(Collectors.toList())
                        : List.of("pop", "indie"))
                .seedArtists(topArtists.stream().limit(2).collect(Collectors.toList()))
                .moodProfile(moodProfile)
                .limit(limit)
                .build();

        // Get recommendations from Spotify
        List<Map<String, Object>> spotifyTracks = spotifyService.getRecommendations(
                recommendationRequest,
                accessToken
        );

        // If no recommendations, try fallback search
        if (spotifyTracks.isEmpty()) {
            log.warn("No recommendations found, using fallback search");
            String searchQuery = mood.toString().toLowerCase() + " " +
                    (selectedGenres != null && !selectedGenres.isEmpty() ? selectedGenres.get(0) : "music");
            spotifyTracks = spotifyService.searchTracks(searchQuery, limit, accessToken);
        }

        // Convert to Track entities
        List<Track> tracks = new ArrayList<>();
        for (int i = 0; i < spotifyTracks.size() && i < limit; i++) {
            Map<String, Object> spotifyTrack = spotifyTracks.get(i);
            Track track = mapSpotifyTrackToEntity(spotifyTrack, scene, i + 1);
            tracks.add(track);
        }

        log.info("Generated {} tracks for scene {} ({})",
                tracks.size(), scene.getSceneNumber(), scene.getMood());

        return tracks;
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

            if (trackUris.isEmpty()) {
                log.warn("No tracks to add to Spotify playlist");
                return;
            }

            String spotifyPlaylistId = spotifyService.createSpotifyPlaylist(
                    user,
                    playlist.getTitle(),
                    playlist.getDescription(),
                    trackUris,
                    playlist.getIsPublic()
            );

            if (spotifyPlaylistId != null) {
                playlist.setSpotifyPlaylistId(spotifyPlaylistId);
                log.info("Created Spotify playlist: {}", spotifyPlaylistId);
            }

        } catch (Exception e) {
            log.error("Failed to create Spotify playlist", e);
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