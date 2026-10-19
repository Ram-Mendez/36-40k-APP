package com.acme.operations;

import static org.assertj.core.api.Assertions.*;

import com.acme.operations.common.BusinessException;
import com.acme.operations.shipping.ShippingClient;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

class ShippingClientTest {
    private HttpServer server;

    private ShippingClient client(int status, String response, long delay) throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/quotes",
                exchange -> {
                    try {
                        if (delay > 0) Thread.sleep(delay);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status, bytes.length);
                    try (var output = exchange.getResponseBody()) {
                        output.write(bytes);
                    }
                });
        server.start();
        return new ShippingClient(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                Duration.ofSeconds(1),
                Duration.ofMillis(200));
    }

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void validQuotePreservesReferenceAndDecimalCost() throws Exception {
        var quote =
                client(200, "{\"reference\":\"C-123\",\"cost\":7.50}", 0).quote(1, BigDecimal.TEN);
        assertThat(quote.reference()).isEqualTo("C-123");
        assertThat(quote.cost()).isEqualByComparingTo("7.50");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "not-json",
                "{\"reference\":\"x\",\"cost\":-1}",
                "{\"reference\":\"\",\"cost\":2}",
                "{\"reference\":\"x\",\"cost\":0.001}"
            })
    void malformedOrInvalidQuotesBecomeBusinessUnavailable(String body) throws Exception {
        var client = client(200, body, 0);
        assertThatThrownBy(() -> client.quote(1, BigDecimal.TEN))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void httpFailureBecomesBusinessUnavailable() throws Exception {
        var client = client(503, "{\"error\":\"maintenance\"}", 0);
        assertThatThrownBy(() -> client.quote(1, BigDecimal.TEN))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void slowCarrierIsBoundedByReadTimeout() throws Exception {
        var client = client(200, "{\"reference\":\"late\",\"cost\":2}", 1000);
        long start = System.nanoTime();
        assertThatThrownBy(() -> client.quote(1, BigDecimal.TEN))
                .isInstanceOf(BusinessException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    }
}
