package com.audience.app.dto.spotify;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AudioFeatures {
    private String id;
    private double valence;     // 0.0 - 1.0 (Positiveness)
    private double energy;      // 0.0 - 1.0 (Intensity/Activity)
    private double danceability; // 0.0 - 1.0
    private double acousticness; // 0.0 - 1.0
    private double instrumentalness; // 0.0 - 1.0
    private float tempo;       // BPM
}