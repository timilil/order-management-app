package com.example.orders.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * API metadata for the generated OpenAPI spec. The {@code bearerAuth} scheme lets Swagger UI's "Authorize"
 * button send the session token returned by {@code POST /api/auth/login}.
 */
@Configuration
@OpenAPIDefinition(info = @Info(title = "Order Management API", version = "1.0"))
@SecurityScheme(name = OpenApiConfig.BEARER_AUTH, type = SecuritySchemeType.HTTP, scheme = "bearer")
class OpenApiConfig {

    static final String BEARER_AUTH = "bearerAuth";
}
