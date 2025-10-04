package com.audience.app.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaylistResponse {
    private Long id;
    private String title;
    private String description;
    private String originalNarrative;
    private List<SceneResponse> scenes;
    private String spotifyPlaylistId;
    private String spotifyPlaylistUrl;
    private Boolean isPublic;
    private LocalDateTime createdAt;
    private Long generationTimeMs;
}