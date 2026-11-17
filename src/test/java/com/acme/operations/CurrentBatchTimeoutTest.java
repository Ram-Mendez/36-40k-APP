package com.acme.operations;

import static org.assertj.core.api.Assertions.assertThat;
import com.acme.operations.common.BusinessException;
import com.acme.operations.shipping.ShippingClient;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class CurrentBatchTimeoutTest {
    @Test
    void readTimeoutDoesNotInheritTheConnectionBudget() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/quotes", exchange -> {
            entered.countDown();
            try { release.await(8, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            byte[] body = "{\"reference\":\"SLOW-QUOTE\",\"cost\":7.50}".getBytes(StandardCharsets.UTF_8);
            try {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (var output = exchange.getResponseBody()) { output.write(body); }
            } catch (java.io.IOException ignored) { /* Timed-out clients close their connections. */ }
        });
        server.start();
        var executor = Executors.newSingleThreadExecutor();
        try {
            var client = new ShippingClient("http://127.0.0.1:" + server.getAddress().getPort(),
                    Duration.ofSeconds(5), Duration.ofMillis(250));
            var result = executor.submit(() -> {
                try { client.quote(501, BigDecimal.TEN); return "UNEXPECTED_SUCCESS"; }
                catch (BusinessException e) { return e.getCode(); }
            });
            assertThat(entered.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(result.get(1500, TimeUnit.MILLISECONDS)).isEqualTo("SHIPPING_UNAVAILABLE");
            assertThat(release.getCount()).isEqualTo(1);
        } finally {
            release.countDown();
            executor.shutdownNow();
            server.stop(0);
        }
    }
}
