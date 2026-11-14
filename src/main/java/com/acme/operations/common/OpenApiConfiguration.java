package com.acme.operations.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Spring uses this configuration to register the beans defined here.
@Configuration
public class OpenApiConfiguration {
    // Spring registers the returned object as a bean so other components can inject it.
    @Bean
    // Configure the API documentation shown in Swagger.
    OpenAPI openAPI() {
        // Set the title and description shown in Swagger UI.
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Business Operations API")
                                .version("1.0.0")
                                .description(
                                        "Catalog, inventory, sales orders, purchases, replenishment,"
                                            + " returns, billing, reports and audit. Monetary amounts are in EUR, excluding"
                                            + " taxes."))
                // Let Swagger UI send a JWT with authenticated requests.
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "bearerAuth",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")))
                // It indicates the security requirement in OpenAPI; the real protection is in Spring Security.
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
