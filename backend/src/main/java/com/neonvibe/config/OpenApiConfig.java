package com.neonvibe.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger metadata.
 *
 * <p>La mayoría de endpoints bajo {@code /api/v1} requieren un JWT de acceso
 * ({@code Authorization: Bearer <token>}). Swagger UI se desactiva en el perfil
 * {@code prod} (ver {@code application-prod.yml}).</p>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI neonVibeOpenApi() {
        final String scheme = "bearer";
        return new OpenAPI()
                .info(new Info()
                        .title("NeonVibe API")
                        .description("Servidor de música personal self-hosted. "
                                + "Los endpoints bajo /api/v1 requieren un JWT de acceso "
                                + "(header Authorization: Bearer <token>), salvo /auth/** "
                                + "y /public/**.")
                        .version("v1"))
                .addSecurityItem(new SecurityRequirement().addList(scheme))
                .components(new Components().addSecuritySchemes(scheme,
                        new SecurityScheme()
                                .name(scheme)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
