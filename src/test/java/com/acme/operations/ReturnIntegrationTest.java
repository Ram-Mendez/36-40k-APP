package com.acme.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class ReturnIntegrationTest extends AbstractIntegrationTest {
    private String bearer;

    @BeforeEach
    void authenticate() throws Exception {
        bearer = "Bearer " + token("admin");
        CARRIER_STATUS.set(200);
        CARRIER_ENTERED.set(null);
        CARRIER_RELEASE.set(null);
    }

    @Test
    void receivingOneSellableAndTwoDamagedUnitsAddsOnlyOneUnitToStock() throws Exception {
        Sale sale = saleWithOpeningStock(10, 3, "14.00");
        long productId = sale.products().getFirst();
        assertThat(balance(productId, "on_hand")).isEqualTo(7);

        JsonNode requested = request(sale.orderId(), List.of(line(productId, 3)));
        long returnId = requested.get("id").asLong();
        JsonNode approved = decision(returnId, "approve", requested.get("version").asLong(), 200);
        call(post("/api/v1/returns/{id}/receive", returnId),
                Map.of("requestId", UUID.randomUUID(), "version", approved.get("version").asLong(),
                        "lines", List.of(disposition(productId, 1, 2))), 200);

        JsonNode received = call(get("/api/v1/returns/{id}", returnId), null, 200);
        JsonNode inspectedLine = received.get("lines").get(0);
        assertThat(received.get("status").asText()).isEqualTo("RECEIVED");
        assertThat(received.get("creditAmount").decimalValue()).isEqualByComparingTo("42.00");
        assertThat(inspectedLine.get("restockQuantity").asLong()).isEqualTo(1);
        assertThat(inspectedLine.get("damagedQuantity").asLong()).isEqualTo(2);
        assertThat(jdbc.queryForList(
                "SELECT quantity FROM stock_movement WHERE product_id=? AND order_id=? AND kind='RETURN'",
                Long.class, productId, sale.orderId())).containsExactly(1L);

        JsonNode inventory = call(get("/api/v1/inventory")
                .param("productId", Long.toString(productId)), null, 200);
        JsonNode stock = inventory.get("content").get(0);
        assertThat(stock.get("onHand").asLong()).as("stock after return inspection").isEqualTo(8);
        assertThat(stock.get("available").asLong()).isEqualTo(8);
    }

    @Test
    void receivingUsesSoldPricesAndSeparatesRestockFromDamagedWithoutConsumingReservations()
            throws Exception {
        Sale sale = sale(3, "10.00", "7.50");
        long first = sale.products().getFirst();
        long second = sale.products().get(1);
        JsonNode product = call(get("/api/v1/products/{id}", first), null, 200);
        call(put("/api/v1/products/{id}", first),
                Map.of("sku", product.path("sku").asText(), "name", "New catalog price",
                        "unitPrice", 99, "version", product.path("version").asLong()), 200);
        long reservedOrder = draft(sale.customerId(), List.of(first), 2);
        call(post("/api/v1/orders/{id}/confirm", reservedOrder), null, 200);

        JsonNode requested = request(sale.orderId(), List.of(line(first, 3), line(second, 2)));
        assertThat(requested.path("creditAmount").decimalValue()).isEqualByComparingTo("45.00");
        assertThat(requested.path("lines").get(0).path("unitPrice").decimalValue())
                .isEqualByComparingTo("10.00");
        long id = requested.path("id").asLong();
        JsonNode approved = decision(id, "approve", 0, 200);
        JsonNode received = call(post("/api/v1/returns/{id}/receive", id),
                Map.of("requestId", UUID.randomUUID(), "version", approved.path("version").asLong(),
                        "lines", List.of(disposition(second, 0, 2), disposition(first, 2, 1))), 200);

        assertThat(received.path("status").asText()).isEqualTo("RECEIVED");
        assertThat(received.path("creditAmount").decimalValue()).isEqualByComparingTo("45.00");
        assertThat(balance(first, "on_hand")).isEqualTo(12);
        assertThat(balance(first, "reserved")).isEqualTo(2);
        assertThat(balance(second, "on_hand")).isEqualTo(10);
        assertThat(returnMovements(sale.orderId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT damaged_quantity FROM sales_return_line WHERE return_id=? AND product_id=?",
                Long.class, id, second)).isEqualTo(2);
    }

    @Test
    void cumulativeQuantitiesIncludePendingReturnsAndRejectionReleasesTheAllowance() throws Exception {
        Sale sale = sale(4, "10.00");
        long product = sale.products().getFirst();
        JsonNode initial = request(sale.orderId(), List.of(line(product, 3)));
        JsonNode excess = call(post("/api/v1/returns"), creation(sale.orderId(), UUID.randomUUID(),
                List.of(line(product, 2))), 409);
        assertThat(excess.path("code").asText()).isEqualTo("RETURN_QUANTITY_EXCEEDED");
        decision(initial.path("id").asLong(), "reject", 0, 200);
        JsonNode replacement = request(sale.orderId(), List.of(line(product, 4)));
        long id = replacement.path("id").asLong();
        decision(id, "approve", 0, 200);
        decision(id, "reject", 1, 409);
        call(post("/api/v1/returns"), creation(sale.orderId(), UUID.randomUUID(),
                List.of(line(product, 1))), 409);
        assertThat(jdbc.queryForObject("SELECT status FROM sales_return WHERE id=?", String.class, id))
                .isEqualTo("APPROVED");
    }

    @Test
    void requestAndReceiptReplayDoNotDuplicateStockOrAuditAndRejectChangedPayload() throws Exception {
        Sale sale = sale(3, "12.25");
        long product = sale.products().getFirst();
        UUID createId = UUID.randomUUID();
        var create = creation(sale.orderId(), createId, List.of(line(product, 2)));
        JsonNode requested = call(post("/api/v1/returns"), create, 201);
        long id = requested.path("id").asLong();
        assertThat(call(post("/api/v1/returns"), create, 201).path("id").asLong()).isEqualTo(id);
        assertThat(call(post("/api/v1/returns"),
                creation(sale.orderId(), createId, List.of(line(product, 1))), 409)
                .path("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        decision(id, "approve", 0, 200);
        UUID receiptId = UUID.randomUUID();
        var receive = Map.of("requestId", receiptId, "version", 1,
                "lines", List.of(disposition(product, 1, 1)));
        JsonNode first = call(post("/api/v1/returns/{id}/receive", id), receive, 200);
        JsonNode replay = call(post("/api/v1/returns/{id}/receive", id), receive, 200);
        assertThat(replay).isEqualTo(first);
        assertThat(call(post("/api/v1/returns/{id}/receive", id),
                Map.of("requestId", receiptId, "version", 1,
                        "lines", List.of(disposition(product, 2, 0))), 409)
                .path("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(balance(product, "on_hand")).isEqualTo(11);
        assertThat(returnMovements(sale.orderId())).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_event WHERE entity_type='RETURN' AND entity_id=?"
                        + " AND action='RETURN_RECEIVED'", Long.class, Long.toString(id))).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM sales_return WHERE order_id=?", Long.class, sale.orderId()))
                .isEqualTo(1);
    }

    @Test
    void invalidSecondDispositionPersistsNoPartialStockCreditOrReceipt() throws Exception {
        Sale sale = sale(2, "10.00", "20.00");
        long first = sale.products().getFirst();
        long second = sale.products().get(1);
        long id = request(sale.orderId(), List.of(line(first, 2), line(second, 2)))
                .path("id").asLong();
        decision(id, "approve", 0, 200);
        call(post("/api/v1/returns/{id}/receive", id),
                Map.of("requestId", UUID.randomUUID(), "version", 1,
                        "lines", List.of(disposition(first, 2, 0), disposition(second, 1, 0))), 400);
        assertThat(balance(first, "on_hand")).isEqualTo(10);
        assertThat(balance(second, "on_hand")).isEqualTo(10);
        assertThat(returnMovements(sale.orderId())).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM sales_return WHERE id=?", String.class, id))
                .isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM sales_return_line WHERE return_id=? AND restock_quantity IS NOT NULL",
                Long.class, id)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM audit_event WHERE entity_type='RETURN' AND entity_id=?"
                        + " AND action='RETURN_RECEIVED'", Long.class, Long.toString(id))).isZero();
    }

    @Test
    void stockOverflowOnSecondLineRollsBackFirstLineAndAllReceiptData() throws Exception {
        Sale sale = sale(1, "10.00", "20.00");
        long first = sale.products().getFirst();
        long second = sale.products().get(1);
        long id = request(sale.orderId(), List.of(line(first, 1), line(second, 1)))
                .path("id").asLong();
        decision(id, "approve", 0, 200);
        jdbc.update("UPDATE inventory_balance SET on_hand=? WHERE product_id=?", Long.MAX_VALUE, second);
        try {
            JsonNode response = call(post("/api/v1/returns/{id}/receive", id),
                    Map.of("requestId", UUID.randomUUID(), "version", 1,
                            "lines", List.of(disposition(first, 1, 0), disposition(second, 1, 0))), 409);
            assertThat(response.path("code").asText()).isEqualTo("STOCK_CAPACITY_EXCEEDED");
            assertThat(balance(first, "on_hand")).isEqualTo(10);
            assertThat(balance(second, "on_hand")).isEqualTo(Long.MAX_VALUE);
            assertThat(returnMovements(sale.orderId())).isZero();
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM sales_return_line WHERE return_id=? AND restock_quantity IS NOT NULL",
                    Long.class, id)).isZero();
            assertThat(jdbc.queryForObject("SELECT version FROM sales_return WHERE id=?", Long.class, id))
                    .isEqualTo(1);
        } finally {
            jdbc.update("UPDATE inventory_balance SET on_hand=10 WHERE product_id=?", second);
        }
    }

    @Test
    void rejectsIncompleteOrdersForeignProductsDuplicateLinesAndStaleReview() throws Exception {
        Sale sale = sale(2, "10.00");
        long product = sale.products().getFirst();
        long draft = draft(sale.customerId(), List.of(product), 1);
        assertThat(call(post("/api/v1/returns"), creation(draft, UUID.randomUUID(),
                List.of(line(product, 1))), 409).path("code").asText()).isEqualTo("ORDER_NOT_COMPLETED");
        long foreign = createProduct("5.00");
        assertThat(call(post("/api/v1/returns"), creation(sale.orderId(), UUID.randomUUID(),
                List.of(line(foreign, 1))), 409).path("code").asText()).isEqualTo("PRODUCT_NOT_IN_ORDER");
        call(post("/api/v1/returns"), creation(sale.orderId(), UUID.randomUUID(),
                List.of(line(product, 1), line(product, 1))), 400);
        long id = request(sale.orderId(), List.of(line(product, 1))).path("id").asLong();
        assertThat(decision(id, "approve", 8, 409).path("code").asText())
                .isEqualTo("CONCURRENT_MODIFICATION");
        assertThat(call(get("/api/v1/returns/{id}", id), null, 200).path("status").asText())
                .isEqualTo("REQUESTED");
    }

    @Test
    void concurrentRequestsCannotAllocateMoreThanTheSoldQuantity() throws Exception {
        Sale sale = sale(3, "10.00");
        long product = sale.products().getFirst();
        var start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var requests = new ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < 2; i++) {
                requests.add(executor.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return mvc.perform(post("/api/v1/returns")
                                    .header("Authorization", bearer)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(mapper.writeValueAsBytes(creation(sale.orderId(), UUID.randomUUID(),
                                            List.of(line(product, 2))))))
                            .andReturn().getResponse().getStatus();
                }));
            }
            assertThat(List.of(requests.getFirst().get(20, TimeUnit.SECONDS),
                    requests.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject(
                "SELECT sum(l.quantity) FROM sales_return_line l JOIN sales_return r ON r.id=l.return_id"
                        + " WHERE r.order_id=?", Long.class, sale.orderId())).isEqualTo(2);
    }

    @Test
    void concurrentIdenticalReceiptsRestockOnlyOnce() throws Exception {
        Sale sale = sale(2, "10.00");
        long product = sale.products().getFirst();
        long id = request(sale.orderId(), List.of(line(product, 2))).path("id").asLong();
        decision(id, "approve", 0, 200);
        byte[] body = mapper.writeValueAsBytes(Map.of("requestId", UUID.randomUUID(), "version", 1,
                "lines", List.of(disposition(product, 2, 0))));
        var start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return mvc.perform(post("/api/v1/returns/{id}/receive", id)
                                .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body))
                        .andReturn().getResponse().getStatus();
            });
            var two = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return mvc.perform(post("/api/v1/returns/{id}/receive", id)
                                .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body))
                        .andReturn().getResponse().getStatus();
            });
            assertThat(one.get(20, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(two.get(20, TimeUnit.SECONDS)).isEqualTo(200);
        }
        assertThat(balance(product, "on_hand")).isEqualTo(12);
        assertThat(returnMovements(sale.orderId())).isEqualTo(1);
    }

    @Test
    void listFiltersHaveStablePaginationAndReadonlyCannotWrite() throws Exception {
        Sale sale = sale(4, "10.00");
        long product = sale.products().getFirst();
        long first = request(sale.orderId(), List.of(line(product, 1))).path("id").asLong();
        long second = request(sale.orderId(), List.of(line(product, 1))).path("id").asLong();
        decision(first, "reject", 0, 200);
        JsonNode result = call(get("/api/v1/returns").param("orderId", Long.toString(sale.orderId()))
                .param("status", "REQUESTED").param("size", "1"), null, 200);
        assertThat(result.path("totalElements").asLong()).isEqualTo(1);
        assertThat(result.path("content").get(0).path("id").asLong()).isEqualTo(second);
        assertThat(call(get("/api/v1/returns").param("orderId", Long.toString(sale.orderId()))
                .param("status", "REQUESTED").param("size", "1").param("page", "1"), null, 200)
                .path("content")).isEmpty();
        call(get("/api/v1/returns").param("status", "COMPLETED"), null, 400);
        call(get("/api/v1/returns").param("size", "101"), null, 400);
        call(get("/api/v1/returns").param("sort", "reason;drop table sales_return"), null, 400);
        bearer = "Bearer " + token("readonly");
        call(get("/api/v1/returns/{id}", second), null, 200);
        decision(second, "approve", 0, 403);
        call(post("/api/v1/returns"), creation(sale.orderId(), UUID.randomUUID(),
                List.of(line(product, 1))), 403);
        assertThat(jdbc.queryForObject("SELECT status FROM sales_return WHERE id=?", String.class, second))
                .isEqualTo("REQUESTED");
    }

    private record Sale(long customerId, long orderId, List<Long> products) {}

    private Sale sale(long quantity, String... prices) throws Exception {
        return saleWithOpeningStock(quantity + 10, quantity, prices);
    }

    private Sale saleWithOpeningStock(long openingStock, long quantity, String... prices) throws Exception {
        long customer = call(post("/api/v1/customers"),
                Map.of("name", unique("Return client"), "taxId", unique("B"),
                        "email", "returns@example.test", "address", "Madrid"), 201).path("id").asLong();
        var products = new ArrayList<Long>();
        for (String price : prices) {
            long product = createProduct(price);
            products.add(product);
            call(post("/api/v1/inventory/{id}/adjustments", product),
                    Map.of("quantity", openingStock, "reason", "Return test opening stock"), 204);
        }
        long order = draft(customer, products, quantity);
        call(post("/api/v1/orders/{id}/confirm", order), null, 200);
        call(post("/api/v1/orders/{id}/complete", order), null, 200);
        return new Sale(customer, order, products);
    }

    private long createProduct(String price) throws Exception {
        return call(post("/api/v1/products"), Map.of("sku", unique("RMA"),
                "name", unique("Return product"), "unitPrice", new BigDecimal(price)), 201)
                .path("id").asLong();
    }

    private long draft(long customerId, List<Long> products, long quantity) throws Exception {
        long order = call(post("/api/v1/orders"), Map.of("customerId", customerId), 201)
                .path("id").asLong();
        for (long product : products)
            call(put("/api/v1/orders/{id}/lines/{productId}", order, product),
                    Map.of("quantity", quantity), 200);
        return order;
    }

    private JsonNode request(long orderId, List<Map<String, Long>> lines) throws Exception {
        return call(post("/api/v1/returns"), creation(orderId, UUID.randomUUID(), lines), 201);
    }

    private static Map<String, Object> creation(long orderId, UUID requestId, List<Map<String, Long>> lines) {
        return Map.of("orderId", orderId, "requestId", requestId, "reason", "Accepted return request", "lines", lines);
    }

    private static Map<String, Long> line(long productId, long quantity) {
        return Map.of("productId", productId, "quantity", quantity);
    }

    private static Map<String, Long> disposition(long productId, long restock, long damaged) {
        return Map.of("productId", productId, "restockQuantity", restock, "damagedQuantity", damaged);
    }

    private JsonNode decision(long id, String action, long version, int status) throws Exception {
        return call(post("/api/v1/returns/{id}/{action}", id, action),
                Map.of("version", version, "reason", "Goods reviewed"), status);
    }

    private long balance(long product, String column) {
        if (!List.of("on_hand", "reserved").contains(column)) throw new IllegalArgumentException();
        return jdbc.queryForObject("SELECT " + column + " FROM inventory_balance WHERE product_id=?", Long.class, product);
    }

    private long returnMovements(long order) {
        return jdbc.queryForObject("SELECT count(*) FROM stock_movement WHERE order_id=? AND kind='RETURN'", Long.class, order);
    }

    private JsonNode call(MockHttpServletRequestBuilder request, Object body, int expectedStatus) throws Exception {
        request.header("Authorization", bearer);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
        var result = mvc.perform(request).andReturn().getResponse();
        assertThat(result.getStatus()).withFailMessage("HTTP %s: %s", result.getStatus(), result.getContentAsString())
                .isEqualTo(expectedStatus);
        return result.getContentAsString().isBlank() ? mapper.nullNode() : json(result.getContentAsString());
    }
}
