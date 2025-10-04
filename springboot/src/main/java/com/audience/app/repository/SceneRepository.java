package com.audience.app.repository;

import com.audience.app.entity.Scene;
import com.audience.app.entity.MoodType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SceneRepository extends JpaRepository<Scene, Long> {

    List<Scene> findByPlaylistIdOrderBySceneNumberAsc(Long playlistId);

    List<Scene> findByPlaylistId(Long playlistId);

    List<Scene> findByMood(MoodType mood);

    void deleteByPlaylistId(Long playlistId);

    long countByPlaylistId(Long playlistId);
}