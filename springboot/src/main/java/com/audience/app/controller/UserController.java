package com.audience.app.controller;

import com.audience.app.dto.request.UserUpdateRequest;
import com.audience.app.dto.response.ApiResponse;
import com.audience.app.dto.response.UserResponse;
import com.audience.app.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "${app.frontend.url}")
public class UserController {

    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails) {

        String spotifyId = userDetails.getUsername();
        log.info("Fetching profile for user: {}", spotifyId);

        try {
            UserResponse user = userService.getUserProfile(spotifyId);
            return ResponseEntity.ok(ApiResponse.success(user));
        } catch (RuntimeException e) {
            log.error("User not found: {}", spotifyId, e);
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("User not found"));
        }
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> updateCurrentUser(
            @Valid @RequestBody UserUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        String spotifyId = userDetails.getUsername();
        log.info("Updating profile for user: {}", spotifyId);

        try {
            UserResponse user = userService.updateUser(spotifyId, request);
            return ResponseEntity.ok(
                    ApiResponse.success("Profile updated successfully", user)
            );
        } catch (RuntimeException e) {
            log.error("Error updating user: {}", spotifyId, e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.error("Failed to update profile"));
        }
    }
}