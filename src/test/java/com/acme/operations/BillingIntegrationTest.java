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

class BillingIntegrationTest extends AbstractIntegrationTest {
    private String bearer;

    @BeforeEach
    void authenticate() throws Exception {
        bearer = "Bearer " + token("admin");
        CARRIER_STATUS.set(200);
        CARRIER_ENTERED.set(null);
        CARRIER_RELEASE.set(null);
    }

    @Test
    void issuingSnapshotsCustomerAndOrderAmountsAndReplaysWithoutAnotherInvoiceOrAudit()
            throws Exception {
        Sale sale = sale(2, "10.00");
        LocalDate due = today().plusDays(30);
        JsonNode originalCustomer = call(get("/api/v1/customers/{id}", sale.customerId()), null, 200);
        JsonNode issued = issue(sale.orderId(), due);
        long id = issued.path("id").asLong();
        assertThat(issued.path("total").decimalValue()).isEqualByComparingTo("32.50");
        assertThat(issued.path("productTotal").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(issued.path("shippingTotal").decimalValue()).isEqualByComparingTo("12.50");
        assertThat(issued.path("currency").asText()).isEqualTo("EUR");

        call(put("/api/v1/customers/{id}", sale.customerId()),
                Map.of("name", "New legal name", "taxId", unique("B"),
                        "email", "changed@example.test", "address", "Changed address",
                        "version", originalCustomer.path("version").asLong()), 200);
        JsonNode product = call(get("/api/v1/products/{id}", sale.productId()), null, 200);
        call(put("/api/v1/products/{id}", sale.productId()),
                Map.of("sku", product.path("sku").asText(), "name", "New product name",
                        "unitPrice", 99, "version", product.path("version").asLong()), 200);

        JsonNode replay = issue(sale.orderId(), due);
        assertThat(replay.path("id").asLong()).isEqualTo(id);
        assertThat(replay.path("customerName").asText()).isEqualTo(originalCustomer.path("name").asText());
        assertThat(replay.path("customerTaxId").asText()).isEqualTo(originalCustomer.path("taxId").asText());
        assertThat(replay.path("customerAddress").asText()).isEqualTo(originalCustomer.path("address").asText());
        assertThat(replay.path("productTotal").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(call(post("/api/v1/billing/invoices"),
                Map.of("orderId", sale.orderId(), "dueDate", due.plusDays(1)), 409)
                .path("code").asText()).isEqualTo("INVOICE_ALREADY_ISSUED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invoice WHERE order_id=?", Long.class, sale.orderId()))
                .isEqualTo(1);
        assertThat(auditCount(id, "INVOICE_ISSUED")).isEqualTo(1);
    }

    @Test
    void issuanceRequiresCompletedOrderAndInclusiveDueDateWindow() throws Exception {
        Sale sale = sale(1, "10.00");
        long draft = draft(sale.customerId(), sale.productId(), 1);
        assertThat(call(post("/api/v1/billing/invoices"),
                Map.of("orderId", draft, "dueDate", today()), 409).path("code").asText())
                .isEqualTo("ORDER_NOT_COMPLETED");
        call(post("/api/v1/billing/invoices"), Map.of("orderId", sale.orderId(), "dueDate", today().minusDays(1)), 400);
        call(post("/api/v1/billing/invoices"), Map.of("orderId", sale.orderId(), "dueDate", today().plusDays(91)), 400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM invoice WHERE order_id=?", Long.class, sale.orderId()))
                .isZero();
        assertThat(issue(sale.orderId(), today().plusDays(90)).path("dueDate").asText())
                .isEqualTo(today().plusDays(90).toString());
        long completed = complete(sale.customerId(), sale.productId(), 1);
        assertThat(issue(completed, today()).path("dueDate").asText()).isEqualTo(today().toString());
    }

    @Test
    void receivedGoodsProduceCreditAndRefundWhileOriginalShippingRemainsPaid() throws Exception {
        Sale sale = sale(2, "10.00");
        long invoice = issue(sale.orderId(), today()).path("id").asLong();
        settle(invoice, "payments", "32.50");
        assertThat(invoice(invoice).path("status").asText()).isEqualTo("SETTLED");
        long returned = requestReturn(sale.orderId(), sale.productId(), 2);
        approveReturn(returned);
        assertThat(invoice(invoice).path("credited").decimalValue()).isZero();
        receiveReturn(returned, sale.productId(), 1, 1);
        JsonNode credit = invoice(invoice);
        assertThat(credit.path("credited").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(credit.path("balance").decimalValue()).isEqualByComparingTo("-20.00");
        assertThat(credit.path("status").asText()).isEqualTo("CREDIT_DUE");
        settle(invoice, "refunds", "20.00");
        JsonNode settled = invoice(invoice);
        assertThat(settled.path("balance").decimalValue()).isZero();
        assertThat(settled.path("netPaid").decimalValue()).isEqualByComparingTo("12.50");
        assertThat(settled.path("shippingTotal").decimalValue()).isEqualByComparingTo("12.50");
        assertThat(settled.path("status").asText()).isEqualTo("SETTLED");
        JsonNode credits = call(get("/api/v1/billing/invoices/{id}/credits", invoice), null, 200);
        assertThat(credits.path("totalElements").asLong()).isEqualTo(1);
        assertThat(credits.path("content").get(0).path("returnId").asLong()).isEqualTo(returned);
        assertThat(call(post("/api/v1/billing/invoices/{id}/refunds", invoice),
                settlement(UUID.randomUUID(), "0.01", "Extra refund"), 409).path("code").asText())
                .isEqualTo("REFUND_EXCEEDS_CREDIT");
    }

    @Test
    void twoReturnsAndTwoPaymentsAreAggregatedWithoutMultiplyingEitherRelationship() throws Exception {
        Sale sale = sale(10, "10.00");
        long invoice = issue(sale.orderId(), today()).path("id").asLong();
        settle(invoice, "payments", "20.00");
        settle(invoice, "payments", "30.00");
        receiveAll(sale.orderId(), sale.productId(), 1);
        receiveAll(sale.orderId(), sale.productId(), 2);
        long rejected = requestReturn(sale.orderId(), sale.productId(), 1);
        call(post("/api/v1/returns/{id}/reject", rejected), Map.of("version", 0, "reason", "Outside policy"), 200);
        requestReturn(sale.orderId(), sale.productId(), 1);

        JsonNode summary = invoice(invoice);
        assertThat(summary.path("total").decimalValue()).isEqualByComparingTo("112.50");
        assertThat(summary.path("credited").decimalValue()).isEqualByComparingTo("30.00");
        assertThat(summary.path("netPaid").decimalValue()).isEqualByComparingTo("50.00");
        assertThat(summary.path("balance").decimalValue()).isEqualByComparingTo("32.50");
        assertThat(call(get("/api/v1/billing/invoices/{id}/entries", invoice), null, 200)
                .path("totalElements").asLong()).isEqualTo(2);
        assertThat(call(get("/api/v1/billing/invoices/{id}/credits", invoice), null, 200)
                .path("totalElements").asLong()).isEqualTo(2);
        JsonNode listed = call(get("/api/v1/billing/invoices")
                .param("customerId", Long.toString(sale.customerId())).param("status", "PARTIALLY_PAID"), null, 200);
        assertThat(listed.path("totalElements").asLong()).isEqualTo(1);
        assertThat(listed.path("content").get(0).path("balance").decimalValue()).isEqualByComparingTo("32.50");
    }

    @Test
    void settlementReplaySurvivesReversalWithoutResurrectingOrDuplicatingTheEntry() throws Exception {
        Sale sale = sale(2, "10.00");
        long invoice = issue(sale.orderId(), today()).path("id").asLong();
        UUID key = UUID.randomUUID();
        var body = settlement(key, "10.00", "Bank reference");
        long entry = call(post("/api/v1/billing/invoices/{id}/payments", invoice), body, 200).path("id").asLong();
        assertThat(call(post("/api/v1/billing/invoices/{id}/payments", invoice),
                settlement(key, "10.0", " Bank reference "), 200).path("id").asLong()).isEqualTo(entry);
        assertThat(call(post("/api/v1/billing/invoices/{id}/payments", invoice),
                settlement(key, "11.00", "Bank reference"), 409).path("code").asText())
                .isEqualTo("IDEMPOTENCY_CONFLICT");
        assertThat(call(post("/api/v1/billing/invoices/{id}/refunds", invoice), body, 409)
                .path("code").asText()).isEqualTo("IDEMPOTENCY_CONFLICT");
        JsonNode reversed = reverse(invoice, entry, "Duplicate bank registration", 200);
        assertThat(reverse(invoice, entry, "Duplicate bank registration", 200)).isEqualTo(reversed);
        assertThat(reverse(invoice, entry, "Changed reason", 409).path("code").asText())
                .isEqualTo("REVERSAL_CONFLICT");
        JsonNode replay = call(post("/api/v1/billing/invoices/{id}/payments", invoice), body, 200);
        assertThat(replay.path("id").asLong()).isEqualTo(entry);
        assertThat(replay.path("reversedAt").isNull()).isFalse();
        assertThat(invoice(invoice).path("netPaid").decimalValue()).isZero();
        assertThat(invoice(invoice).path("balance").decimalValue()).isEqualByComparingTo("32.50");
        assertThat(entryCount(invoice)).isEqualTo(1);
        assertThat(auditCount(invoice, "INVOICE_PAYMENT_RECORDED")).isEqualTo(1);
        assertThat(auditCount(invoice, "INVOICE_ENTRY_REVERSED")).isEqualTo(1);
    }

    @Test
    void cannotReversePaymentBelowZeroNetPaidUntilRelatedRefundHasBeenReversed() throws Exception {
        Sale sale = sale(2, "10.00");
        long invoice = issue(sale.orderId(), today()).path("id").asLong();
        long payment = settle(invoice, "payments", "32.50").path("id").asLong();
        receiveAll(sale.orderId(), sale.productId(), 2);
        long refund = settle(invoice, "refunds", "20.00").path("id").asLong();
        assertThat(reverse(invoice, payment, "Bank correction", 409).path("code").asText())
                .isEqualTo("PAYMENT_ALREADY_REFUNDED");
        assertThat(jdbc.queryForObject("SELECT reversed_at IS NULL FROM invoice_entry WHERE id=?", Boolean.class, payment))
                .isTrue();
        assertThat(invoice(invoice).path("netPaid").decimalValue()).isEqualByComparingTo("12.50");
        reverse(invoice, refund, "Refund cancelled before processing", 200);
        reverse(invoice, payment, "Bank correction", 200);
        assertThat(invoice(invoice).path("netPaid").decimalValue()).isZero();
        assertThat(invoice(invoice).path("balance").decimalValue()).isEqualByComparingTo("12.50");
    }

    @Test
    void rejectsOverpaymentInvalidAmountsAndUnfundedRefundWithoutPartialLedgerOrAudit() throws Exception {
        Sale sale = sale(1, "10.00");
        long invoice = issue(sale.orderId(), today()).path("id").asLong();
        assertThat(call(post("/api/v1/billing/invoices/{id}/payments", invoice),
                settlement(UUID.randomUUID(), "22.51", "Excess"), 409).path("code").asText())
                .isEqualTo("PAYMENT_EXCEEDS_BALANCE");
        for (String amount : List.of("0", "-1", "0.001"))
            call(post("/api/v1/billing/invoices/{id}/payments", invoice), settlement(UUID.randomUUID(), amount, "Invalid"), 400);
        call(post("/api/v1/billing/invoices/{id}/payments", invoice), settlement(UUID.randomUUID(), "1.00", " "), 400);
        call(post("/api/v1/billing/invoices/{id}/refunds", invoice), settlement(UUID.randomUUID(), "1.00", "No credit"), 409);
        assertThat(entryCount(invoice)).isZero();
        assertThat(auditCount(invoice, "INVOICE_PAYMENT_RECORDED")).isZero();
        assertThat(auditCount(invoice, "INVOICE_REFUND_RECORDED")).isZero();
        settle(invoice, "payments", "22.50");
        call(post("/api/v1/billing/invoices/{id}/payments", invoice), settlement(UUID.randomUUID(), "0.01", "Already paid"), 409);
        assertThat(entryCount(invoice)).isEqualTo(1);
        assertThat(invoice(invoice).path("balance").decimalValue()).isZero();
    }

    @Test
    void settlementPermissionsAndEntryOwnershipAreEnforcedAtTheRequestedInvoice() throws Exception {
        Sale first = sale(1, "10.00");
        Sale second = sale(1, "20.00");
        long invoiceA = issue(first.orderId(), today()).path("id").asLong();
        long invoiceB = issue(second.orderId(), today()).path("id").asLong();
        long entryA = settle(invoiceA, "payments", "1.00").path("id").asLong();
        bearer = "Bearer " + token("operations");
        settle(invoiceB, "payments", "1.00");
        reverse(invoiceA, entryA, "Operations cannot reverse", 403);
        bearer = "Bearer " + token("readonly");
        invoice(invoiceA);
        call(get("/api/v1/billing/invoices/{id}/entries/{entry}", invoiceA, entryA), null, 200);
        call(get("/api/v1/billing/invoices/{id}/entries/{entry}", invoiceB, entryA), null, 404);
        call(post("/api/v1/billing/invoices/{id}/payments", invoiceA), settlement(UUID.randomUUID(), "1.00", "Denied"), 403);
        call(post("/api/v1/billing/invoices/{id}/refunds", invoiceA), settlement(UUID.randomUUID(), "1.00", "Denied"), 403);
        call(post("/api/v1/billing/invoices"), Map.of("orderId", first.orderId(), "dueDate", today()), 403);
        bearer = "Bearer " + token("admin");
        reverse(invoiceB, entryA, "Wrong invoice", 404);
        assertThat(entryCount(invoiceA)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT reversed_at IS NULL FROM invoice_entry WHERE id=?", Boolean.class, entryA)).isTrue();
        assertThat(mvc.perform(get("/api/v1/billing/invoices/{id}", invoiceA))
                .andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void concurrentDifferentPaymentsCannotCollectMoreThanTheOutstandingBalance() throws Exception {
        Sale sale = sale(2, "10.00");
        long invoice = issue(sale.orderId(), today()).path("id").asLong();
        var start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return rawPayment(invoice, settlement(UUID.randomUUID(), "25.00", "Bank A"));
            });
            var second = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return rawPayment(invoice, settlement(UUID.randomUUID(), "25.00", "Bank B"));
            });
            assertThat(List.of(first.get(20, TimeUnit.SECONDS).status(), second.get(20, TimeUnit.SECONDS).status()))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(entryCount(invoice)).isEqualTo(1);
        assertThat(invoice(invoice).path("netPaid").decimalValue()).isEqualByComparingTo("25.00");
        assertThat(invoice(invoice).path("balance").decimalValue()).isEqualByComparingTo("7.50");
    }

    @Test
    void concurrentSamePaymentRequestReturnsOnePersistedEntryAndOneAudit() throws Exception {
        Sale sale = sale(2, "10.00");
        long invoice = issue(sale.orderId(), today()).path("id").asLong();
        var body = settlement(UUID.randomUUID(), "10.00", "Retrying bank notification");
        var start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return rawPayment(invoice, body);
            });
            var two = executor.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return rawPayment(invoice, body);
            });
            Raw first = one.get(20, TimeUnit.SECONDS);
            Raw second = two.get(20, TimeUnit.SECONDS);
            assertThat(first.status()).isEqualTo(200);
            assertThat(second.status()).isEqualTo(200);
            assertThat(json(first.body()).path("id").asLong()).isEqualTo(json(second.body()).path("id").asLong());
        }
        assertThat(entryCount(invoice)).isEqualTo(1);
        assertThat(auditCount(invoice, "INVOICE_PAYMENT_RECORDED")).isEqualTo(1);
        assertThat(invoice(invoice).path("netPaid").decimalValue()).isEqualByComparingTo("10.00");
    }

    @Test
    void agingUsesCurrentBalancesAndSeparatesFutureDuesRefundsAndFilteredPages() throws Exception {
        Sale sale = sale(1, "10.00");
        long invoiceA = issue(sale.orderId(), today()).path("id").asLong();
        long invoiceB = issue(complete(sale.customerId(), sale.productId(), 1), today().plusDays(40)).path("id").asLong();
        long invoiceC = issue(complete(sale.customerId(), sale.productId(), 1), today().plusDays(90)).path("id").asLong();
        long returnedOrder = complete(sale.customerId(), sale.productId(), 1);
        long invoiceD = issue(returnedOrder, today()).path("id").asLong();
        settle(invoiceA, "payments", "2.50");
        settle(invoiceD, "payments", "22.50");
        receiveAll(returnedOrder, sale.productId(), 1);

        JsonNode report = call(get("/api/v1/billing/receivables")
                .param("customerId", Long.toString(sale.customerId()))
                .param("asOf", today().plusDays(35).toString()), null, 200);
        assertThat(report.path("balanceBasis").asText()).isEqualTo("CURRENT_BALANCE");
        assertThat(report.path("outstanding").decimalValue()).isEqualByComparingTo("65.00");
        assertThat(report.path("overdue").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(report.path("refundsDue").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(bucket(report, "31_60").path("amount").decimalValue()).isEqualByComparingTo("20.00");
        assertThat(bucket(report, "31_60").path("invoiceCount").asLong()).isEqualTo(1);
        assertThat(bucket(report, "NOT_DUE").path("amount").decimalValue()).isEqualByComparingTo("45.00");
        assertThat(bucket(report, "NOT_DUE").path("invoiceCount").asLong()).isEqualTo(2);
        JsonNode past = call(get("/api/v1/billing/receivables")
                .param("customerId", Long.toString(sale.customerId())).param("asOf", today().minusDays(100).toString()), null, 200);
        assertThat(past.path("balanceBasis").asText()).isEqualTo("CURRENT_BALANCE");
        assertThat(past.path("outstanding").decimalValue()).isEqualByComparingTo("65.00");
        assertThat(past.path("overdue").decimalValue()).isZero();

        JsonNode first = call(get("/api/v1/billing/invoices").param("customerId", Long.toString(sale.customerId()))
                .param("status", "OPEN").param("size", "1"), null, 200);
        JsonNode second = call(get("/api/v1/billing/invoices").param("customerId", Long.toString(sale.customerId()))
                .param("status", "OPEN").param("size", "1").param("page", "1"), null, 200);
        assertThat(first.path("totalElements").asLong()).isEqualTo(2);
        assertThat(first.path("content").get(0).path("id").asLong()).isEqualTo(invoiceB);
        assertThat(second.path("content").get(0).path("id").asLong()).isEqualTo(invoiceC);
        call(get("/api/v1/billing/invoices").param("status", "COMPLETED"), null, 400);
        call(get("/api/v1/billing/invoices").param("size", "101"), null, 400);
        call(get("/api/v1/billing/receivables").param("customerId", "-1"), null, 400);
    }

    private record Sale(long customerId, long productId, long orderId) {}
    private record Raw(int status, String body) {}

    private static LocalDate today() { return LocalDate.now(ZoneOffset.UTC); }

    private Sale sale(long quantity, String price) throws Exception {
        long customer = call(post("/api/v1/customers"), Map.of("name", unique("Billing customer"),
                "taxId", unique("B"), "email", "billing@example.test", "address", "Original billing address"), 201)
                .path("id").asLong();
        long product = call(post("/api/v1/products"), Map.of("sku", unique("BILL"),
                "name", unique("Billing product"), "unitPrice", new BigDecimal(price)), 201).path("id").asLong();
        call(post("/api/v1/inventory/{id}/adjustments", product),
                Map.of("quantity", quantity + 10, "reason", "Billing opening stock"), 204);
        return new Sale(customer, product, complete(customer, product, quantity));
    }

    private long draft(long customer, long product, long quantity) throws Exception {
        long order = call(post("/api/v1/orders"), Map.of("customerId", customer), 201).path("id").asLong();
        call(put("/api/v1/orders/{id}/lines/{productId}", order, product), Map.of("quantity", quantity), 200);
        return order;
    }

    private long complete(long customer, long product, long quantity) throws Exception {
        long order = draft(customer, product, quantity);
        call(post("/api/v1/orders/{id}/confirm", order), null, 200);
        call(post("/api/v1/orders/{id}/complete", order), null, 200);
        return order;
    }

    private JsonNode issue(long order, LocalDate dueDate) throws Exception {
        return call(post("/api/v1/billing/invoices"), Map.of("orderId", order, "dueDate", dueDate), 201);
    }

    private JsonNode invoice(long id) throws Exception {
        return call(get("/api/v1/billing/invoices/{id}", id), null, 200);
    }

    private static Map<String, Object> settlement(UUID requestId, String amount, String reference) {
        return Map.of("requestId", requestId, "amount", new BigDecimal(amount), "reference", reference);
    }

    private JsonNode settle(long invoice, String operation, String amount) throws Exception {
        return call(post("/api/v1/billing/invoices/{id}/{operation}", invoice, operation),
                settlement(UUID.randomUUID(), amount, unique("Bank")), 200);
    }

    private JsonNode reverse(long invoice, long entry, String reason, int status) throws Exception {
        return call(post("/api/v1/billing/invoices/{id}/entries/{entry}/reverse", invoice, entry),
                Map.of("reason", reason), status);
    }

    private long requestReturn(long order, long product, long quantity) throws Exception {
        return call(post("/api/v1/returns"), Map.of("orderId", order, "requestId", UUID.randomUUID(),
                "reason", "Accepted sales return", "lines", List.of(Map.of("productId", product, "quantity", quantity))), 201)
                .path("id").asLong();
    }

    private void approveReturn(long returned) throws Exception {
        call(post("/api/v1/returns/{id}/approve", returned), Map.of("version", 0, "reason", "Approved for inspection"), 200);
    }

    private void receiveReturn(long returned, long product, long restock, long damaged) throws Exception {
        call(post("/api/v1/returns/{id}/receive", returned), Map.of("requestId", UUID.randomUUID(), "version", 1,
                "lines", List.of(Map.of("productId", product, "restockQuantity", restock, "damagedQuantity", damaged))), 200);
    }

    private void receiveAll(long order, long product, long quantity) throws Exception {
        long returned = requestReturn(order, product, quantity);
        approveReturn(returned);
        receiveReturn(returned, product, quantity, 0);
    }

    private long entryCount(long invoice) {
        return jdbc.queryForObject("SELECT count(*) FROM invoice_entry WHERE invoice_id=?", Long.class, invoice);
    }

    private long auditCount(long invoice, String action) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE entity_type='INVOICE' AND entity_id=? AND action=?",
                Long.class, Long.toString(invoice), action);
    }

    private JsonNode bucket(JsonNode report, String name) {
        for (JsonNode bucket : report.path("buckets"))
            if (bucket.path("name").asText().equals(name)) return bucket;
        throw new AssertionError("Missing aging bucket " + name);
    }

    private Raw rawPayment(long invoice, Map<String, Object> body) throws Exception {
        var result = mvc.perform(post("/api/v1/billing/invoices/{id}/payments", invoice)
                        .header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body)))
                .andReturn().getResponse();
        return new Raw(result.getStatus(), result.getContentAsString());
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
