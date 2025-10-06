package com.audience.app.service;

import com.audience.app.dto.request.UserUpdateRequest;
import com.audience.app.dto.response.UserResponse;
import com.audience.app.entity.User;
import com.audience.app.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Optional<User> findBySpotifyId(String spotifyId) {
        return userRepository.findBySpotifyId(spotifyId);
    }

    @Transactional(readOnly = true)
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Transactional
    public User createOrUpdateUser(String spotifyId, String email, String displayName,
                                   String profileImageUrl, String accessToken,
                                   String refreshToken, LocalDateTime tokenExpiresAt) {

        Optional<User> existingUser = userRepository.findBySpotifyId(spotifyId);

        if (existingUser.isPresent()) {
            User user = existingUser.get();
            user.setEmail(email);
            user.setDisplayName(displayName);
            user.setProfileImageUrl(profileImageUrl);
            user.setAccessToken(accessToken);
            user.setRefreshToken(refreshToken);
            user.setTokenExpiresAt(tokenExpiresAt);
            user.setLastLoginAt(LocalDateTime.now());

            log.info("Updated existing user: {}", spotifyId);
            return userRepository.save(user);
        } else {
            User newUser = User.builder()
                    .spotifyId(spotifyId)
                    .email(email)
                    .displayName(displayName)
                    .profileImageUrl(profileImageUrl)
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenExpiresAt(tokenExpiresAt)
                    .lastLoginAt(LocalDateTime.now())
                    .build();

            log.info("Created new user: {}", spotifyId);
            return userRepository.save(newUser);
        }
    }

    @Transactional
    public UserResponse updateUser(String spotifyId, UserUpdateRequest request) {
        User user = userRepository.findBySpotifyId(spotifyId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setDisplayName(request.getDisplayName());
        if (request.getProfileImageUrl() != null) {
            user.setProfileImageUrl(request.getProfileImageUrl());
        }

        User savedUser = userRepository.save(user);
        return mapToUserResponse(savedUser);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserProfile(String spotifyId) {
        User user = userRepository.findBySpotifyId(spotifyId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return mapToUserResponse(user);
    }
    
    @Transactional
    public void cacheTopArtists(String spotifyId, String topArtistsCache) {
        User user = userRepository.findBySpotifyId(spotifyId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        user.setTopArtistsCache(topArtistsCache);
        user.setTopArtistsCacheUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        log.info("Cached top artists for user: {}", spotifyId);
    }

    private UserResponse mapToUserResponse(User user) {
        long playlistCount = user.getPlaylists() != null ? user.getPlaylists().size() : 0;

        return UserResponse.builder()
                .spotifyId(user.getSpotifyId())
                .displayName(user.getDisplayName())
                .email(user.getEmail())
                .profileImageUrl(user.getProfileImageUrl())
                .subscriptionTier(user.getSubscriptionTier())
                .playlistsCount((int) playlistCount)
                .lastLoginAt(user.getLastLoginAt())
                .createdAt(user.getCreatedAt())
                .build();
    }
}