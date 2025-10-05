package com.audience.app.config;

import com.audience.app.dto.spotify.MoodProfile;
import com.audience.app.entity.MoodType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class MoodProfileConfig {

    @Bean
    public Map<MoodType, MoodProfile> moodProfiles() {
        Map<MoodType, MoodProfile> profiles = new HashMap<>();

        // UPBEAT: Happy, energetic
        profiles.put(MoodType.UPBEAT, MoodProfile.builder()
                .targetValence(0.8)
                .targetEnergy(0.8)
                .targetDanceability(0.7)
                .targetTempo(120)
                .targetAcousticness(0.2)
                .build());

        // MELANCHOLIC: Sad, reflective
        profiles.put(MoodType.MELANCHOLIC, MoodProfile.builder()
                .targetValence(0.2)
                .targetEnergy(0.3)
                .targetDanceability(0.3)
                .targetTempo(80)
                .targetAcousticness(0.6)
                .build());

        // ROMANTIC: Warm, tender
        profiles.put(MoodType.ROMANTIC, MoodProfile.builder()
                .targetValence(0.6)
                .targetEnergy(0.4)
                .targetDanceability(0.5)
                .targetTempo(90)
                .targetAcousticness(0.4)
                .build());

        // ADVENTUROUS: Bold, exciting
        profiles.put(MoodType.ADVENTUROUS, MoodProfile.builder()
                .targetValence(0.7)
                .targetEnergy(0.7)
                .targetDanceability(0.6)
                .targetTempo(130)
                .targetAcousticness(0.3)
                .build());

        // PEACEFUL: Calm, serene
        profiles.put(MoodType.PEACEFUL, MoodProfile.builder()
                .targetValence(0.5)
                .targetEnergy(0.2)
                .targetDanceability(0.3)
                .targetTempo(70)
                .targetAcousticness(0.8)
                .build());

        // ENERGETIC: High intensity
        profiles.put(MoodType.ENERGETIC, MoodProfile.builder()
                .targetValence(0.7)
                .targetEnergy(0.9)
                .targetDanceability(0.8)
                .targetTempo(140)
                .targetAcousticness(0.1)
                .build());

        // INTENSE: Dark, powerful
        profiles.put(MoodType.INTENSE, MoodProfile.builder()
                .targetValence(0.3)
                .targetEnergy(0.8)
                .targetDanceability(0.5)
                .targetTempo(140)
                .targetAcousticness(0.2)
                .build());

        // NOSTALGIC: Wistful, reminiscent
        profiles.put(MoodType.NOSTALGIC, MoodProfile.builder()
                .targetValence(0.5)
                .targetEnergy(0.4)
                .targetDanceability(0.4)
                .targetTempo(85)
                .targetAcousticness(0.5)
                .build());

        // DREAMY: Ethereal, floating
        profiles.put(MoodType.DREAMY, MoodProfile.builder()
                .targetValence(0.6)
                .targetEnergy(0.3)
                .targetDanceability(0.4)
                .targetTempo(75)
                .targetAcousticness(0.6)
                .build());

        // CHILL: Relaxed, laid-back
        profiles.put(MoodType.CHILL, MoodProfile.builder()
                .targetValence(0.6)
                .targetEnergy(0.4)
                .targetDanceability(0.5)
                .targetTempo(95)
                .targetAcousticness(0.4)
                .build());

        // BALANCED: Neutral, versatile
        profiles.put(MoodType.BALANCED, MoodProfile.builder()
                .targetValence(0.5)
                .targetEnergy(0.5)
                .targetDanceability(0.5)
                .targetTempo(110)
                .targetAcousticness(0.4)
                .build());

        return profiles;
    }
}