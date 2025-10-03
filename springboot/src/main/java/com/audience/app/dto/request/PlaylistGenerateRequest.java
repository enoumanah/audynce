package com.audience.app.dto.request;

import com.audience.app.entity.MoodType;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlaylistGenerateRequest {

    @NotBlank(message = "Story narrative cannot be blank")
    @Size(min = 50, max = 2000, message = "Story must between 50 and 2000 characters")
    private String narrative;

    @NotNull(message = "Mood is required")
    private MoodType mood;

    @Min(value = 5, message = "Minimum of 5 tracks")
    @Max(value = 20, message = "Maximum 20 track allowed")
    private Integer trackCount = 10;

    private Boolean createSpotifyPlaylist = false;

    private Boolean isPublic = false;

}
