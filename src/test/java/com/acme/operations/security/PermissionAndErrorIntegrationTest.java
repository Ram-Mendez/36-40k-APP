package com.acme.operations.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.acme.operations.AbstractIntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;

class PermissionAndErrorIntegrationTest extends AbstractIntegrationTest {
    @Test
    void unsupportedMethodReturns405InsteadOfServerError() throws Exception {
        mvc.perform(put("/api/v1/customers").header("Authorization", "Bearer " + token("admin")))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.requestId").isNotEmpty());
    }

    @Test
    void unsupportedContentTypeReturns415InsteadOfServerError() throws Exception {
        mvc.perform(
                        post("/api/v1/customers")
                                .header("Authorization", "Bearer " + token("admin"))
                                .contentType(MediaType.TEXT_PLAIN)
                                .content("customer"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void malformedJsonAndNumericParametersReturnConsistent400() throws Exception {
        String authorization = "Bearer " + token("admin");
        mvc.perform(
                        post("/api/v1/users")
                                .header("Authorization", authorization)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(
                        get("/api/v1/users")
                                .param("page", "not-a-number")
                                .header("Authorization", authorization))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void readonlyCannotMutateAnyBusinessResourceOrReadAuditAndDatabaseIsUnchanged()
            throws Exception {
        String authorization = "Bearer " + token("readonly");
        Long customerId = jdbc.queryForObject("SELECT min(id) FROM customer", Long.class);
        Long productId = jdbc.queryForObject("SELECT min(id) FROM product", Long.class);
        var before = businessSnapshot();
        String customer =
                "{\"name\":\"Denied"
                    + " Customer\",\"taxId\":\"DENIED123\",\"email\":\"test@example.com\",\"address\":\"Madrid\",\"version\":0}";
        String product =
                "{\"sku\":\"DENIED123\",\"name\":\"Denied"
                    + " Product\",\"unitPrice\":12.50,\"version\":0}";
        String user =
                "{\"username\":\"denieduser\",\"password\":\"DeniedPassword123!\",\"role\":\"ADMIN\"}";
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
        for (var request : requests) {
            mvc.perform(
                            request.header("Authorization", authorization)
                                    .contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        }
        assertThat(businessSnapshot()).isEqualTo(before);
    }

    @Test
    void usersUseSharedPaginationContractAndCombineFilters() throws Exception {
        mvc.perform(
                        get("/api/v1/users")
                                .header("Authorization", "Bearer " + token("admin"))
                                .param("query", "readonly")
                                .param("active", "true")
                                .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("readonly"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1));
    }

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
