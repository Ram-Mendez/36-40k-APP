package com.acme.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class ProcurementIntegrationTest extends AbstractIntegrationTest {
    private String bearer;

    @BeforeEach
    void authenticate() throws Exception {
        bearer = "Bearer " + token("admin");
    }

    @Test
    void supplierUpdateRequiresCurrentVersionAndTaxIdentifierIsCanonical() throws Exception {
        String taxId = unique("supply");
        JsonNode supplier = call(post("/api/v1/suppliers"), supplierInput("Vendor", taxId, 5), 201);
        long id = supplier.path("id").asLong();
        assertThat(supplier.path("taxId").asText()).isEqualTo(taxId.toUpperCase(java.util.Locale.ROOT));
        call(post("/api/v1/suppliers"), supplierInput("Duplicate", taxId.toUpperCase(java.util.Locale.ROOT), 5), 409);
        call(put("/api/v1/suppliers/{id}", id), supplierInput("Missing version", taxId, 5), 400);
        Map<String, Object> update = Map.of("name", "Updated", "taxId", taxId, "email", "new@example.test",
                "leadTimeDays", 8, "version", supplier.path("version").asLong());
        call(put("/api/v1/suppliers/{id}", id), update, 200);
        call(put("/api/v1/suppliers/{id}", id), update, 409);
        assertThat(call(get("/api/v1/suppliers/{id}", id), null, 200).path("leadTimeDays").asInt()).isEqualTo(8);
        call(delete("/api/v1/suppliers/{id}", id), null, 204);
        call(delete("/api/v1/suppliers/{id}", id), null, 204);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE entity_type='SUPPLIER' AND entity_id=? AND action='SUPPLIER_DEACTIVATED'",
                Long.class, Long.toString(id))).isEqualTo(1);
    }

    @Test
    void draftPricesCanChangeButSubmittedPurchaseAndDeliveryPlanAreStable() throws Exception {
        long supplier = supplier(7);
        long product = product();
        long order = purchase(supplier);
        var draft = call(get("/api/v1/purchase-orders/{id}", order), null, 200);
        assertThat(draft.path("expectedOn").asText()).isEqualTo(LocalDate.now(ZoneOffset.UTC).plusDays(7).toString());
        call(post("/api/v1/purchase-orders/{id}/submit", order), null, 409);
        line(order, product, 3, "4.25");
        var updated = call(put("/api/v1/purchase-orders/{id}/lines/{product}", order, product),
                Map.of("quantity", 4, "unitCost", new BigDecimal("4.50")), 200);
        assertThat(updated.path("total").decimalValue()).isEqualByComparingTo("18.00");
        call(post("/api/v1/purchase-orders/{id}/submit", order), null, 200);
        call(put("/api/v1/purchase-orders/{id}/lines/{product}", order, product),
                Map.of("quantity", 5, "unitCost", 99), 409);
        call(delete("/api/v1/purchase-orders/{id}/lines/{product}", order, product), null, 409);
        jdbc.update("UPDATE product SET unit_price=99 WHERE id=?", product);
        assertThat(call(get("/api/v1/purchase-orders/{id}", order), null, 200)
                .path("lines").get(0).path("unitCost").decimalValue()).isEqualByComparingTo("4.50");
        call(post("/api/v1/purchase-orders/{id}/cancel", order), null, 200);
        call(post("/api/v1/purchase-orders/{id}/receipts", order), receipt(UUID.randomUUID(), product, 1), 409);
        assertThat(stock(product, "on_hand")).isZero();
    }

    @Test
    void partialAndFinalReceiptsIncreaseOnlyPhysicalStockAndReplayTheOriginalReceipt() throws Exception {
        long first = product();
        long second = product();
        long supplier = supplier(2);
        long order = purchase(supplier);
        line(order, first, 10, "2.00");
        line(order, second, 4, "3.00");
        call(post("/api/v1/purchase-orders/{id}/submit", order), null, 200);
        // Existing sales reservations remain committed when new goods arrive.
        call(post("/api/v1/inventory/{id}/adjustments", first),
                Map.of("quantity", 20, "reason", "Stock before purchase receipt"), 204);
        reserveForSale(first, 5);
        UUID key = UUID.randomUUID();
        Map<String, Object> payload = Map.of("requestId", key, "deliveryReference", "DEL-1",
                "lines", List.of(receiptLine(second, 1), receiptLine(first, 3)));
        var receipt = call(post("/api/v1/purchase-orders/{id}/receipts", order), payload, 200);
        assertThat(stock(first, "on_hand")).isEqualTo(23);
        assertThat(stock(first, "reserved")).isEqualTo(5);
        assertThat(stock(second, "on_hand")).isEqualTo(1);
        assertThat(call(get("/api/v1/purchase-orders/{id}", order), null, 200).path("status").asText())
                .isEqualTo("PARTIALLY_RECEIVED");
        Map<String, Object> reordered = Map.of("requestId", key, "deliveryReference", " DEL-1 ",
                "lines", List.of(receiptLine(first, 3), receiptLine(second, 1)));
        assertThat(call(post("/api/v1/purchase-orders/{id}/receipts", order), reordered, 200)).isEqualTo(receipt);
        assertThat(stock(first, "on_hand")).isEqualTo(23);
        call(post("/api/v1/purchase-orders/{id}/cancel", order), null, 409);
        var mismatch = call(post("/api/v1/purchase-orders/{id}/receipts", order),
                Map.of("requestId", key, "deliveryReference", "DEL-1", "lines", List.of(receiptLine(first, 4))), 409);
        assertThat(mismatch.path("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        call(post("/api/v1/purchase-orders/{id}/receipts", order),
                Map.of("requestId", UUID.randomUUID(), "deliveryReference", "DEL-2",
                        "lines", List.of(receiptLine(first, 7), receiptLine(second, 3))), 200);
        var complete = call(get("/api/v1/purchase-orders/{id}", order), null, 200);
        assertThat(complete.path("status").asText()).isEqualTo("RECEIVED");
        assertThat(complete.path("total").decimalValue()).isEqualByComparingTo("32.00");
        assertThat(stock(first, "on_hand")).isEqualTo(30);
        assertThat(stock(first, "reserved")).isEqualTo(5);
        assertThat(call(post("/api/v1/purchase-orders/{id}/receipts", order), payload, 200)).isEqualTo(receipt);
        assertThat(call(get("/api/v1/purchase-orders/{id}/receipts", order), null, 200)
                .path("totalElements").asLong()).isEqualTo(2);
        assertThat(call(get("/api/v1/purchase-orders/{id}/receipts/{receipt}", order, receipt.path("id").asLong()), null, 200))
                .isEqualTo(receipt);
        long unrelated = purchase(supplier);
        call(get("/api/v1/purchase-orders/{id}/receipts/{receipt}", unrelated, receipt.path("id").asLong()), null, 404);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action='PURCHASE_RECEIVED' AND entity_id=?",
                Long.class, Long.toString(order))).isEqualTo(2);
    }

    @Test
    void openCommitmentsCountOrdersOnceAndValueOnlyOutstandingUnits() throws Exception {
        long supplier = supplier(1);
        long gloves = product();
        long boots = product();
        LocalDate early = LocalDate.now(ZoneOffset.UTC).plusDays(4);
        LocalDate later = early.plusDays(2);
        String purchases = "/api/v1/purchase-orders";
        String submit = purchases + "/{id}/submit";
        String receipts = purchases + "/{id}/receipts";
        String commitments = "/api/v1/suppliers/{id}/open-commitments";
        long first = call(post(purchases), Map.of("supplierId", supplier, "expectedOn", early.toString()), 201).get("id").asLong();
        long second = call(post(purchases), Map.of("supplierId", supplier, "expectedOn", later.toString()), 201).get("id").asLong();
        line(first, gloves, 5, "2.15");
        line(first, boots, 2, "0.30");
        line(second, gloves, 1, "3.10");
        line(second, boots, 2, "1.05");
        call(post(submit, first), null, 200);
        call(post(submit, second), null, 200);
        call(post(receipts, first), receipt(UUID.randomUUID(), gloves, 2), 200);

        long draft = purchase(supplier);
        line(draft, boots, 12, "50.00");
        long cancelled = purchase(supplier);
        line(cancelled, boots, 11, "60.00");
        call(post(purchases + "/{id}/cancel", cancelled), null, 200);
        submitted(gloves, 9); // Another supplier's open order.
        call(delete("/api/v1/suppliers/{id}", supplier), null, 204);
        String readonlyBearer = "Bearer " + token("readonly");
        JsonNode summary = callAs(readonlyBearer, get(commitments, supplier), null, 200);
        assertThat(summary.get("supplierId").asLong()).isEqualTo(supplier);
        assertThat(summary.get("purchaseOrderCount").asLong()).isEqualTo(2);
        assertThat(summary.get("outstandingUnits").asLong()).isEqualTo(8);
        assertThat(summary.get("outstandingValue").decimalValue()).isEqualByComparingTo("12.25");
        assertThat(summary.get("earliestExpectedOn").asText()).isEqualTo(early.toString());

        call(post(receipts, first), Map.of("requestId", UUID.randomUUID(), "deliveryReference", "FINAL-A",
                "lines", List.of(receiptLine(gloves, 3), receiptLine(boots, 2))), 200);
        JsonNode remaining = callAs(readonlyBearer, get(commitments, supplier), null, 200);
        assertThat(remaining.get("purchaseOrderCount").asLong()).isEqualTo(1);
        assertThat(remaining.get("earliestExpectedOn").asText()).isEqualTo(later.toString());
        call(post(receipts, second), Map.of("requestId", UUID.randomUUID(), "deliveryReference", "FINAL-B",
                "lines", List.of(receiptLine(gloves, 1), receiptLine(boots, 2))), 200);
        JsonNode empty = callAs(readonlyBearer, get(commitments, supplier), null, 200);
        assertThat(empty.get("supplierId").asLong()).isEqualTo(supplier);
        assertThat(empty.get("purchaseOrderCount").asLong()).isZero();
        assertThat(empty.get("outstandingUnits").asLong()).isZero();
        assertThat(empty.get("outstandingValue").decimalValue()).isEqualByComparingTo("0");
        assertThat(empty.get("earliestExpectedOn").isNull()).isTrue();
    }

    @Test
    void readonlyCanReadSupplierCommitmentsAndGetsNotFoundForUnknownSupplier() throws Exception {
        long supplier = supplier(1);
        bearer = "Bearer " + token("readonly");
        String commitments = "/api/v1/suppliers/{id}/open-commitments";
        assertThat(call(get(commitments, supplier), null, 200).get("supplierId").asLong()).isEqualTo(supplier);
        assertThat(call(get(commitments, Long.MAX_VALUE), null, 404).get("code").asText()).isEqualTo("SUPPLIER_NOT_FOUND");
    }

    @Test
    void reusedReceiptRequestIdRejectsChangedDeliveryAndPreservesOriginalReceipt() throws Exception {
        long productId = product();
        long orderId = submitted(productId, 10);
        long initialStock = stock(productId, "on_hand");
        String receipts = "/api/v1/purchase-orders/{id}/receipts";
        UUID requestId = UUID.randomUUID();
        var firstDelivery = Map.of("requestId", requestId, "deliveryReference", "ALBARAN-A",
                "lines", List.of(receiptLine(productId, 3)));
        var original = call(post(receipts, orderId), firstDelivery, 200);

        var changedDelivery = Map.of("requestId", requestId, "deliveryReference", "ALBARAN-B",
                "lines", List.of(receiptLine(productId, 4)));
        var conflict = call(post(receipts, orderId), changedDelivery, 409);
        assertThat(conflict.get("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(call(post(receipts, orderId), firstDelivery, 200)).isEqualTo(original);
        assertThat(call(get(receipts, orderId), null, 200).get("totalElements").asLong()).isEqualTo(1);
        assertThat(stock(productId, "on_hand")).isEqualTo(initialStock + 3);
        var order = call(get("/api/v1/purchase-orders/{id}", orderId), null, 200);
        assertThat(order.get("status").asText()).isEqualTo("PARTIALLY_RECEIVED");
        var line = order.get("lines").get(0);
        assertThat(line.get("receivedQuantity").asLong()).isEqualTo(3);
        assertThat(line.get("outstandingQuantity").asLong()).isEqualTo(7);

        call(post(receipts, orderId), receipt(UUID.randomUUID(), productId, 7), 200);
        var completed = call(get("/api/v1/purchase-orders/{id}", orderId), null, 200);
        assertThat(completed.get("status").asText()).isEqualTo("RECEIVED");
        assertThat(stock(productId, "on_hand")).isEqualTo(initialStock + 10);
    }

    @Test
    void multiLineOverReceiptLeavesAllStockAndReceiptsUntouched() throws Exception {
        long first = product();
        long second = product();
        long order = purchase(supplier(1));
        line(order, first, 5, "2.00");
        line(order, second, 2, "3.00");
        call(post("/api/v1/purchase-orders/{id}/submit", order), null, 200);
        var response = call(post("/api/v1/purchase-orders/{id}/receipts", order),
                Map.of("requestId", UUID.randomUUID(), "deliveryReference", "OVER",
                        "lines", List.of(receiptLine(first, 3), receiptLine(second, 3))), 409);
        assertThat(response.path("code").asText()).isEqualTo("PURCHASE_OVER_RECEIPT");
        assertThat(stock(first, "on_hand")).isZero();
        assertThat(stock(second, "on_hand")).isZero();
        assertThat(jdbc.queryForObject("SELECT sum(received_quantity) FROM purchase_order_line WHERE purchase_order_id=?",
                Long.class, order)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_receipt WHERE purchase_order_id=?", Long.class, order)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_movement WHERE product_id IN (?,?)", Long.class, first, second)).isZero();
        assertThat(call(get("/api/v1/purchase-orders/{id}", order), null, 200).path("status").asText()).isEqualTo("SUBMITTED");
    }

    @Test
    void overflowInAnotherInventoryRollsBackWithoutPublishingAReceipt() throws Exception {
        long first = product();
        long second = product();
        long order = purchase(supplier(1));
        line(order, first, 2, "1.00");
        line(order, second, 2, "1.00");
        call(post("/api/v1/purchase-orders/{id}/submit", order), null, 200);
        jdbc.update("UPDATE inventory_balance SET on_hand=? WHERE product_id=?", Long.MAX_VALUE, second);
        try {
            var response = call(post("/api/v1/purchase-orders/{id}/receipts", order),
                    Map.of("requestId", UUID.randomUUID(), "deliveryReference", "OVERFLOW",
                            "lines", List.of(receiptLine(first, 2), receiptLine(second, 1))), 400);
            assertThat(response.path("code").asText()).isEqualTo("STOCK_AMOUNT_TOO_LARGE");
            assertThat(stock(first, "on_hand")).isZero();
            assertThat(stock(second, "on_hand")).isEqualTo(Long.MAX_VALUE);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_receipt WHERE purchase_order_id=?", Long.class, order)).isZero();
        } finally {
            // This boundary fixture must not affect aggregate reports in other test classes.
            jdbc.update("UPDATE inventory_balance SET on_hand=0 WHERE product_id=?", second);
        }
    }

    @Test
    void concurrentReceiptsCannotReceiveMoreThanOrdered() throws Exception {
        long product = product();
        long order = submitted(product, 10);
        var start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return status(post("/api/v1/purchase-orders/{id}/receipts", order), receipt(UUID.randomUUID(), product, 6));
            });
            var second = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return status(post("/api/v1/purchase-orders/{id}/receipts", order), receipt(UUID.randomUUID(), product, 6));
            });
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(stock(product, "on_hand")).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT received_quantity FROM purchase_order_line WHERE purchase_order_id=? AND product_id=?",
                Long.class, order, product)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_receipt WHERE purchase_order_id=?", Long.class, order)).isEqualTo(1);
    }

    @Test
    void concurrentSameRequestReceivesStockExactlyOnce() throws Exception {
        long product = product();
        long order = submitted(product, 6);
        var payload = receipt(UUID.randomUUID(), product, 6);
        var start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return call(post("/api/v1/purchase-orders/{id}/receipts", order), payload, 200);
            });
            var second = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return call(post("/api/v1/purchase-orders/{id}/receipts", order), payload, 200);
            });
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(second.get(20, TimeUnit.SECONDS));
        }
        assertThat(stock(product, "on_hand")).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_receipt WHERE purchase_order_id=?", Long.class, order)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_movement WHERE product_id=? AND kind='RECEIPT'", Long.class, product)).isEqualTo(1);
    }

    @Test
    void supplierDeactivationBlocksNewCommitmentsButAllowsReceivingSubmittedGoods() throws Exception {
        long supplier = supplier(1);
        long product = product();
        long draft = purchase(supplier);
        long committed = purchase(supplier);
        line(draft, product, 2, "1.00");
        line(committed, product, 2, "1.00");
        call(post("/api/v1/purchase-orders/{id}/submit", committed), null, 200);
        call(delete("/api/v1/suppliers/{id}", supplier), null, 204);
        call(post("/api/v1/purchase-orders"), Map.of("supplierId", supplier), 409);
        call(post("/api/v1/purchase-orders/{id}/submit", draft), null, 409);
        call(post("/api/v1/purchase-orders/{id}/receipts", committed), receipt(UUID.randomUUID(), product, 2), 200);
        assertThat(stock(product, "on_hand")).isEqualTo(2);
    }

    @Test
    void receiptValidationRejectsDuplicateUnknownAndInvalidLinesWithoutChanges() throws Exception {
        long product = product();
        long other = product();
        long order = submitted(product, 5);
        call(post("/api/v1/purchase-orders/{id}/receipts", order),
                Map.of("requestId", UUID.randomUUID(), "deliveryReference", "DUP",
                        "lines", List.of(receiptLine(product, 1), receiptLine(product, 1))), 400);
        call(post("/api/v1/purchase-orders/{id}/receipts", order), receipt(UUID.randomUUID(), other, 1), 409);
        call(post("/api/v1/purchase-orders/{id}/receipts", order), receipt(UUID.randomUUID(), product, 0), 400);
        call(post("/api/v1/purchase-orders/{id}/receipts", order),
                Map.of("requestId", UUID.randomUUID(), "deliveryReference", "EMPTY", "lines", List.of()), 400);
        assertThat(stock(product, "on_hand")).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_receipt WHERE purchase_order_id=?", Long.class, order)).isZero();
    }

    @Test
    void dueBeforeIncludesEarlierPurchaseAndExcludesPurchaseOnCutoffDate() throws Exception {
        long supplierId = supplier(1);
        LocalDate cutoff = LocalDate.now(ZoneOffset.UTC).plusDays(3);
        LocalDate earlierDate = cutoff.minusDays(1);
        var earlierPurchase = call(post("/api/v1/purchase-orders"),
                Map.of("supplierId", supplierId, "expectedOn", earlierDate.toString()), 201);
        call(post("/api/v1/purchase-orders"),
                Map.of("supplierId", supplierId, "expectedOn", cutoff.toString()), 201);

        var page = call(get("/api/v1/purchase-orders")
                .param("supplierId", Long.toString(supplierId))
                .param("dueBefore", cutoff.toString()), null, 200);

        assertThat(page.path("content").size()).isEqualTo(1);
        var onlyPurchase = page.path("content").get(0);
        assertThat(onlyPurchase.get("id")).isEqualTo(earlierPurchase.get("id"));
        assertThat(onlyPurchase.path("expectedOn").asText()).isEqualTo(earlierDate.toString());
        assertThat(page.path("totalElements").asLong()).isEqualTo(1);
    }

    @Test
    void listFiltersAndPagingUseTheSamePopulationAndRejectUnknownSort() throws Exception {
        long supplier = supplier(3);
        long first = purchase(supplier);
        long second = purchase(supplier);
        long third = purchase(supplier);
        call(post("/api/v1/purchase-orders/{id}/cancel", second), null, 200);
        var page = call(get("/api/v1/purchase-orders").param("supplierId", Long.toString(supplier))
                .param("status", "DRAFT").param("sort", "id").param("direction", "asc")
                .param("page", "1").param("size", "1"), null, 200);
        assertThat(page.path("totalElements").asLong()).isEqualTo(2);
        assertThat(page.path("content").get(0).path("id").asLong()).isEqualTo(third);
        assertThat(page.path("totalPages").asInt()).isEqualTo(2);
        call(get("/api/v1/purchase-orders").param("status", "INVALID"), null, 400);
        call(get("/api/v1/purchase-orders").param("sort", "id;DROP TABLE supplier"), null, 400);
        call(get("/api/v1/suppliers").param("page", "-1"), null, 400);
        var outside = call(get("/api/v1/purchase-orders").param("supplierId", Long.toString(supplier))
                .param("dueBefore", LocalDate.now(ZoneOffset.UTC).plusDays(3).toString()), null, 200);
        assertThat(outside.path("totalElements").asLong()).isZero();
        assertThat(first).isLessThan(third);
    }

    @Test
    void supplierSearchTreatsWildcardsLiterallyAndKeepsActiveFilterInCount() throws Exception {
        String prefix = unique("Search");
        long literal = call(post("/api/v1/suppliers"), supplierInput(prefix + "_100%", unique("T"), 1), 201).path("id").asLong();
        call(post("/api/v1/suppliers"), supplierInput(prefix + "X100zz", unique("T"), 1), 201);
        var page = call(get("/api/v1/suppliers").param("search", prefix + "_100%"), null, 200);
        assertThat(page.path("totalElements").asLong()).isEqualTo(1);
        assertThat(page.path("content").get(0).path("id").asLong()).isEqualTo(literal);
        call(delete("/api/v1/suppliers/{id}", literal), null, 204);
        assertThat(call(get("/api/v1/suppliers").param("search", prefix + "_100%").param("active", "true"), null, 200)
                .path("totalElements").asLong()).isZero();
    }

    @Test
    void readonlyCannotCreateSuppliersOrReceiveGoodsAndAnonymousCannotRead() throws Exception {
        long product = product();
        long order = submitted(product, 1);
        bearer = "Bearer " + token("readonly");
        call(get("/api/v1/purchase-orders/{id}", order), null, 200);
        call(post("/api/v1/suppliers"), supplierInput("Forbidden", unique("T"), 2), 403);
        call(post("/api/v1/purchase-orders/{id}/receipts", order), receipt(UUID.randomUUID(), product, 1), 403);
        assertThat(stock(product, "on_hand")).isZero();
        assertThat(mvc.perform(get("/api/v1/suppliers")).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void invalidCostDateAndTotalDoNotPersistAPartialPurchaseChange() throws Exception {
        long supplier = supplier(0);
        call(post("/api/v1/purchase-orders"), Map.of("supplierId", supplier,
                "expectedOn", LocalDate.now(ZoneOffset.UTC).minusDays(1).toString()), 400);
        long product = product();
        long order = purchase(supplier);
        call(put("/api/v1/purchase-orders/{id}/lines/{product}", order, product),
                Map.of("quantity", 1, "unitCost", new BigDecimal("0.001")), 400);
        call(put("/api/v1/purchase-orders/{id}/lines/{product}", order, product),
                Map.of("quantity", 1000000, "unitCost", new BigDecimal("999999999999.99")), 400);
        assertThat(call(get("/api/v1/purchase-orders/{id}", order), null, 200).path("lines")).isEmpty();
        line(order, product, 2, "1.50");
        call(delete("/api/v1/purchase-orders/{id}/lines/{product}", order, product), null, 200);
        assertThat(call(get("/api/v1/purchase-orders/{id}", order), null, 200).path("total").decimalValue()).isEqualByComparingTo("0");
    }

    private Map<String, Object> supplierInput(String name, String taxId, int leadTimeDays) {
        return Map.of("name", name, "taxId", taxId, "email", "supply@example.test", "leadTimeDays", leadTimeDays);
    }

    private long supplier(int days) throws Exception {
        return call(post("/api/v1/suppliers"), supplierInput(unique("Vendor"), unique("T"), days), 201).path("id").asLong();
    }

    private long product() throws Exception {
        return call(post("/api/v1/products"), Map.of("sku", unique("PUR"), "name", unique("Product"), "unitPrice", 10), 201)
                .path("id").asLong();
    }

    private long purchase(long supplier) throws Exception {
        return call(post("/api/v1/purchase-orders"), Map.of("supplierId", supplier), 201).path("id").asLong();
    }

    private void line(long order, long product, long quantity, String cost) throws Exception {
        call(put("/api/v1/purchase-orders/{id}/lines/{product}", order, product),
                Map.of("quantity", quantity, "unitCost", new BigDecimal(cost)), 200);
    }

    private long submitted(long product, long quantity) throws Exception {
        long order = purchase(supplier(2));
        line(order, product, quantity, "2.00");
        call(post("/api/v1/purchase-orders/{id}/submit", order), null, 200);
        return order;
    }

    private void reserveForSale(long product, long quantity) throws Exception {
        long customer = call(post("/api/v1/customers"), Map.of("name", unique("Buyer"),
                "taxId", unique("T"), "email", "buyer@example.test", "address", "Madrid"), 201).path("id").asLong();
        long order = call(post("/api/v1/orders"), Map.of("customerId", customer), 201).path("id").asLong();
        call(put("/api/v1/orders/{id}/lines/{product}", order, product), Map.of("quantity", quantity), 200);
        call(post("/api/v1/orders/{id}/confirm", order), null, 200);
    }

    private Map<String, Object> receipt(UUID key, long product, long quantity) {
        return Map.of("requestId", key, "deliveryReference", "DELIVERY", "lines", List.of(receiptLine(product, quantity)));
    }

    private Map<String, Object> receiptLine(long product, long quantity) {
        return Map.of("productId", product, "quantity", quantity);
    }

    private long stock(long product, String column) {
        if (!List.of("on_hand", "reserved").contains(column)) throw new IllegalArgumentException();
        return jdbc.queryForObject("SELECT " + column + " FROM inventory_balance WHERE product_id=?", Long.class, product);
    }

    private int status(MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    private JsonNode call(MockHttpServletRequestBuilder request, Object body, int expectedStatus) throws Exception {
        return callAs(bearer, request, body, expectedStatus);
    }

    private JsonNode callAs(String authorization, MockHttpServletRequestBuilder request,
                            Object body, int expectedStatus) throws Exception {
        request.header("Authorization", authorization);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).withFailMessage("HTTP %s: %s", response.getStatus(), response.getContentAsString())
                .isEqualTo(expectedStatus);
        return response.getContentAsString().isBlank() ? mapper.nullNode() : json(response.getContentAsString());
    }
}
