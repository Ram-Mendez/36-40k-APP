package com.acme.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class BusinessIntegrationTest extends AbstractIntegrationTest {
    private String bearer;

    @BeforeEach
    void authenticate() throws Exception {
        bearer = "Bearer " + token("admin");
        CARRIER_STATUS.set(200);
    }

    private JsonNode call(MockHttpServletRequestBuilder request, Object body, int expectedStatus)
            throws Exception {
        request.header("Authorization", bearer);
        if (body != null)
            request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
        var result = mvc.perform(request).andReturn().getResponse();
        assertThat(result.getStatus())
                .withFailMessage("HTTP %s: %s", result.getStatus(), result.getContentAsString())
                .isEqualTo(expectedStatus);
        return result.getContentAsString().isBlank()
                ? mapper.nullNode()
                : json(result.getContentAsString());
    }

    private long customer() throws Exception {
        return call(
                        post("/api/v1/customers"),
                        Map.of(
                                "name",
                                unique("Client"),
                                "taxId",
                                unique("B"),
                                "email",
                                "buyer@example.test",
                                "address",
                                "Calle Mayor 1, Madrid"),
                        201)
                .path("id")
                .asLong();
    }

    private long product(String price) throws Exception {
        return call(
                        post("/api/v1/products"),
                        Map.of(
                                "sku",
                                unique("SKU"),
                                "name",
                                unique("Product"),
                                "unitPrice",
                                new BigDecimal(price)),
                        201)
                .path("id")
                .asLong();
    }

    private void adjust(long product, long quantity) throws Exception {
        call(
                post("/api/v1/inventory/{id}/adjustments", product),
                Map.of("quantity", quantity, "reason", "Test stock receipt"),
                204);
    }

    private long order(long customer, long product, long quantity) throws Exception {
        long id =
                call(post("/api/v1/orders"), Map.of("customerId", customer), 201)
                        .path("id")
                        .asLong();
        call(
                put("/api/v1/orders/{id}/lines/{product}", id, product),
                Map.of("quantity", quantity),
                200);
        return id;
    }

    private long balance(long product, String column) {
        if (!List.of("on_hand", "reserved").contains(column)) throw new IllegalArgumentException();
        return jdbc.queryForObject(
                "SELECT " + column + " FROM inventory_balance WHERE product_id=?",
                Long.class,
                product);
    }

    private long count(String table, long order) {
        if (!List.of("stock_reservation", "stock_movement").contains(table))
            throw new IllegalArgumentException();
        return jdbc.queryForObject(
                "SELECT count(*) FROM " + table + " WHERE order_id=?", Long.class, order);
    }

    @Test
    void customerCanBeUpdatedAndSoftDeleted() throws Exception {
        long id = customer();
        JsonNode before = call(get("/api/v1/customers/{id}", id), null, 200);
        call(
                put("/api/v1/customers/{id}", id),
                Map.of(
                        "name",
                        "Updated company",
                        "taxId",
                        before.path("taxId").asText(),
                        "email",
                        "changed@example.test",
                        "address",
                        "Madrid",
                        "version",
                        before.path("version").asLong()),
                200);
        assertThat(call(get("/api/v1/customers/{id}", id), null, 200).path("name").asText())
                .isEqualTo("Updated company");
        call(delete("/api/v1/customers/{id}", id), null, 204);
        assertThat(jdbc.queryForObject("SELECT active FROM customer WHERE id=?", Boolean.class, id))
                .isFalse();
    }

    @Test
    void duplicateCustomerTaxIdIsConflict() throws Exception {
        long id = customer();
        String taxId =
                jdbc.queryForObject("SELECT tax_id FROM customer WHERE id=?", String.class, id);
        call(
                post("/api/v1/customers"),
                Map.of(
                        "name",
                        "Other",
                        "taxId",
                        taxId,
                        "email",
                        "other@example.test",
                        "address",
                        "Madrid"),
                409);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM customer WHERE tax_id=?", Long.class, taxId))
                .isEqualTo(1);
    }

    @Test
    void invalidCustomerDoesNotPersist() throws Exception {
        long before = jdbc.queryForObject("SELECT count(*) FROM customer", Long.class);
        call(
                post("/api/v1/customers"),
                Map.of("name", "", "taxId", "", "email", "bad", "address", ""),
                400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer", Long.class))
                .isEqualTo(before);
    }

    @Test
    void staleProductUpdateIsConflictAndPreservesNewPrice() throws Exception {
        long id = product("10.00");
        JsonNode current = call(get("/api/v1/products/{id}", id), null, 200);
        Map<String, Object> update =
                Map.of(
                        "sku",
                        current.path("sku").asText(),
                        "name",
                        "Changed",
                        "unitPrice",
                        20,
                        "version",
                        current.path("version").asLong());
        call(put("/api/v1/products/{id}", id), update, 200);
        call(put("/api/v1/products/{id}", id), update, 409);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT unit_price FROM product WHERE id=?", BigDecimal.class, id))
                .isEqualByComparingTo("20.00");
    }

    @Test
    void duplicateProductSkuIsConflict() throws Exception {
        long id = product("10.00");
        String sku = jdbc.queryForObject("SELECT sku FROM product WHERE id=?", String.class, id);
        call(
                post("/api/v1/products"),
                Map.of("sku", sku, "name", "Duplicate", "unitPrice", 12),
                409);
    }

    @Test
    void negativePriceIsValidationError() throws Exception {
        call(
                post("/api/v1/products"),
                Map.of("sku", unique("SKU"), "name", "Invalid", "unitPrice", -1),
                400);
    }

    @Test
    void productSearchCombinesPaginationAndActiveFilter() throws Exception {
        long id = product("11.25");
        String sku = jdbc.queryForObject("SELECT sku FROM product WHERE id=?", String.class, id);
        JsonNode page =
                call(
                        get("/api/v1/products")
                                .param("search", sku)
                                .param("active", "true")
                                .param("page", "0")
                                .param("size", "1")
                                .param("sort", "unitPrice")
                                .param("direction", "desc"),
                        null,
                        200);
        assertThat(page.path("totalElements").asLong()).isEqualTo(1);
        assertThat(page.path("content").get(0).path("id").asLong()).isEqualTo(id);
        call(delete("/api/v1/products/{id}", id), null, 204);
        assertThat(
                        call(
                                        get("/api/v1/products")
                                                .param("search", sku)
                                                .param("active", "true"),
                                        null,
                                        200)
                                .path("totalElements")
                                .asLong())
                .isZero();
    }

    @Test
    void invalidPaginationAndSortAreRejected() throws Exception {
        call(get("/api/v1/products").param("size", "101"), null, 400);
        call(get("/api/v1/products").param("sort", "name;DROP TABLE product"), null, 400);
    }

    @Test
    void orderTotalUsesDecimalSnapshotAndLineReplacement() throws Exception {
        long product = product("12.35");
        long id = order(customer(), product, 3);
        assertThat(call(get("/api/v1/orders/{id}", id), null, 200).path("total").decimalValue())
                .isEqualByComparingTo("37.05");
        jdbc.update("UPDATE product SET unit_price=99 WHERE id=?", product);
        assertThat(
                        call(get("/api/v1/orders/{id}", id), null, 200)
                                .path("lines")
                                .get(0)
                                .path("unitPrice")
                                .decimalValue())
                .isEqualByComparingTo("12.35");
        call(delete("/api/v1/orders/{id}/lines/{product}", id, product), null, 200);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT total FROM sales_order WHERE id=?", BigDecimal.class, id))
                .isEqualByComparingTo("0");
    }

    @Test
    void emptyOrderCannotBeConfirmed() throws Exception {
        long id =
                call(post("/api/v1/orders"), Map.of("customerId", customer()), 201)
                        .path("id")
                        .asLong();
        call(post("/api/v1/orders/{id}/confirm", id), null, 409);
        assertThat(count("stock_reservation", id)).isZero();
    }

    @Test
    void zeroLineQuantityDoesNotPersist() throws Exception {
        long product = product("10");
        long id = order(customer(), product, 2);
        call(put("/api/v1/orders/{id}/lines/{product}", id, product), Map.of("quantity", 0), 400);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT quantity FROM order_line WHERE order_id=?", Long.class, id))
                .isEqualTo(2);
    }

    @Test
    void confirmationReservesWithoutReducingPhysicalStock() throws Exception {
        long product = product("9.99");
        adjust(product, 10);
        long id = order(customer(), product, 4);
        assertThat(call(post("/api/v1/orders/{id}/confirm", id), null, 200).path("status").asText())
                .isEqualTo("CONFIRMED");
        assertThat(balance(product, "on_hand")).isEqualTo(10);
        assertThat(balance(product, "reserved")).isEqualTo(4);
        assertThat(count("stock_reservation", id)).isEqualTo(1);
        assertThat(count("stock_movement", id)).isEqualTo(1);
    }

    @Test
    void insufficientSecondLineRollsBackFirstReservationAndMovements() throws Exception {
        long first = product("10");
        long second = product("20");
        adjust(first, 10);
        adjust(second, 1);
        long id = order(customer(), first, 3);
        call(put("/api/v1/orders/{id}/lines/{product}", id, second), Map.of("quantity", 2), 200);
        call(post("/api/v1/orders/{id}/confirm", id), null, 409);
        assertThat(balance(first, "reserved")).isZero();
        assertThat(balance(second, "reserved")).isZero();
        assertThat(count("stock_reservation", id)).isZero();
        assertThat(count("stock_movement", id)).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM sales_order WHERE id=?", String.class, id))
                .isEqualTo("DRAFT");
    }

    @Test
    void cancellingConfirmedOrderReleasesEveryReservation() throws Exception {
        long product = product("10");
        adjust(product, 9);
        long id = order(customer(), product, 4);
        call(post("/api/v1/orders/{id}/confirm", id), null, 200);
        call(post("/api/v1/orders/{id}/cancel", id), null, 200);
        assertThat(balance(product, "on_hand")).isEqualTo(9);
        assertThat(balance(product, "reserved")).isZero();
        assertThat(count("stock_reservation", id)).isZero();
        assertThat(count("stock_movement", id)).isEqualTo(2);
    }

    @Test
    void completingOrderCallsCarrierAndConsumesStock() throws Exception {
        long product = product("10");
        adjust(product, 9);
        long id = order(customer(), product, 4);
        call(post("/api/v1/orders/{id}/confirm", id), null, 200);
        int calls = CARRIER_CALLS.get();
        JsonNode completed = call(post("/api/v1/orders/{id}/complete", id), null, 200);
        assertThat(completed.path("shippingReference").asText()).isEqualTo("TEST-TRACKING");
        assertThat(completed.path("shippingCost").decimalValue()).isEqualByComparingTo("12.50");
        assertThat(CARRIER_CALLS.get()).isGreaterThan(calls);
        assertThat(balance(product, "on_hand")).isEqualTo(5);
        assertThat(balance(product, "reserved")).isZero();
        assertThat(count("stock_reservation", id)).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM sales_order WHERE id=?", String.class, id))
                .isEqualTo("COMPLETED");
    }

    @Test
    void carrierFailurePreservesConfirmedOrderAndReservationForRetry() throws Exception {
        long product = product("10");
        adjust(product, 7);
        long id = order(customer(), product, 3);
        call(post("/api/v1/orders/{id}/confirm", id), null, 200);
        CARRIER_STATUS.set(503);
        try {
            call(post("/api/v1/orders/{id}/complete", id), null, 503);
        } finally {
            CARRIER_STATUS.set(200);
        }
        assertThat(balance(product, "on_hand")).isEqualTo(7);
        assertThat(balance(product, "reserved")).isEqualTo(3);
        assertThat(count("stock_reservation", id)).isEqualTo(1);
        assertThat(count("stock_movement", id)).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM sales_order WHERE id=?", String.class, id))
                .isEqualTo("CONFIRMED");
        call(post("/api/v1/orders/{id}/complete", id), null, 200);
        assertThat(balance(product, "on_hand")).isEqualTo(4);
    }

    @Test
    void confirmedOrderCannotBeEditedOrConfirmedTwice() throws Exception {
        long product = product("10");
        adjust(product, 7);
        long id = order(customer(), product, 3);
        call(post("/api/v1/orders/{id}/confirm", id), null, 200);
        call(put("/api/v1/orders/{id}/lines/{product}", id, product), Map.of("quantity", 4), 409);
        call(post("/api/v1/orders/{id}/confirm", id), null, 409);
        assertThat(balance(product, "reserved")).isEqualTo(3);
        assertThat(count("stock_movement", id)).isEqualTo(1);
    }

    @Test
    void completedOrderCannotBeCancelledOrCompletedTwice() throws Exception {
        long product = product("10");
        adjust(product, 7);
        long id = order(customer(), product, 3);
        call(post("/api/v1/orders/{id}/confirm", id), null, 200);
        call(post("/api/v1/orders/{id}/complete", id), null, 200);
        call(post("/api/v1/orders/{id}/cancel", id), null, 409);
        call(post("/api/v1/orders/{id}/complete", id), null, 409);
        assertThat(balance(product, "on_hand")).isEqualTo(4);
    }

    @Test
    void adjustmentCannotConsumeReservedUnits() throws Exception {
        long product = product("10");
        adjust(product, 7);
        long id = order(customer(), product, 5);
        call(post("/api/v1/orders/{id}/confirm", id), null, 200);
        call(
                post("/api/v1/inventory/{id}/adjustments", product),
                Map.of("quantity", -3, "reason", "Bad count"),
                409);
        assertThat(balance(product, "on_hand")).isEqualTo(7);
        assertThat(balance(product, "reserved")).isEqualTo(5);
    }

    @Test
    void zeroAdjustmentIsRejectedWithoutMovement() throws Exception {
        long product = product("10");
        call(
                post("/api/v1/inventory/{id}/adjustments", product),
                Map.of("quantity", 0, "reason", "Zero"),
                400);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM stock_movement WHERE product_id=?",
                                Long.class,
                                product))
                .isZero();
    }

    @Test
    void inventoryJoinReturnsCorrectAvailableBalance() throws Exception {
        long product = product("10");
        adjust(product, 7);
        long id = order(customer(), product, 5);
        call(post("/api/v1/orders/{id}/confirm", id), null, 200);
        JsonNode row =
                call(get("/api/v1/inventory").param("productId", Long.toString(product)), null, 200)
                        .path("content")
                        .get(0);
        assertThat(row.path("onHand").asLong()).isEqualTo(7);
        assertThat(row.path("reserved").asLong()).isEqualTo(5);
        assertThat(row.path("available").asLong()).isEqualTo(2);
    }

    @RepeatedTest(3)
    void competingOrdersCannotReserveSameUnits() throws Exception {
        long product = product("10");
        adjust(product, 5);
        long customer = customer();
        long first = order(customer, product, 4);
        long second = order(customer, product, 4);
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var responses =
                    executor.invokeAll(
                            List.of(
                                    () -> {
                                        barrier.await(10, TimeUnit.SECONDS);
                                        return mvc.perform(
                                                        post("/api/v1/orders/{id}/confirm", first)
                                                                .header("Authorization", bearer))
                                                .andReturn()
                                                .getResponse()
                                                .getStatus();
                                    },
                                    () -> {
                                        barrier.await(10, TimeUnit.SECONDS);
                                        return mvc.perform(
                                                        post("/api/v1/orders/{id}/confirm", second)
                                                                .header("Authorization", bearer))
                                                .andReturn()
                                                .getResponse()
                                                .getStatus();
                                    }));
            assertThat(List.of(responses.get(0).get(), responses.get(1).get()))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(balance(product, "on_hand")).isEqualTo(5);
        assertThat(balance(product, "reserved")).isEqualTo(4);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT sum(quantity) FROM stock_reservation WHERE product_id=?",
                                Long.class,
                                product))
                .isEqualTo(4);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM sales_order WHERE id IN (?,?) AND"
                                    + " status='CONFIRMED'",
                                Long.class,
                                first,
                                second))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM stock_movement WHERE product_id=? AND"
                                    + " kind='RESERVE'",
                                Long.class,
                                product))
                .isEqualTo(1);
    }

    @Test
    void salesReportAggregatesOnlyCompletedOrdersAndHonorsDateWindow() throws Exception {
        long customer = customer();
        long product = product("12.35");
        adjust(product, 10);
        long first = order(customer, product, 2);
        long second = order(customer, product, 3);
        order(customer, product, 1); // A draft must not inflate revenue.
        for (long id : List.of(first, second)) {
            call(post("/api/v1/orders/{id}/confirm", id), null, 200);
            call(post("/api/v1/orders/{id}/complete", id), null, 200);
            jdbc.update("UPDATE sales_order SET updated_at='2020-02-02T12:00:00Z' WHERE id=?", id);
        }
        JsonNode page =
                call(
                        get("/api/v1/reports/sales")
                                .param("from", "2020-02-02T00:00:00Z")
                                .param("to", "2020-02-03T00:00:00Z"),
                        null,
                        200);
        assertThat(page.path("totalElements").asLong()).isEqualTo(1);
        JsonNode row = page.path("content").get(0);
        assertThat(row.path("customerId").asLong()).isEqualTo(customer);
        assertThat(row.path("completedOrders").asLong()).isEqualTo(2);
        assertThat(row.path("netSales").decimalValue()).isEqualByComparingTo("61.75");
        assertThat(row.path("shippingCost").decimalValue()).isEqualByComparingTo("25.00");
        assertThat(
                        call(
                                        get("/api/v1/reports/sales")
                                                .param("to", "2020-02-02T12:00:00Z"),
                                        null,
                                        200)
                                .path("totalElements")
                                .asLong())
                .isZero();
    }

    @Test
    void lowStockReportIncludesMissingBalanceAndCountsConfirmedOrders() throws Exception {
        long product = product("10");
        adjust(product, 5);
        long id = order(customer(), product, 5);
        call(post("/api/v1/orders/{id}/confirm", id), null, 200);
        long missing = product("11");
        jdbc.update("DELETE FROM inventory_balance WHERE product_id=?", missing);
        JsonNode rows =
                call(
                                get("/api/v1/reports/low-stock")
                                        .param("threshold", "0")
                                        .param("size", "100"),
                                null,
                                200)
                        .path("content");
        JsonNode reservedRow = null;
        JsonNode missingRow = null;
        for (JsonNode row : rows) {
            if (row.path("productId").asLong() == product) reservedRow = row;
            if (row.path("productId").asLong() == missing) missingRow = row;
        }
        assertThat(reservedRow).isNotNull();
        assertThat(missingRow).isNotNull();
        assertThat(reservedRow.path("available").asLong()).isZero();
        assertThat(reservedRow.path("confirmedOrders").asLong()).isEqualTo(1);
        assertThat(missingRow.path("onHand").asLong()).isZero();
        assertThat(missingRow.path("confirmedOrders").asLong()).isZero();
    }

    @Test
    void orderFiltersAndPaginationReturnMatchingStatusAndCustomer() throws Exception {
        long customer = customer();
        long product = product("10");
        adjust(product, 10);
        long confirmed = order(customer, product, 1);
        order(customer, product, 2);
        call(post("/api/v1/orders/{id}/confirm", confirmed), null, 200);
        JsonNode page =
                call(
                        get("/api/v1/orders")
                                .param("customerId", Long.toString(customer))
                                .param("status", "CONFIRMED")
                                .param("size", "1")
                                .param("sort", "total,asc"),
                        null,
                        200);
        assertThat(page.path("totalElements").asLong()).isEqualTo(1);
        assertThat(page.path("content").get(0).path("id").asLong()).isEqualTo(confirmed);
    }

    @Test
    void inactiveCustomerCannotPlaceNewOrder() throws Exception {
        long customer = customer();
        call(delete("/api/v1/customers/{id}", customer), null, 204);
        call(post("/api/v1/orders"), Map.of("customerId", customer), 409);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM sales_order WHERE customer_id=?",
                                Long.class,
                                customer))
                .isZero();
    }

    @Test
    void successfulOrderOperationHasCorrelatedAuditAndFailedTransitionDoesNot() throws Exception {
        long product = product("10");
        adjust(product, 5);
        long id = order(customer(), product, 2);
        call(
                post("/api/v1/orders/{id}/confirm", id).header("X-Request-ID", "qa-confirm-123"),
                null,
                200);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT request_id FROM audit_event WHERE action='ORDER_CONFIRMED'"
                                    + " AND entity_id=?",
                                String.class,
                                Long.toString(id)))
                .isEqualTo("qa-confirm-123");
        call(post("/api/v1/orders/{id}/confirm", id), null, 409);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM audit_event WHERE action='ORDER_CONFIRMED'"
                                    + " AND entity_id=?",
                                Long.class,
                                Long.toString(id)))
                .isEqualTo(1);
    }

    @Test
    void cancellationDuringCarrierCallWinsWithoutPartialShipment() throws Exception {
        long product = product("10");
        adjust(product, 8);
        long id = order(customer(), product, 3);
        call(post("/api/v1/orders/{id}/confirm", id), null, 200);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        CARRIER_ENTERED.set(entered);
        CARRIER_RELEASE.set(release);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var completion =
                    executor.submit(
                            () ->
                                    mvc.perform(
                                                    post("/api/v1/orders/{id}/complete", id)
                                                            .header("Authorization", bearer))
                                            .andReturn()
                                            .getResponse()
                                            .getStatus());
            try {
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                call(post("/api/v1/orders/{id}/cancel", id), null, 200);
            } finally {
                release.countDown();
                CARRIER_ENTERED.set(null);
                CARRIER_RELEASE.set(null);
            }
            assertThat(completion.get(10, TimeUnit.SECONDS)).isEqualTo(409);
        } finally {
            release.countDown();
            CARRIER_ENTERED.set(null);
            CARRIER_RELEASE.set(null);
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM sales_order WHERE id=?", String.class, id))
                .isEqualTo("CANCELLED");
        assertThat(balance(product, "on_hand")).isEqualTo(8);
        assertThat(balance(product, "reserved")).isZero();
        assertThat(count("stock_reservation", id)).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM stock_movement WHERE order_id=? AND"
                                    + " kind='SHIP'",
                                Long.class,
                                id))
                .isZero();
        assertThat(count("stock_movement", id)).isEqualTo(2);
    }
}
