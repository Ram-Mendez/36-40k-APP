package com.acme.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Acceptance contracts for the second November 17 batch, using real PostgreSQL and security. */
class CurrentBatchIntegrationTest extends AbstractIntegrationTest {
    private String admin;

    @BeforeEach
    void signIn() throws Exception {
        admin = token("admin");
        CARRIER_STATUS.set(200);
    }

    @Test
    void receiptRetryWithReorderedLinesKeepsTheSameReceiptAndStock() throws Exception {
        long supplier = call(post("/api/v1/suppliers"), Map.of(
                "name", unique("Supplier"), "taxId", unique("SUP"),
                "email", "supplier@example.test", "leadTimeDays", 1), admin, 201).path("id").asLong();
        long p = product("10.00"), q = product("20.00");
        long purchase = call(post("/api/v1/purchase-orders"), Map.of("supplierId", supplier), admin, 201).path("id").asLong();
        for (long id : List.of(p, q)) {
            call(put("/api/v1/purchase-orders/{id}/lines/{productId}", purchase, id),
                    Map.of("quantity", 10, "unitCost", 8), admin, 200);
        }
        call(post("/api/v1/purchase-orders/{id}/submit", purchase), null, admin, 200);
        String requestId = UUID.randomUUID().toString();
        var firstLine = Map.of("productId", p, "quantity", 2);
        var secondLine = Map.of("productId", q, "quantity", 3);
        var first = call(post("/api/v1/purchase-orders/{id}/receipts", purchase), Map.of(
                "requestId", requestId, "deliveryReference", "CURRENT-DELIVERY",
                "lines", List.of(firstLine, secondLine)), admin, 200);
        var reordered = call(post("/api/v1/purchase-orders/{id}/receipts", purchase), Map.of(
                "requestId", requestId, "deliveryReference", "CURRENT-DELIVERY",
                "lines", List.of(secondLine, firstLine)), admin, 200);
        assertThat(reordered).isEqualTo(first);
        var changed = call(post("/api/v1/purchase-orders/{id}/receipts", purchase), Map.of(
                "requestId", requestId, "deliveryReference", "CURRENT-DELIVERY",
                "lines", List.of(Map.of("productId", p, "quantity", 4), secondLine)), admin, 409);
        assertThat(changed.path("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(jdbc.queryForObject("SELECT on_hand FROM inventory_balance WHERE product_id=?", Long.class, p)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT on_hand FROM inventory_balance WHERE product_id=?", Long.class, q)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_receipt WHERE purchase_order_id=?", Long.class, purchase)).isEqualTo(1);
    }

    @Test
    void logoutOnlyRevokesItsOwnLoginFamily() throws Exception {
        String account = unique("device");
        call(post("/api/v1/users"), Map.of("username", account, "password", "DevPassword123!", "role", "OPERATIONS"), admin, 201);
        var a = login(account);
        var b = login(account);
        String otherAccount = token("readonly");
        call(get("/api/v1/products"), null, a.path("accessToken").asText(), 200);
        call(get("/api/v1/products"), null, b.path("accessToken").asText(), 200);
        call(post("/api/v1/auth/logout"), null, a.path("accessToken").asText(), 204);
        call(get("/api/v1/products"), null, a.path("accessToken").asText(), 401);
        call(get("/api/v1/products"), null, b.path("accessToken").asText(), 200);
        call(get("/api/v1/products"), null, otherAccount, 200);
        call(post("/api/v1/auth/refresh"), Map.of("refreshToken", a.path("refreshToken").asText()), null, 401);
        call(post("/api/v1/auth/refresh"), Map.of("refreshToken", b.path("refreshToken").asText()), null, 200);
    }

    @Test
    void salesReportCountsOrdersRatherThanLinesEvenWhenTotalsAreEqual() throws Exception {
        long customer = call(post("/api/v1/customers"), Map.of("name", unique("Buyer"), "taxId", unique("CUS"),
                "email", "buyer@example.test", "address", "Madrid"), admin, 201).path("id").asLong();
        long p = product("10.00"), q = product("20.00");
        for (long id : List.of(p, q)) {
            call(post("/api/v1/inventory/{id}/adjustments", id), Map.of("quantity", 20, "reason", "Current batch"), admin, 204);
        }
        for (int index = 0; index < 2; index++) {
            long order = call(post("/api/v1/orders"), Map.of("customerId", customer), admin, 201).path("id").asLong();
            call(put("/api/v1/orders/{id}/lines/{productId}", order, p), Map.of("quantity", index == 0 ? 1 : 3), admin, 200);
            if (index == 0) call(put("/api/v1/orders/{id}/lines/{productId}", order, q), Map.of("quantity", 1), admin, 200);
            call(post("/api/v1/orders/{id}/confirm", order), null, admin, 200);
            call(post("/api/v1/orders/{id}/complete", order), null, admin, 200);
        }
        var report = call(get("/api/v1/reports/sales").param("size", "100"), null, admin, 200);
        JsonNode row = null;
        for (JsonNode item : report.path("content")) if (item.path("customerId").asLong() == customer) row = item;
        assertThat(row).isNotNull();
        assertThat(row.path("completedOrders").asLong()).isEqualTo(2);
        assertThat(row.path("netSales").decimalValue()).isEqualByComparingTo("60.00");
        assertThat(row.path("shippingCost").decimalValue()).isEqualByComparingTo("25.00");
    }

    private long product(String price) throws Exception {
        return call(post("/api/v1/products"), Map.of("sku", unique("SKU"), "name", "Current batch product", "unitPrice", price), admin, 201).path("id").asLong();
    }

    private JsonNode login(String username) throws Exception {
        return call(post("/api/v1/auth/login"), Map.of("username", username, "password", "DevPassword123!"), null, 200);
    }

    private JsonNode call(MockHttpServletRequestBuilder request, Object body, String token, int expected) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).withFailMessage("HTTP %s: %s", response.getStatus(), response.getContentAsString()).isEqualTo(expected);
        return response.getContentAsString().isBlank() ? mapper.nullNode() : json(response.getContentAsString());
    }
}
