package com.audience.app.controller;

import com.audience.app.dto.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "${app.frontend.url}")
public class AuthController {

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, String>>> getAuthStatus() {
        return ResponseEntity.ok(ApiResponse.success(
                Map.of("status", "Authentication endpoints coming soon")
        ));
    }

    // OAuth callback and JWT token generation will be implemented next
}