package com.audience.app.dto.spotify;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SpotifyRecommendationRequest {
    private List<String> seedGenres;
    private List<String> seedArtists;
    private MoodProfile moodProfile;
    private Integer limit;
    private String promptKeywords;
}