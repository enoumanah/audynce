package com.audience.app.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"playlists", "accessToken", "refreshToken"})
@EqualsAndHashCode(of = "spotifyId")
public class User {

    @Id
    @Column(name = "spotify_id", nullable = false)
    private String spotifyId;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "profile_image_url")
    private String profileImageUrl;

    @JsonIgnore
    @Column(name = "access_token", nullable = false, length = 512)
    private String accessToken;

    @JsonIgnore
    @Column(name = "refresh_token", nullable = false, length = 512)
    private String refreshToken;

    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Playlist> playlists;

    @Column(name = "subscription_tier", nullable = false)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SubscriptionTier subscriptionTier = SubscriptionTier.FREE;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "update_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "login_at")
    private LocalDateTime loginAt;

    public enum SubscriptionTier{
        FREE, PREMIUM, ENTERPRISE
    }

}
