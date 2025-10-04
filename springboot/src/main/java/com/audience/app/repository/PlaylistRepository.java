package com.audience.app.repository;

import com.audience.app.entity.Playlist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

    List<Playlist> findByUserSpotifyId(String spotifyId);

    List<Playlist> findByUserSpotifyIdOrderByCreatedAtDesc(String spotifyId);

    Optional<Playlist> findByIdAndUserSpotifyId(Long id, String spotifyId);

    List<Playlist> findByIsPublicTrue();

    List<Playlist> findByUserSpotifyIdAndCreatedAtAfterOrderByCreatedAtDesc(
            String spotifyId,
            LocalDateTime since
    );

    long countByUserSpotifyId(String spotifyId);

    @Query("SELECT p FROM Playlist p LEFT JOIN FETCH p.scenes WHERE p.id = :id")
    Optional<Playlist> findByIdWithScenes(@Param("id") Long id);
}