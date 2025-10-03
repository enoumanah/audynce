package com.audience.app.dto.response;

import com.audience.app.entity.SubscriptionTier;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserResponse {
    private String spotifyId;
    private String displayName;
    private String email;
    private String profileImageUrl;
    private SubscriptionTier subscriptionTier;
    private Integer playlistsCount;
    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;
}
