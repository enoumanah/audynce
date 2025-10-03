package com.audience.app.dto.response;

import com.audience.app.entity.MoodType;
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
    private MoodType mood;
    private Integer sceneCount;
    private List<TrackResponse> tracks;
    private String spotifyPlaylistId;
    private String spotifyPlaylistUrl;
    private Boolean isPublic;
    private LocalDateTime createdAt;
    private Long generationTimeMs;
}