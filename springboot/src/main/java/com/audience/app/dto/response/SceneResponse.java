package com.audience.app.dto.response;

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
public class SceneResponse {
    private Long id;
    private Integer sceneNumber;
    private MoodType mood;
    private String description;
    private List<TrackResponse> tracks;
}