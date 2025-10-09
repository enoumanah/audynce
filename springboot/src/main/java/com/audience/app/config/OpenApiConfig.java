package com.audience.app.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        SecurityScheme oauth2Scheme = new SecurityScheme()
                .type(SecurityScheme.Type.OAUTH2)
                .flows(new OAuthFlows()
                        .authorizationCode(new OAuthFlow()
                                .authorizationUrl("https://accounts.spotify.com/authorize")
                                .tokenUrl("https://accounts.spotify.com/api/token")
                                .scopes(new Scopes()
                                        .addString("user-read-email", "Read user email")
                                        .addString("user-read-private", "Read user private profile")
                                        .addString("playlist-modify-public", "Create public playlists")
                                )
                        )
                );

        return new OpenAPI()
                .components(new Components().addSecuritySchemes("spotifyOAuth", oauth2Scheme))
                .addSecurityItem(new SecurityRequirement().addList("spotifyOAuth"))
                .info(new Info().title("Audynce API").version("1.0").description("..."));
    }

}
