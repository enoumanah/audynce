package com.audience.app.service;

import com.audience.app.dto.ai.AIAnalysisResponse;
import com.audience.app.dto.ai.DirectModeAnalysis;
import com.audience.app.dto.ai.SceneAnalysis;
import com.audience.app.entity.MoodType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
    public AIAnalysisResponse analyzePrompt(String prompt, List<String> selectedGenres) {
        log.info("Sending prompt to AI service. Length: {} chars", prompt.length());

        WebClient webClient = webClientBuilder
                .baseUrl(fastApiUrl)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        Map<String, Object> request = Map.of(
                "prompt", prompt,
                "selected_genres", selectedGenres != null ? selectedGenres : Collections.emptyList(),
                "story_threshold", storyModeThreshold
        );

        try {
            AIAnalysisResponse response = webClient.post()
                    .uri("/ai/analyze")
                    .bodyValue(request)
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

            // Return fallback response
            return createFallbackResponse(prompt, selectedGenres);
        }
    }

    /**
     * Fallback if AI service is unavailable
     */
    private AIAnalysisResponse createFallbackResponse(String prompt, List<String> selectedGenres) {
        log.warn("Using fallback AI response");

        int wordCount = prompt.split("\\s+").length;

        if (wordCount >= storyModeThreshold) {
            // Story mode fallback
            return AIAnalysisResponse.builder()
                    .analysisId("fallback-" + System.currentTimeMillis())
                    .mode(AIAnalysisResponse.AnalysisMode.STORY)
                    .scenes(List.of(
                            SceneAnalysis.builder()
                                    .sceneNumber(1)
                                    .description("Beginning")
                                    .mood(MoodType.BALANCED)
                                    .suggestedGenres(selectedGenres)
                                    .energyLevel("medium")
                                    .build(),
                            SceneAnalysis.builder()
                                    .sceneNumber(2)
                                    .description("Middle")
                                    .mood(MoodType.UPBEAT)
                                    .suggestedGenres(selectedGenres)
                                    .energyLevel("high")
                                    .build(),
                            SceneAnalysis.builder()
                                    .sceneNumber(3)
                                    .description("End")
                                    .mood(MoodType.PEACEFUL)
                                    .suggestedGenres(selectedGenres)
                                    .energyLevel("low")
                                    .build()
                    ))
                    .build();
        } else {
            // Direct mode fallback
            return AIAnalysisResponse.builder()
                    .analysisId("fallback-" + System.currentTimeMillis())
                    .mode(AIAnalysisResponse.AnalysisMode.DIRECT)
                    .directAnalysis(DirectModeAnalysis.builder()
                            .mood(MoodType.BALANCED)
                            .extractedGenres(selectedGenres)
                            .keywords(List.of("music", "playlist"))
                            .theme(prompt.substring(0, Math.min(50, prompt.length())))
                            .build())
                    .build();
        }
    }
}