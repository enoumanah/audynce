package com.audience.app.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SceneAnalysis {
    private Integer sceneNumber;
    private String description;
    private String searchQuery;
}