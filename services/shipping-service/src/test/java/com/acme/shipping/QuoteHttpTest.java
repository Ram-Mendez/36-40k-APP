package com.acme.shipping;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class QuoteHttpTest {
    @Autowired private TestRestTemplate http;

    @Test
    void repeatedQuotationIsDeterministicAndPreservesTheWarehouseContract() {
        var headers = new HttpHeaders();
        headers.set("X-Request-ID", "warehouse-order-42");
        var first =
                http.exchange(
                        "/quotes?orderId=42&amount=199.99",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        JsonNode.class);
        var repeated = http.getForEntity("/quotes?orderId=42&amount=199.99", JsonNode.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(first.getHeaders().getFirst("X-Request-ID")).isEqualTo("warehouse-order-42");
        assertThat(first.getBody().size()).isEqualTo(2);
        assertThat(first.getBody().path("reference").asText()).isEqualTo("QUOTE-42-199.99");
        assertThat(first.getBody().path("cost").decimalValue()).isEqualByComparingTo("7.50");
        assertThat(repeated.getBody()).isEqualTo(first.getBody());
    }

    @Test
    void amountAtTheFreeShippingThresholdReturnsZeroCost() {
        var response = http.getForEntity("/quotes?orderId=42&amount=200.00", JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("cost").decimalValue()).isEqualByComparingTo("0.00");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "orderId=0&amount=10",
                "orderId=-1&amount=10",
                "orderId=invalid&amount=10",
                "orderId=1&amount=-0.01",
                "orderId=1&amount=1.001",
                "orderId=1&amount=1000000000000.00",
                "orderId=1&amount=NaN",
                "orderId=1",
                "amount=10"
            })
    void invalidInputReturnsBadRequestProblemDetail(String query) {
        var response = http.getForEntity("/quotes?" + query, JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        assertThat(response.getBody().path("status").asInt()).isEqualTo(400);
        assertThat(response.getBody().path("title").asText()).isNotBlank();
        assertThat(response.getBody().path("instance").asText()).isEqualTo("/quotes");
        assertThat(response.getHeaders().getFirst("X-Request-ID")).isNotBlank();
    }

    @Test
    void replacesAnUnsafeRequestIdBeforeLoggingOrEchoingIt() {
        var headers = new HttpHeaders();
        headers.set("X-Request-ID", "invalid!request!id");
        var response =
                http.exchange(
                        "/quotes?orderId=42&amount=10.00",
                        HttpMethod.GET,
                        new HttpEntity<>(headers),
                        JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("X-Request-ID"))
                .matches("[A-Za-z0-9_-]{1,64}")
                .isNotEqualTo("invalid!request!id");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator/health/liveness", "/actuator/health/readiness"})
    void exposesIndependentHealthProbes(String path) {
        var response = http.getForEntity(path, JsonNode.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().path("status").asText()).isEqualTo("UP");
    }
}
