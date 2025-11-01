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
public class DirectModeAnalysis {
    private MoodType mood;
    private List<String> extractedGenres;
    private List<String> keywords;
    private String theme;
}
