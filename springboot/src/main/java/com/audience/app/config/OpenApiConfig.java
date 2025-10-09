package com.audience.app.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        // Define Spotify OAuth scopes properly
        Scopes spotifyScopes = new Scopes()
                .addString("user-read-email", "Read user email")
                .addString("playlist-modify-private", "Modify private playlists")
                .addString("playlist-modify-public", "Modify public playlists")
                .addString("user-read-private", "Read private user data");

        // Define OAuth2 scheme with Spotify endpoints
        SecurityScheme spotifyScheme = new SecurityScheme()
                .type(SecurityScheme.Type.OAUTH2)
                .flows(new OAuthFlows()
                        .authorizationCode(new OAuthFlow()
                                .authorizationUrl("https://accounts.spotify.com/authorize")
                                .tokenUrl("https://accounts.spotify.com/api/token")
                                .scopes(spotifyScopes)
                        )
                );

        // Register the scheme and attach it globally
        return new OpenAPI()
                .info(new Info()
                        .title("Audiance API")
                        .version("1.0")
                        .description("Audiance backend API documentation with Spotify OAuth2 integration."))
                .addSecurityItem(new SecurityRequirement().addList("spotifyOAuth"))
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes("spotifyOAuth", spotifyScheme));
    }
}
