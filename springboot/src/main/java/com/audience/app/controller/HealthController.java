package com.audience.app.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
@Slf4j
public class HealthController {

    @Value("${spring.application.name}")
    private String appName;

    @Value("${app.available-genres:[]}")
    private List<String> availableGenres;

    @GetMapping
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("application", appName);
        health.put("timestamp", LocalDateTime.now());

        return ResponseEntity.ok(health);
    }
}