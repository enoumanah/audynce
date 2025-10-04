package com.audience.app.repository;

import com.audience.app.entity.Track;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrackRepository extends JpaRepository<Track, Long> {

    List<Track> findBySceneIdOrderByPositionAsc(Long sceneId);

    List<Track> findBySceneId(Long sceneId);

    void deleteBySceneId(Long sceneId);

    @Query("SELECT t FROM Track t WHERE t.scene.playlist.id = :playlistId ORDER BY t.scene.sceneNumber, t.position")
    List<Track> findAllByPlaylistIdOrderBySceneAndPosition(Long playlistId);

    List<Track> findBySpotifyId(String spotifyId);

    long countBySceneId(Long sceneId);
}