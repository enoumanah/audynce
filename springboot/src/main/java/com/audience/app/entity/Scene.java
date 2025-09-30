package com.audience.app.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "scenes")
@Getter @Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Scene {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "playlist_id", nullable = false)
    private Playlist playlist;

    @OneToMany(mappedBy = "scene", cascade = CascadeType.ALL)
    @Builder.Default
    private List<Track> tracks = new ArrayList<>();

    @Column(name = "scene_number")
    private Integer sceneNumber;

    @Column(name = "mood")
    @Enumerated(EnumType.STRING)
    private MoodType mood;

    @Column(columnDefinition = "TEXT")
    private String description;

}
