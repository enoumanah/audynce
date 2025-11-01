package com.audience.app.dto.ai;

import com.audience.app.entity.MoodType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SceneAnalysis {
    private Integer sceneNumber;
    private String description;
    private String searchQuery;
}