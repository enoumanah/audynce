package com.audience.app.dto.spotify;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MoodProfile {
    private Double targetValence;      // 0.0-1.0 (negativity to positivity)
    private Double targetEnergy;       // 0.0-1.0 (calm to energetic)
    private Double targetDanceability; // 0.0-1.0
    private Integer targetTempo;       // BPM
    private Double targetAcousticness; // 0.0-1.0
    private Double targetInstrumentalness; // 0.0-1.0
}

