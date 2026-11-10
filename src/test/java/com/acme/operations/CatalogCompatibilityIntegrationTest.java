package com.acme.operations;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class CatalogCompatibilityIntegrationTest extends AbstractIntegrationTest {
    @Test
    void integratedCatalogRetainsBothSkuAndActiveOrderingContracts() throws Exception {
        String bearer = "Bearer " + token("admin");
        String prefix = unique("SORT").toUpperCase(java.util.Locale.ROOT);
        for (String suffix : List.of("C", "A", "B")) {
            JsonNode product = call(post("/api/v1/products"),
                    Map.of("sku", prefix + suffix, "name", prefix + suffix, "unitPrice", 10), bearer, 201);
            if (suffix.equals("A")) call(delete("/api/v1/products/{id}", product.path("id").asLong()), null, bearer, 204);
        }
        JsonNode sku = call(get("/api/v1/products").param("search", prefix)
                .param("sort", "sku").param("direction", "asc"), null, bearer, 200);
        var found = new ArrayList<String>();
        sku.path("content").forEach(row -> found.add(row.path("sku").asText()));
        assertThat(found).containsExactly(prefix + "A", prefix + "B", prefix + "C");
        JsonNode active = call(get("/api/v1/products").param("search", prefix)
                .param("sort", "active").param("direction", "asc").param("size", "1"), null, bearer, 200);
        assertThat(active.path("totalElements").asInt()).isEqualTo(3);
        assertThat(active.path("content").get(0).path("sku").asText()).isEqualTo(prefix + "A");
        JsonNode filtered = call(get("/api/v1/products").param("search", prefix)
                .param("active", "true").param("sort", "sku").param("direction", "desc"), null, bearer, 200);
        assertThat(filtered.path("totalElements").asInt()).isEqualTo(2);
        assertThat(filtered.path("content").get(0).path("sku").asText()).isEqualTo(prefix + "C");
        call(get("/api/v1/products").param("sort", "unknown"), null, bearer, 400);
    }

    private JsonNode call(MockHttpServletRequestBuilder request, Object body, String bearer, int expected) throws Exception {
        request.header("Authorization", bearer);
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
        var response = mvc.perform(request).andReturn().getResponse();
        assertThat(response.getStatus()).withFailMessage(response.getContentAsString()).isEqualTo(expected);
        return response.getContentAsString().isBlank() ? mapper.nullNode() : json(response.getContentAsString());
    }
}
