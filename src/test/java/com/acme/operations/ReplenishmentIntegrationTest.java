package com.acme.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

class ReplenishmentIntegrationTest extends AbstractIntegrationTest {
    private String bearer;

    @BeforeEach
    void authenticate() throws Exception {
        bearer = "Bearer " + token("admin");
    }

    @Test
    void policyCreationAndReplacementUseAnExplicitVersionContract() throws Exception {
        long product = product(unique("Policy"));
        var create = policy(4,10,null,true,null);
        assertThat(call(put("/api/v1/replenishment/policies/{id}",product),create,200)
                .path("version").asLong()).isZero();
        assertThat(call(put("/api/v1/replenishment/policies/{id}",product),create,400)
                .path("code").asText()).isEqualTo("VERSION_REQUIRED");
        var updated = call(put("/api/v1/replenishment/policies/{id}",product),
                policy(6,12,null,true,0L),200);
        assertThat(updated.path("version").asLong()).isEqualTo(1);
        assertThat(call(put("/api/v1/replenishment/policies/{id}",product),
                policy(8,20,null,true,0L),409).path("code").asText())
                .isEqualTo("CONCURRENT_MODIFICATION");
        var persisted = call(get("/api/v1/replenishment/policies/{id}",product),null,200);
        assertThat(persisted.path("minimumStock").asLong()).isEqualTo(6);
        assertThat(persisted.path("targetStock").asLong()).isEqualTo(12);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE entity_type='REPLENISHMENT_POLICY' AND entity_id=?",
                Long.class,Long.toString(product))).isEqualTo(2);
    }

    @Test
    void invalidRangesAndCreationVersionsLeaveNoPolicyAndMaximumQuantityIsSupported() throws Exception {
        long product = product(unique("Range"));
        assertThat(call(put("/api/v1/replenishment/policies/{id}",product),
                policy(10,9,null,true,null),400).path("code").asText())
                .isEqualTo("INVALID_REPLENISHMENT_RANGE");
        call(put("/api/v1/replenishment/policies/{id}",product),policy(-1,5,null,true,null),400);
        call(put("/api/v1/replenishment/policies/{id}",product),policy(1,1000000001L,null,true,null),400);
        assertThat(call(put("/api/v1/replenishment/policies/{id}",product),
                policy(1,5,null,true,0L),400).path("code").asText()).isEqualTo("VERSION_NOT_ALLOWED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM replenishment_policy WHERE product_id=?",
                Long.class,product)).isZero();
        call(put("/api/v1/replenishment/policies/{id}",product),
                policy(1000000000,1000000000,null,true,null),200);
        assertThat(proposal(product).path("suggestedQuantity").asLong()).isEqualTo(1000000000);
    }

    @Test
    void proposalCombinesAvailableStockAndEveryOutstandingPurchaseWithoutJoinMultiplication() throws Exception {
        long product = product(unique("Projection"));
        long unrelated = product(unique("Unrelated"));
        long preferred = supplier();
        long alternate = supplier();
        call(post("/api/v1/inventory/{id}/adjustments",product),
                Map.of("quantity",10,"reason","Initial replenishment stock"),204);
        reserve(product,8);
        long partial = purchase(preferred,product,5,true);
        receive(partial,product,2);
        long submitted = purchase(alternate,product,3,false);
        call(put("/api/v1/purchase-orders/{id}/lines/{product}",submitted,unrelated),
                Map.of("quantity",17,"unitCost",2),200);
        call(post("/api/v1/purchase-orders/{id}/submit",submitted),null,200);
        purchase(preferred,product,100,false);
        long cancelled = purchase(preferred,product,100,true);
        call(post("/api/v1/purchase-orders/{id}/cancel",cancelled),null,200);
        long completed = purchase(preferred,product,1,true);
        receive(completed,product,1);
        call(put("/api/v1/replenishment/policies/{id}",product),policy(12,20,preferred,true,null),200);

        JsonNode first = proposal(product);
        assertThat(first.path("available").asLong()).isEqualTo(5);
        assertThat(first.path("incoming").asLong()).isEqualTo(6);
        assertThat(first.path("projected").asLong()).isEqualTo(11);
        assertThat(first.path("suggestedQuantity").asLong()).isEqualTo(9);
        assertThat(first.path("purchasable").asBoolean()).isTrue();
        assertThat(first.path("blockingReason").isNull()).isTrue();

        receive(partial,product,1);
        JsonNode afterReceipt = proposal(product);
        assertThat(afterReceipt.path("available").asLong()).isEqualTo(6);
        assertThat(afterReceipt.path("incoming").asLong()).isEqualTo(5);
        assertThat(afterReceipt.path("projected").asLong()).isEqualTo(11);
        assertThat(afterReceipt.path("suggestedQuantity").asLong()).isEqualTo(9);
        assertThat(jdbc.queryForObject("SELECT reserved FROM inventory_balance WHERE product_id=?",
                Long.class,product)).isEqualTo(8);
    }

    @Test
    void thresholdEqualityAndDisabledPoliciesOrProductsDoNotProduceProposals() throws Exception {
        long product = product(unique("Boundary"));
        long supplier = supplier();
        call(put("/api/v1/replenishment/policies/{id}",product),policy(5,9,supplier,true,null),200);
        long incoming = purchase(supplier,product,5,true);
        assertThat(proposals(product).path("totalElements").asLong()).isZero();
        call(post("/api/v1/purchase-orders/{id}/cancel",incoming),null,200);
        assertThat(proposal(product).path("suggestedQuantity").asLong()).isEqualTo(9);
        call(put("/api/v1/replenishment/policies/{id}",product),policy(5,9,supplier,false,0L),200);
        assertThat(proposals(product).path("totalElements").asLong()).isZero();
        assertThat(call(get("/api/v1/replenishment/policies").param("productId",Long.toString(product))
                .param("active","false"),null,200).path("totalElements").asLong()).isEqualTo(1);
        call(put("/api/v1/replenishment/policies/{id}",product),policy(5,9,supplier,true,1L),200);
        call(delete("/api/v1/products/{id}",product),null,204);
        assertThat(proposals(product).path("totalElements").asLong()).isZero();
        assertThat(call(get("/api/v1/replenishment/policies/{id}",product),null,200)
                .path("productActive").asBoolean()).isFalse();
        call(put("/api/v1/replenishment/policies/{id}",product),policy(5,9,supplier,true,2L),409);
        call(put("/api/v1/replenishment/policies/{id}",product),policy(5,9,supplier,false,2L),200);
    }

    @Test
    void missingBalancesRemainVisibleAndPaginationUsesLiteralSearchAndStableProductOrder() throws Exception {
        String prefix = unique("NoBalance");
        String literal = prefix + "_%!";
        long first = product(literal + "A");
        long second = product(literal + "B");
        long third = product(literal + "C");
        long noise = product(prefix + "XYZ!noise");
        for (long product : List.of(first,second,third,noise))
            call(put("/api/v1/replenishment/policies/{id}",product),policy(3,8,null,true,null),200);
        // Imported catalogue rows may legitimately predate an inventory balance.
        jdbc.update("DELETE FROM inventory_balance WHERE product_id=?",first);
        assertThat(proposal(first).path("available").asLong()).isZero();
        assertThat(proposal(first).path("suggestedQuantity").asLong()).isEqualTo(8);
        for (String resource : List.of("policies","proposals")) {
            for (int page = 0; page < 3; page++) {
                var result = call(get("/api/v1/replenishment/"+resource)
                        .param("search",literal).param("size","1").param("page",Integer.toString(page)),null,200);
                assertThat(result.path("totalElements").asLong()).isEqualTo(3);
                assertThat(result.path("totalPages").asInt()).isEqualTo(3);
                assertThat(result.path("content").get(0).path("productId").asLong())
                        .isEqualTo(List.of(first,second,third).get(page));
            }
            assertThat(call(get("/api/v1/replenishment/"+resource)
                    .param("search",literal).param("size","1").param("page","3"),null,200)
                    .path("content").size()).isZero();
        }
    }

    @Test
    void inactiveAndMissingPreferredSuppliersAreVisibleWarningsWithoutAutomaticPurchases() throws Exception {
        long supplier = supplier();
        long product = product(unique("UnavailableSupplier"));
        long unassigned = product(unique("NoSupplier"));
        long newProduct = product(unique("NewAssignment"));
        call(put("/api/v1/replenishment/policies/{id}",product),policy(3,7,supplier,true,null),200);
        call(put("/api/v1/replenishment/policies/{id}",unassigned),policy(3,7,null,true,null),200);
        call(delete("/api/v1/suppliers/{id}",supplier),null,204);
        long purchasesBefore = jdbc.queryForObject("SELECT count(*) FROM purchase_order WHERE supplier_id=?",Long.class,supplier);
        JsonNode unavailable = proposal(product);
        assertThat(unavailable.path("supplierActive").asBoolean()).isFalse();
        assertThat(unavailable.path("purchasable").asBoolean()).isFalse();
        assertThat(unavailable.path("blockingReason").asText()).isEqualTo("SUPPLIER_INACTIVE");
        JsonNode noSupplier = proposal(unassigned);
        assertThat(noSupplier.path("supplierActive").isNull()).isTrue();
        assertThat(noSupplier.path("purchasable").asBoolean()).isFalse();
        assertThat(noSupplier.path("blockingReason").asText()).isEqualTo("NO_PREFERRED_SUPPLIER");
        assertThat(call(get("/api/v1/replenishment/proposals").param("preferredSupplierId",Long.toString(supplier)),
                null,200).path("totalElements").asLong()).isEqualTo(1);
        assertThat(call(put("/api/v1/replenishment/policies/{id}",newProduct),
                policy(3,7,supplier,true,null),409).path("code").asText()).isEqualTo("SUPPLIER_UNAVAILABLE");
        call(put("/api/v1/replenishment/policies/{id}",newProduct),policy(3,7,Long.MAX_VALUE,true,null),404);
        call(put("/api/v1/replenishment/policies/{id}",product),policy(4,8,supplier,false,0L),200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM purchase_order WHERE supplier_id=?",Long.class,supplier))
                .isEqualTo(purchasesBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM replenishment_policy WHERE product_id=?",Long.class,newProduct))
                .isZero();
    }

    @Test
    void readsAndWritesRespectRolesAndRejectInvalidPagination() throws Exception {
        long product = product(unique("Permissions"));
        bearer = "Bearer " + token("operations");
        call(put("/api/v1/replenishment/policies/{id}",product),policy(2,5,null,true,null),200);
        bearer = "Bearer " + token("readonly");
        call(get("/api/v1/replenishment/policies/{id}",product),null,200);
        call(get("/api/v1/replenishment/policies"),null,200);
        call(get("/api/v1/replenishment/proposals"),null,200);
        call(put("/api/v1/replenishment/policies/{id}",product),policy(3,8,null,true,0L),403);
        assertThat(jdbc.queryForObject("SELECT target_stock FROM replenishment_policy WHERE product_id=?",Long.class,product))
                .isEqualTo(5);
        call(get("/api/v1/replenishment/policies").param("size","101"),null,400);
        call(get("/api/v1/replenishment/proposals").param("page","-1"),null,400);
        call(get("/api/v1/replenishment/proposals").param("preferredSupplierId","0"),null,400);
        call(get("/api/v1/replenishment/policies/{id}",Long.MAX_VALUE),null,404);
        assertThat(mvc.perform(get("/api/v1/replenishment/proposals")).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void concurrentReplacementsWithOneVersionCannotLoseAnUpdate() throws Exception {
        long product = product(unique("ConcurrentPolicy"));
        call(put("/api/v1/replenishment/policies/{id}",product),policy(2,5,null,true,null),200);
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                barrier.await(5,TimeUnit.SECONDS);
                return mvc.perform(put("/api/v1/replenishment/policies/{id}",product)
                        .header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(policy(3,8,null,true,0L))))
                        .andReturn().getResponse().getStatus();
            });
            var second = executor.submit(() -> {
                barrier.await(5,TimeUnit.SECONDS);
                return mvc.perform(put("/api/v1/replenishment/policies/{id}",product)
                        .header("Authorization",bearer).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(policy(4,9,null,true,0L))))
                        .andReturn().getResponse().getStatus();
            });
            assertThat(List.of(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200,409);
        }
        JsonNode finalPolicy = call(get("/api/v1/replenishment/policies/{id}",product),null,200);
        assertThat(finalPolicy.path("version").asLong()).isEqualTo(1);
        assertThat(finalPolicy.path("targetStock").asLong()).isIn(8L,9L);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE entity_type='REPLENISHMENT_POLICY' AND entity_id=? AND action='REPLENISHMENT_POLICY_UPDATED'",
                Long.class,Long.toString(product))).isEqualTo(1);
    }

    private JsonNode proposals(long product) throws Exception {
        return call(get("/api/v1/replenishment/proposals").param("productId",Long.toString(product)),null,200);
    }

    private JsonNode proposal(long product) throws Exception {
        JsonNode result = proposals(product);
        assertThat(result.path("totalElements").asLong()).isEqualTo(1);
        return result.path("content").get(0);
    }

    private Map<String,Object> policy(long minimum, long target, Long supplier, boolean active, Long version) {
        Map<String,Object> body = new HashMap<>();
        body.put("minimumStock",minimum);
        body.put("targetStock",target);
        body.put("preferredSupplierId",supplier);
        body.put("active",active);
        body.put("version",version);
        return body;
    }

    private long product(String name) throws Exception {
        return call(post("/api/v1/products"),Map.of("sku",unique("REPL"),"name",name,"unitPrice",10),201)
                .path("id").asLong();
    }

    private long supplier() throws Exception {
        return call(post("/api/v1/suppliers"),Map.of("name",unique("Vendor"),"taxId",unique("V"),
                "email","replenishment@example.test","leadTimeDays",3),201).path("id").asLong();
    }

    private long purchase(long supplier, long product, long quantity, boolean submit) throws Exception {
        long id = call(post("/api/v1/purchase-orders"),Map.of("supplierId",supplier),201).path("id").asLong();
        call(put("/api/v1/purchase-orders/{id}/lines/{product}",id,product),Map.of("quantity",quantity,"unitCost",2),200);
        if (submit) call(post("/api/v1/purchase-orders/{id}/submit",id),null,200);
        return id;
    }

    private void receive(long purchase, long product, long quantity) throws Exception {
        call(post("/api/v1/purchase-orders/{id}/receipts",purchase),Map.of("requestId",UUID.randomUUID(),
                "deliveryReference",unique("DEL"),"lines",List.of(Map.of("productId",product,"quantity",quantity))),200);
    }

    private void reserve(long product, long quantity) throws Exception {
        long customer = call(post("/api/v1/customers"),Map.of("name",unique("Buyer"),"taxId",unique("B"),
                "email","buyer@example.test","address","Test address"),201).path("id").asLong();
        long order = call(post("/api/v1/orders"),Map.of("customerId",customer),201).path("id").asLong();
        call(put("/api/v1/orders/{id}/lines/{product}",order,product),Map.of("quantity",quantity),200);
        call(post("/api/v1/orders/{id}/confirm",order),null,200);
    }

    private JsonNode call(MockHttpServletRequestBuilder request, Object body, int expectedStatus) throws Exception {
        request.header("Authorization",bearer);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).withFailMessage("HTTP %s: %s",response.getStatus(),response.getContentAsString())
                .isEqualTo(expectedStatus);
        return response.getContentAsString().isBlank() ? mapper.nullNode() : json(response.getContentAsString());
    }
}
