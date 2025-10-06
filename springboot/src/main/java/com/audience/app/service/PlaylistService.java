package com.audience.app.service;

import com.audience.app.dto.request.PlaylistGenerateRequest;
import com.audience.app.dto.response.PlaylistResponse;
import com.audience.app.entity.Playlist;
import com.audience.app.repository.PlaylistRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final PlaylistOrchestrationService orchestrationService;

    @Transactional
    public PlaylistResponse generatePlaylist(String spotifyId, PlaylistGenerateRequest request) {
        return orchestrationService.generatePlaylist(spotifyId, request);
    }

    @Transactional(readOnly = true)
    public PlaylistResponse getPlaylist(Long playlistId, String spotifyId) {
        Playlist playlist = playlistRepository.findByIdAndUserSpotifyId(playlistId, spotifyId)
                .orElseThrow(() -> new RuntimeException("Playlist not found"));

        return orchestrationService.mapToPlaylistResponse(playlist);
    }

    @Transactional(readOnly = true)
    public List<PlaylistResponse> getUserPlaylists(String spotifyId) {
        List<Playlist> playlists = playlistRepository.findByUserSpotifyIdOrderByCreatedAtDesc(spotifyId);
        return playlists.stream()
                .map(orchestrationService::mapToPlaylistResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<PlaylistResponse> getRecentPlaylists(String spotifyId) {
        LocalDateTime thirtyDaysAgo = LocalDateTime.now().minusDays(30);
        List<Playlist> playlists = playlistRepository
                .findByUserSpotifyIdAndCreatedAtAfterOrderByCreatedAtDesc(spotifyId, thirtyDaysAgo);

        return playlists.stream()
                .map(orchestrationService::mapToPlaylistResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public void deletePlaylist(Long playlistId, String spotifyId) {
        Playlist playlist = playlistRepository.findByIdAndUserSpotifyId(playlistId, spotifyId)
                .orElseThrow(() -> new RuntimeException("Playlist not found"));

        playlistRepository.delete(playlist);
        log.info("Deleted playlist: {}", playlistId);
    }
}