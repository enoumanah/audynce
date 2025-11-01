package com.audience.app.service;

import com.audience.app.dto.ai.AIAnalysisResponse;
import com.audience.app.dto.ai.DirectModeAnalysis;
import com.audience.app.dto.ai.SceneAnalysis;
// import com.audience.app.entity.MoodType; // No longer needed
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils; // Import CollectionUtils
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AIServiceClient {

    private final WebClient.Builder webClientBuilder;

    @Value("${app.fastapi.url}")
    private String fastApiUrl;

    @Value("${app.fastapi.timeout:30000}")
    private int timeout;

    @Value("${app.ai.story-mode-threshold:100}")
    private int storyModeThreshold;

    /**
     * Analyze user prompt - determines if story or direct mode
     */
    // MODIFIED signature to accept topArtists
    public AIAnalysisResponse analyzePrompt(String prompt, List<String> selectedGenres, List<String> topArtists) {
        log.info("Sending prompt to AI service. Length: {} chars, Artists: {}", prompt.length(), topArtists);

        WebClient webClient = webClientBuilder
                .baseUrl(fastApiUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        // MODIFIED request map to include top_artists
        Map<String, Object> request = Map.of(
                "prompt", prompt,
                "selected_genres", selectedGenres != null ? selectedGenres : Collections.emptyList(),
                "story_threshold", storyModeThreshold,
                "top_artists", topArtists != null ? topArtists : Collections.emptyList() // ADDED this line
        );

        try {
            AIAnalysisResponse response = webClient.post()
                    .uri("/ai/analyze")
                    .bodyValue(request) // Send the map with top_artists
                    .retrieve()
                    .bodyToMono(AIAnalysisResponse.class)
                    .timeout(Duration.ofMillis(timeout))
                    .block();

            if (response != null) {
                log.info("AI analysis complete. Mode: {}, Scenes: {}",
                        response.getMode(),
                        response.getScenes() != null ? response.getScenes().size() : 0);
            }

            return response;

        } catch (Exception e) {
            log.error("Error calling AI service", e);
            // Return fallback response, now passing topArtists
            return createFallbackResponse(prompt, selectedGenres, topArtists);
        }
    }

    /**
     * Fallback if AI service is unavailable
     * UPDATED to use search_query and topArtists
     */
    // MODIFIED signature
    private AIAnalysisResponse createFallbackResponse(String prompt, List<String> selectedGenres, List<String> topArtists) {
        log.warn("Using fallback AI response");

        int wordCount = prompt.split("\\s+").length;

        String genreStr = selectedGenres != null ?
                selectedGenres.stream().collect(Collectors.joining(" ")) : "pop";
        if (genreStr.isBlank()) {
            genreStr = "pop";
        }

        // NEW: Add fallback artist personalization
        String artistStr = "";
        if (!CollectionUtils.isEmpty(topArtists)) {
            // Get the first artist and ensure no quotes in the name
            String fallbackArtist = topArtists.get(0).replace("\"", "");
            artistStr = " artist:\"" + fallbackArtist + "\"";
        }


        if (wordCount >= storyModeThreshold) {
            // Story mode fallback
            return AIAnalysisResponse.builder()
                    .analysisId("fallback-" + System.currentTimeMillis())
                    .mode(AIAnalysisResponse.AnalysisMode.STORY)
                    .scenes(List.of(
                            SceneAnalysis.builder()
                                    .sceneNumber(1)
                                    .description("Beginning")
                                    .searchQuery("peaceful " + genreStr + artistStr) // Add artistStr
                                    .build(),
                            SceneAnalysis.builder()
                                    .sceneNumber(2)
                                    .description("Middle")
                                    .searchQuery("upbeat " + genreStr + artistStr) // Add artistStr
                                    .build(),
                            SceneAnalysis.builder()
                                    .sceneNumber(3)
                                    .description("End")
                                    .searchQuery("epic " + genreStr + artistStr) // Add artistStr
                                    .build()
                    ))
                    .build();
        } else {
            // Direct mode fallback
            return AIAnalysisResponse.builder()
                    .analysisId("fallback-" + System.currentTimeMillis())
                    .mode(AIAnalysisResponse.AnalysisMode.DIRECT)
                    .directAnalysis(DirectModeAnalysis.builder()
                            .theme(prompt.substring(0, Math.min(50, prompt.length())))
                            .searchQuery(prompt + " " + genreStr + artistStr) // Add artistStr
                            .build())
                    .build();
        }
    }
}