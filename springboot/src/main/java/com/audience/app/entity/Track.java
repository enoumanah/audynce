package com.audience.app.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString(exclude = "playlist")
@EqualsAndHashCode(of = "id")
@Builder
@Table(name = "tracks")
public class Track {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "spotify_id", nullable = false)
    private String spotifyId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String artist;

    @Column(nullable = false)
    private String album;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "preview_url")
    private String previewUrl;

    @Column(name = "external_url", nullable = false)
    private String externalUrl;

    @Column(name = "duration_ms")
    private Integer durationMs;

    @Column(name = "track_position", nullable = false)
    private Integer position;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scene_id", nullable = false)
    private Scene scene;

    @ManyToOne(fetch = FetchType.LAZY)
    private Playlist playlist;

}
