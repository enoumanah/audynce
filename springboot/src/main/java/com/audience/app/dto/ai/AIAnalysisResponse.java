package com.audience.app.dto.ai;

import com.audience.app.entity.MoodType;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AIAnalysisResponse {
    private String analysisId;
    private AnalysisMode mode; // STORY or DIRECT
    private List<SceneAnalysis> scenes; // For story mode

    @JsonProperty("direct_analysis")
    private DirectModeAnalysis directAnalysis; // For direct mode

    public enum AnalysisMode {
        STORY, DIRECT
    }
}

