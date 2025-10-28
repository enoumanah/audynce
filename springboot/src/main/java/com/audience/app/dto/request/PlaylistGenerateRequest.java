package com.audience.app.dto.request;

import com.audience.app.entity.MoodType;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlaylistGenerateRequest {

    @NotBlank(message = "Prompt cannot be blank")
    @Size(min = 10, max = 2000, message = "Prompt must be between 10 and 2000 characters")
    private String prompt;

    @Size(min = 1, max = 3, message = "Select 1-3 genres")
    @JsonProperty("selectedGenres")
    private List<String> selectedGenres;

    @JsonProperty("overallMood")
    @JsonAlias("overall_mood")
    private MoodType overallMood;


    @Min(value = 5, message = "Minimum 5 tracks per scene")
    @Max(value = 10, message = "Maximum 10 tracks per scene")
    @JsonProperty("tracks_per_scene")
    private Integer tracksPerScene = 5;

    @JsonProperty("create_spotify_playlist")
    private Boolean createSpotifyPlaylist = false;

    @JsonProperty("is_public")
    private Boolean isPublic = false;

    private Boolean usePersonalization = true;

}