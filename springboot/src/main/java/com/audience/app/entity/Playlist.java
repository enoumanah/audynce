package com.audience.app.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "playlists")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"users", "tracks"})
@EqualsAndHashCode(of = "id")
public class Playlist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "original_narrative", columnDefinition = "TEXT", nullable = false)
    private String originalNarrative;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private MoodType mood;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<Track> tracks = new ArrayList<>();

    @Column(name = "spotify_playlist_id")
    private String spotifyPlaylistId;

    @Column(name = "is_public")
    @Builder.Default
    private Boolean isPublic = false;

    @Column(name = "generation_time_ms")
    private Long generationTimeMs;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public enum MoodType {
        UPBEAT, MELANCHOLIC, ROMANTIC, ADVENTUROUS,
        PEACEFUL, ENERGETIC, NOSTALGIC, DREAMY,
        INTENSE, CHILL, BALANCED
    }

}
