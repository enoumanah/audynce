package com.audience.app.security;

import com.audience.app.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
@Slf4j
public class OAuth2SuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;
    private final OAuth2AuthorizedClientService authorizedClientService;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {

        OAuth2AuthenticationToken oauthToken = (OAuth2AuthenticationToken) authentication;
        OAuth2User oAuth2User = oauthToken.getPrincipal();

        // Get Spotify user info
        String spotifyId = oAuth2User.getAttribute("id");
        String email = oAuth2User.getAttribute("email");
        String displayName = oAuth2User.getAttribute("display_name");

        // Get profile image (Spotify returns an array of images)
        String profileImageUrl = null;
        var images = (java.util.List<?>) oAuth2User.getAttribute("images");
        if (images != null && !images.isEmpty()) {
            var firstImage = (java.util.Map<?, ?>) images.get(0);
            profileImageUrl = (String) firstImage.get("url");
        }

        // Get OAuth2 tokens
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(),
                oauthToken.getName()
        );

        String accessToken = client.getAccessToken().getTokenValue();
        String refreshToken = client.getRefreshToken() != null
                ? client.getRefreshToken().getTokenValue()
                : "";

        Instant expiresAtInstant = client.getAccessToken().getExpiresAt();
        LocalDateTime tokenExpiresAt = expiresAtInstant != null
                ? LocalDateTime.ofInstant(expiresAtInstant, ZoneId.systemDefault())
                : LocalDateTime.now().plusHours(1);

        // Save or update user
        userService.createOrUpdateUser(
                spotifyId,
                email,
                displayName,
                profileImageUrl,
                accessToken,
                refreshToken,
                tokenExpiresAt
        );

        // Generate JWT
        String jwt = jwtTokenProvider.generateToken(spotifyId, email);

        // Redirect to frontend with JWT
        String redirectUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/auth/callback")
                .queryParam("token", jwt)
                .build()
                .toUriString();

        log.info("User authenticated successfully: {}", spotifyId);
        getRedirectStrategy().sendRedirect(request, response, redirectUrl);
    }
}