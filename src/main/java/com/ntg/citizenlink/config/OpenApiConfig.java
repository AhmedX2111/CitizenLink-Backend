package com.ntg.citizenlink.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 metadata for the generated spec / Swagger UI.
 *
 * Defines the JWT "Authorization: Bearer <token>" scheme so the UI shows an
 * Authorize button — copy the access token from POST /api/v1/auth/login and
 * paste it there to try authenticated endpoints. A global security requirement
 * applies to every operation; the public auth endpoints (login/refresh) are
 * explicitly exempted with @SecurityRequirements in AuthController.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI citizenLinkOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CitizenLink API")
                        .version("v1")
                        .description("""
                                Citizen complaint and case management REST API.

                                Authentication: POST /api/v1/auth/login returns an access
                                token; pass it as `Authorization: Bearer <token>`. The
                                refresh token is delivered as an HttpOnly cookie on login
                                and rotated by POST /api/v1/auth/refresh.""")
                )
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Access token from POST /api/v1/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}