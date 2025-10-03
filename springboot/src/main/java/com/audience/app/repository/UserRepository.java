package com.audience.app.repository;

import com.audience.app.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {

    Optional<User> findByEmail(String email);

    Optional<User> findBySpotifyId(String spotifyId);

    boolean existsByEmail(String email);

}
