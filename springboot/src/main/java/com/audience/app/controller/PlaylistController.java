package com.audience.app.controller;

import com.audience.app.dto.request.PlaylistGenerateRequest;
import com.audience.app.dto.response.ApiResponse;
import com.audience.app.dto.response.PlaylistResponse;
import com.audience.app.service.PlaylistService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/playlists")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "${app.frontend.url}")
public class PlaylistController {

    private final PlaylistService playlistService;

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<PlaylistResponse>> generatePlaylist(
            @Valid @RequestBody PlaylistGenerateRequest request,
            Authentication authentication) {

        String spotifyId = authentication.getName();
        log.info("Generating playlist for user: {}", spotifyId);

        try {
            PlaylistResponse playlist = playlistService.generatePlaylist(spotifyId, request);
            return ResponseEntity.ok(ApiResponse.success("Playlist generated successfully", playlist));
        } catch (Exception e) {
            log.error("Error generating playlist for user: {}", spotifyId, e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to generate playlist: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PlaylistResponse>> getPlaylist(
            @PathVariable Long id,
            Authentication authentication) {

        String spotifyId = authentication.getName();
        log.info("Fetching playlist {} for user: {}", id, spotifyId);

        try {
            PlaylistResponse playlist = playlistService.getPlaylist(id, spotifyId);
            return ResponseEntity.ok(ApiResponse.success(playlist));
        } catch (RuntimeException e) {
            log.error("Playlist not found: {}", id, e);
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Playlist not found"));
        }
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<PlaylistResponse>>> getUserPlaylists(
            Authentication authentication) {

        String spotifyId = authentication.getName();
        log.info("Fetching all playlists for user: {}", spotifyId);

        try {
            List<PlaylistResponse> playlists = playlistService.getUserPlaylists(spotifyId);
            return ResponseEntity.ok(ApiResponse.success(playlists));
        } catch (Exception e) {
            log.error("Error fetching playlists for user: {}", spotifyId, e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch playlists"));
        }
    }

    @GetMapping("/recent")
    public ResponseEntity<ApiResponse<List<PlaylistResponse>>> getRecentPlaylists(
            Authentication authentication) {

        String spotifyId = authentication.getName();
        log.info("Fetching recent playlists for user: {}", spotifyId);

        try {
            List<PlaylistResponse> playlists = playlistService.getRecentPlaylists(spotifyId);
            return ResponseEntity.ok(ApiResponse.success(playlists));
        } catch (Exception e) {
            log.error("Error fetching recent playlists for user: {}", spotifyId, e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to fetch recent playlists"));
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePlaylist(
            @PathVariable Long id,
            Authentication authentication) {

        String spotifyId = authentication.getName();
        log.info("Deleting playlist {} for user: {}", id, spotifyId);

        try {
            playlistService.deletePlaylist(id, spotifyId);
            return ResponseEntity.ok(ApiResponse.success("Playlist deleted successfully", null));
        } catch (RuntimeException e) {
            log.error("Playlist not found: {}", id, e);
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Playlist not found"));
        }
    }
}