package com.audience.app.repository;

import com.audience.app.entity.Track;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TrackRepository extends JpaRepository<Track, Long> {

    List<Track> findByPlaylistIdOrderByPositionAsc(Long playlistId);

    List<Track> findBySpotifyId(String spotifyId);

    void deleteByPlaylistId(Long playlistId);

}
