package com.audience.app.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info().title("Audiance API").version("1.0"))
                .addSecurityItem(new SecurityRequirement().addList("spotifyOAuth"))
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes("spotifyOAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.OAUTH2)
                                        .flows(new OAuthFlows()
                                                .authorizationCode(
                                                        new OAuthFlow()
                                                                .authorizationUrl("https://accounts.spotify.com/authorize")
                                                                .tokenUrl("https://accounts.spotify.com/api/token")
                                                                .scopes((io.swagger.v3.oas.models.security.Scopes) Map.of(
                                                                        "user-read-email", "Read user email",
                                                                        "playlist-modify-private", "Modify private playlists"
                                                                ))
                                                )
                                        )
                        )
                );
    }
}
