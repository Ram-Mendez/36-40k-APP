package com.acme.operations.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.operations.customer.CustomerController;
import com.acme.operations.customer.CustomerService;
import com.acme.operations.order.OrderCompletionService;
import com.acme.operations.order.OrderController;
import com.acme.operations.order.OrderService;
import com.acme.operations.product.ProductController;
import com.acme.operations.product.ProductService;
import com.acme.operations.security.UserController;
import com.acme.operations.security.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springdoc.core.configuration.SpringDocConfiguration;
import org.springdoc.core.properties.SpringDocConfigProperties;
import org.springdoc.webmvc.core.configuration.SpringDocWebMvcConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;

// Carga solo los controladores necesarios para generar los esquemas de esta prueba MVC.
@WebMvcTest({UserController.class, OrderController.class, CustomerController.class, ProductController.class})
// Incluye Springdoc en el contexto reducido para poder consultar /v3/api-docs.
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
// Prepara MockMvc sin filtros de seguridad: este caso verifica documentación, no permisos.
@AutoConfigureMockMvc(addFilters = false)
// Añade las dependencias de prueba que necesitan los controladores.
@Import(OpenApiSchemaTest.Dependencies.class)
// Mantiene la inyección de Spring sin registrar los demás listeners de pruebas.
@TestExecutionListeners(DependencyInjectionTestExecutionListener.class)
// Comprueba que las clases DTO con nombres similares no se mezclen en OpenAPI.
class OpenApiSchemaTest {
    // MockMvc consulta el endpoint de documentación sin arrancar un servidor HTTP real.
    @Autowired MockMvc mvc;
    // ObjectMapper permite recorrer el JSON de OpenAPI como nodos.
    @Autowired ObjectMapper mapper;

    // Define objetos de apoyo disponibles solo dentro de este contexto de pruebas.
    @TestConfiguration
    static class Dependencies {
        // El test solo genera OpenAPI; estos servicios no ejecutan operaciones ni acceden a la base.
        @Bean UserService users() { return new UserService(null, null, null); }
        @Bean OrderService orders() { return new OrderService(null, null, null); }
        @Bean OrderCompletionService completion() { return new OrderCompletionService(null, null); }
        @Bean CustomerService customers() { return new CustomerService(null, null); }
        @Bean ProductService products() { return new ProductService(null, null, null); }
    }

    // Comprueba cuerpos y respuestas de usuarios, pedidos, productos y clientes.
    @Test
    void documentationDistinguishesDtosWithTheSameSimpleName() throws Exception {
        // Solicita la documentación generada y exige HTTP 200 antes de leer su JSON.
        JsonNode api = mapper.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        // El cuerpo de creación de usuario debe contener credenciales y rol, no customerId.
        JsonNode user = requestSchema(api, "/api/v1/users");
        assertThat(user.has("username")).isTrue();
        assertThat(user.has("password")).isTrue();
        assertThat(user.has("role")).isTrue();
        assertThat(user.has("customerId")).isFalse();

        // El cuerpo de pedido sí debe contener customerId, no username.
        JsonNode order = requestSchema(api, "/api/v1/orders");
        assertThat(order.has("customerId")).isTrue();
        assertThat(order.has("username")).isFalse();

        // Los esquemas de producto y cliente deben conservar sus campos propios.
        JsonNode product = requestSchema(api, "/api/v1/products");
        JsonNode customer = requestSchema(api, "/api/v1/customers");
        assertThat(product.has("sku")).isTrue();
        assertThat(product.has("taxId")).isFalse();
        assertThat(customer.has("taxId")).isTrue();
        assertThat(customer.has("sku")).isFalse();

        // También comprueba el tipo de cada elemento en las respuestas paginadas.
        assertThat(responseContentSchema(api, "/api/v1/users").has("username")).isTrue();
        assertThat(responseContentSchema(api, "/api/v1/products").has("sku")).isTrue();
        assertThat(responseContentSchema(api, "/api/v1/customers").has("taxId")).isTrue();
    }

    // Busca el esquema del cuerpo POST de la ruta indicada.
    private JsonNode requestSchema(JsonNode api, String path) {
        JsonNode schema = api.path("paths").path(path).path("post")
                .path("requestBody").path("content").path("application/json").path("schema");
        // Sigue su referencia y devuelve las propiedades que el test quiere comparar.
        return resolve(api, schema).path("properties");
    }

    // Busca el esquema de los elementos de la página devuelta por un GET.
    private JsonNode responseContentSchema(JsonNode api, String path) {
        JsonNode schema = api.path("paths").path(path).path("get").path("responses").path("200")
                .path("content").path("*/*").path("schema");
        // La propiedad content es una lista; items apunta al DTO de cada elemento.
        JsonNode items = resolve(api, schema).path("properties").path("content").path("items");
        return resolve(api, items).path("properties");
    }

    // Sigue un $ref local hacia components.schemas.
    private JsonNode resolve(JsonNode api, JsonNode reference) {
        // Lee la referencia y comprueba que tenga el prefijo esperado antes de extraer el nombre.
        String ref = reference.path("$ref").asText();
        assertThat(ref).startsWith("#/components/schemas/");
        return api.path("components").path("schemas").path(ref.substring("#/components/schemas/".length()));
    }
}
