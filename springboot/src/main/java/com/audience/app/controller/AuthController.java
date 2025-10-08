package com.audience.app.controller;

import com.audience.app.dto.response.ApiResponse;
import com.audience.app.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "${app.frontend.url}")
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAuthStatus(
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails != null) {
            return ResponseEntity.ok(ApiResponse.success(
                    Map.of(
                            "authenticated", true,
                            "spotifyId", userDetails.getUsername()
                    )
            ));
        }

        return ResponseEntity.ok(ApiResponse.success(
                Map.of("authenticated", false)
        ));
    }

    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<Map<String, Object>>> validateToken(
            @RequestHeader("Authorization") String bearerToken) {

        try {
            String token = bearerToken.substring(7); // Remove "Bearer "
            boolean isValid = jwtTokenProvider.validateToken(token);

            if (isValid) {
                String spotifyId = jwtTokenProvider.getSpotifyIdFromToken(token);
                return ResponseEntity.ok(ApiResponse.success(
                        Map.of(
                                "valid", true,
                                "spotifyId", spotifyId
                        )
                ));
            }
        } catch (Exception e) {
            log.error("Token validation error", e);
        }

        return ResponseEntity.ok(ApiResponse.success(
                Map.of("valid", false)
        ));
    }

    @GetMapping("/login")
    public ResponseEntity<ApiResponse<Map<String, String>>> getLoginUrl() {
        return ResponseEntity.ok(ApiResponse.success(
                Map.of(
                        "loginUrl", "/oauth2/authorization/spotify",
                        "message", "Redirect user to this URL to start OAuth flow"
                )
        ));
    }
}