package com.acme.operations.common;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Spring procesa esta configuración para registrar los objetos definidos con @Bean.
@Configuration
public class OpenApiConfiguration {
    // Spring invoca este método para registrar su resultado y poder inyectarlo en otros componentes.
    @Bean
    // Describe la API para Swagger; esta documentación no aplica permisos de ejecución.
    OpenAPI openAPI() {
        // Construye los metadatos visibles en la documentación interactiva.
        return new OpenAPI()
                .info(
                        new Info()
                                .title("Business Operations API")
                                .version("1.0.0")
                                .description(
                                        "B2B distribution: catalog, inventory, orders, reservations"
                                            + " and audit. Monetary amounts in EUR, excluding"
                                            + " taxes."))
                // Declara el esquema Bearer para poder enviar un JWT desde Swagger UI.
                .components(
                        new Components()
                                .addSecuritySchemes(
                                        "bearerAuth",
                                        new SecurityScheme()
                                                .type(SecurityScheme.Type.HTTP)
                                                .scheme("bearer")
                                                .bearerFormat("JWT")))
                // Indica el requisito de seguridad en OpenAPI; la protección real está en Spring Security.
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
