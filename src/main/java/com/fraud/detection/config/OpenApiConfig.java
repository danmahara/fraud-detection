package com.fraud.detection.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI fraudDetectionOpenAPI() {
        String schemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info().title("Fraud Detection API").version("v1"))
                // Apply the JWT scheme to all endpoints by default...
                .addSecurityItem(new SecurityRequirement().addList(schemeName))
                // ...and define what that scheme is: an HTTP Bearer (JWT) token.
                .components(new Components().addSecuritySchemes(schemeName,
                        new SecurityScheme()
                                .name(schemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}