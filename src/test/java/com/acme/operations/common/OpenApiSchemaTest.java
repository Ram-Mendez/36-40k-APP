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

// Load only the drivers needed to generate the schemes of this MVC test.
@WebMvcTest({UserController.class, OrderController.class, CustomerController.class, ProductController.class})
// It includes Springdoc in the reduced context to be able to consult / v3 / api-docs.
@ImportAutoConfiguration({SpringDocConfiguration.class, SpringDocConfigProperties.class, SpringDocWebMvcConfiguration.class})
// Prepare MockMvc without safety filters: this case verifies documentation, not permissions.
@AutoConfigureMockMvc(addFilters = false)
// Add the test units that drivers need.
@Import(OpenApiSchemaTest.Dependencies.class)
// You keep your Spring injection unregistered with the other test listeners.
@TestExecutionListeners(DependencyInjectionTestExecutionListener.class)
// Check that DTO classes with similar names do not mix in OpenAPI.
class OpenApiSchemaTest {
    // MockMvc consults the documentation endpoint without starting a real HTTP server.
    @Autowired MockMvc mvc;
    // ObjectMapper allows you to walk the OpenAPI JSON as nodes.
    @Autowired ObjectMapper mapper;

    // Define support objects available only within this test context.
    @TestConfiguration
    static class Dependencies {
        // The test only generates OpenAPI; these services do not run operations or access the base.
        @Bean UserService users() { return new UserService(null, null, null); }
        @Bean OrderService orders() { return new OrderService(null, null, null); }
        @Bean OrderCompletionService completion() { return new OrderCompletionService(null, null); }
        @Bean CustomerService customers() { return new CustomerService(null, null); }
        @Bean ProductService products() { return new ProductService(null, null, null); }
    }

    // Check bodies and responses from users, orders, products and customers.
    @Test
    void documentationDistinguishesDtosWithTheSameSimpleName() throws Exception {
        // Request the generated documentation and require HTTP 200 before reading your JSON.
        JsonNode api = mapper.readTree(mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        // The user creation body must contain credentials and role, not customerId.
        var createOrder = api.path("paths").path("/api/v1/orders").path("post");
        assertThat(createOrder.path("tags").get(0).asText()).isEqualTo("Sales orders");
        assertThat(createOrder.path("summary").asText()).isEqualTo("Create a draft sales order");
        assertThat(requestSchema(api, "/api/v1/orders").path("customerId").path("description").asText())
                .isEqualTo("ID of the customer placing the order.");

        JsonNode user = requestSchema(api, "/api/v1/users");
        assertThat(user.has("username")).isTrue();
        assertThat(user.has("password")).isTrue();
        assertThat(user.has("role")).isTrue();
        assertThat(user.has("customerId")).isFalse();

        // The order request has a customerId field.
        JsonNode order = requestSchema(api, "/api/v1/orders");
        assertThat(order.has("customerId")).isTrue();
        assertThat(order.has("username")).isFalse();

        // Product and customer schemes must keep their own fields.
        JsonNode product = requestSchema(api, "/api/v1/products");
        JsonNode customer = requestSchema(api, "/api/v1/customers");
        assertThat(product.has("sku")).isTrue();
        assertThat(product.has("taxId")).isFalse();
        assertThat(customer.has("taxId")).isTrue();
        assertThat(customer.has("sku")).isFalse();

        // It also checks the type of each element in the paid responses.
        assertThat(responseContentSchema(api, "/api/v1/users").has("username")).isTrue();
        assertThat(responseContentSchema(api, "/api/v1/products").has("sku")).isTrue();
        assertThat(responseContentSchema(api, "/api/v1/customers").has("taxId")).isTrue();
    }

    // Look for the POST body scheme of the route indicated.
    private JsonNode requestSchema(JsonNode api, String path) {
        JsonNode schema = api.path("paths").path(path).path("post")
                .path("requestBody").path("content").path("application/json").path("schema");
        // Follow your reference and return the properties the test wants to compare.
        return resolve(api, schema).path("properties");
    }

    // Find the outline of the elements of the page returned by a GET.
    private JsonNode responseContentSchema(JsonNode api, String path) {
        JsonNode schema = api.path("paths").path(path).path("get").path("responses").path("200")
                .path("content").path("*/*").path("schema");
        // The content property is a list; items point to the DTO of each element.
        JsonNode items = resolve(api, schema).path("properties").path("content").path("items");
        return resolve(api, items).path("properties");
    }

    // Follow a local $ref to compartments.schem.
    private JsonNode resolve(JsonNode api, JsonNode reference) {
        // Read the reference and check that you have the prefix expected before you remove the name.
        String ref = reference.path("$ref").asText();
        assertThat(ref).startsWith("#/components/schemas/");
        return api.path("components").path("schemas").path(ref.substring("#/components/schemas/".length()));
    }
}
