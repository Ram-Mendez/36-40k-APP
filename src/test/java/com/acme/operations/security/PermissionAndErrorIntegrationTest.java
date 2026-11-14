package com.acme.operations.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.acme.operations.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

// Check HTTP permissions and responses with the integration application and PostgreSQL.
class PermissionAndErrorIntegrationTest extends AbstractIntegrationTest {
    @Test
    void readonlyCanOpenAnOrderFromTheList() throws Exception {
        // As in Swagger: log in and list the orders with the read-only token.
        String readonly = "Bearer " + token("readonly");
        var list = mvc.perform(get("/api/v1/orders").param("size", "1").header("Authorization", readonly))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").isNumber())
                .andReturn().getResponse();
        long orderId = json(list.getContentAsString()).get("content").get(0).get("id").asLong();

        // Open that order with the same token. The bug returned 403 in this GET.
        mvc.perform(get("/api/v1/orders/{id}", orderId).header("Authorization", readonly))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId));

        // Without token, the same request must continue to require authentication.
        mvc.perform(get("/api/v1/orders/{id}", orderId))
                .andExpect(status().isUnauthorized());
    }

    // An unsupported HTTP method must produce 405, not an internal error.
    @Test
    void unsupportedMethodReturns405InsteadOfServerError() throws Exception {
        // PUT on the customer collection route is not defined; it is called with a valid admin.
        mvc.perform(put("/api/v1/customers").header("Authorization", "Bearer " + token("admin")))
                // Check the HTTP status, error code and requestId.
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    // An unadmitted Content-Type must return 415.
    @Test
    void unsupportedContentTypeReturns415InsteadOfServerError() throws Exception {
        // Send flat text to a JSON-waiting route.
        mvc.perform(
                        post("/api/v1/customers")
                                .header("Authorization", "Bearer " + token("admin"))
                                .contentType(MediaType.TEXT_PLAIN)
                                .content("customer"))
                // Verifies both the state and the UNSUPPORTED_MEDIA_TYPE code.
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    // JSON incomplete and invalid numerical parameters must return 400 consistent.
    @Test
    void malformedJsonAndNumericParametersReturnConsistent400() throws Exception {
        // Reuse admin token to distinguish validation from authentication errors.
        String authorization = "Bearer " + token("admin");
        // Send an incomplete JSON body to the endpoint of users.
        mvc.perform(
                        post("/api/v1/users")
                                .header("Authorization", authorization)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        // Send text where page should be numerical and check the same invalid input code.
        mvc.perform(
                        get("/api/v1/users")
                                .param("page", "not-a-number")
                                .header("Authorization", authorization))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    // READONLY cannot change resources or consult audit, and should not alter the basis.
    @Test
    void readonlyCannotMutateAnyBusinessResourceOrReadAuditAndDatabaseIsUnchanged()
            throws Exception {
        // Prepare reading-only credentials and select an existing customer and product.
        String authorization = "Bearer " + token("readonly");
        Long customerId = jdbc.queryForObject("SELECT min(id) FROM customer", Long.class);
        Long productId = jdbc.queryForObject("SELECT min(id) FROM product", Long.class);
        // Save a picture of the tables to compare it after all the attempts.
        var before = businessSnapshot();
        // It builds example bodies for attempts to create or edit resources.
        String customer =
                "{\"name\":\"Denied"
                    + " Customer\",\"taxId\":\"DENIED123\",\"email\":\"test@example.com\",\"address\":\"Madrid\",\"version\":0}";
        String product =
                "{\"sku\":\"DENIED123\",\"name\":\"Denied"
                    + " Product\",\"unitPrice\":12.50,\"version\":0}";
        String user =
                "{\"username\":\"denieduser\",\"password\":\"DeniedPassword123!\",\"role\":\"ADMIN\"}";
        // Gather the operations to be refused to READONLY.
        List<MockHttpServletRequestBuilder> requests =
                List.of(
                        post("/api/v1/customers").content(customer),
                        put("/api/v1/customers/" + customerId).content(customer),
                        delete("/api/v1/customers/" + customerId),
                        post("/api/v1/products").content(product),
                        put("/api/v1/products/" + productId).content(product),
                        delete("/api/v1/products/" + productId),
                        post("/api/v1/inventory/" + productId + "/adjustments")
                                .content("{\"quantity\":7,\"reason\":\"Denied adjustment\"}"),
                        post("/api/v1/orders").content("{\"customerId\":" + customerId + "}"),
                        put("/api/v1/orders/1").content("{\"customerId\":" + customerId + "}"),
                        put("/api/v1/orders/1/lines/" + productId).content("{\"quantity\":1}"),
                        delete("/api/v1/orders/1/lines/" + productId),
                        post("/api/v1/orders/1/confirm"),
                        post("/api/v1/orders/1/cancel"),
                        post("/api/v1/orders/1/complete"),
                        post("/api/v1/users").content(user),
                        put("/api/v1/users/1").content("{\"role\":\"READONLY\",\"active\":false}"),
                        delete("/api/v1/users/1"),
                        get("/api/v1/audit"));
        // It runs each request and requires 403 ACCESS_DENIED.
        for (var request : requests) {
            mvc.perform(
                            request.header("Authorization", authorization)
                                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        // Compare the posterior status with the initial photo to detect unexpected scriptures.
        assertThat(businessSnapshot()).isEqualTo(before);
    }

    // Check that the user list combines filters and delivers page metadata.
    @Test
    void usersUseSharedPaginationContractAndCombineFilters() throws Exception {
        // Find the user readonly active with a page of size one.
        mvc.perform(
                        get("/api/v1/users")
                                .header("Authorization", "Bearer " + token("admin"))
                                .param("query", "readonly")
                                .param("active", "true")
                                .param("size", "1"))
                // Check the returned user, total results, total pages and size.
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("readonly"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1));
    }

    // It gets rows of business tables in stable order to be able to compare before and after.
    private List<?> businessSnapshot() {
        return List.of(
                jdbc.queryForList("SELECT * FROM customer ORDER BY id"),
                jdbc.queryForList("SELECT * FROM product ORDER BY id"),
                jdbc.queryForList("SELECT * FROM inventory_balance ORDER BY product_id"),
                jdbc.queryForList("SELECT * FROM sales_order ORDER BY id"),
                jdbc.queryForList("SELECT * FROM order_line ORDER BY id"),
                jdbc.queryForList("SELECT * FROM stock_movement ORDER BY id"),
                jdbc.queryForList("SELECT * FROM stock_reservation ORDER BY order_id,product_id"),
                jdbc.queryForList("SELECT * FROM app_user ORDER BY id"),
                jdbc.queryForList("SELECT * FROM audit_event ORDER BY id"));
    }
}
