package com.audience.app.dto.response;

import com.audience.app.entity.MoodType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrackResponse {
    private Long id;
    private String spotifyId;
    private String name;
    private String artist;
    private String album;
    private String imageUrl;
    private String previewUrl;
    private String spotifyUrl;
    private Integer durationMs;
    private Integer position;
    private Integer sceneNumber;
    private MoodType mood;
    private String sceneDescription;
}