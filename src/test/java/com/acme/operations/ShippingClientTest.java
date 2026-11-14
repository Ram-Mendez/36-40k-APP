package com.acme.operations;

import static org.assertj.core.api.Assertions.*;

import com.acme.operations.common.BusinessException;
import com.acme.operations.shipping.ShippingClient;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

// Try the client with a small local HTTP server, not the actual carrier.
class ShippingClientTest {
    // Reference to the test server to be able to stop it at the end of each case.
    private HttpServer server;
    private final AtomicReference<String> requestId = new AtomicReference<>();
    private final AtomicReference<String> query = new AtomicReference<>();

    // Build a server that will return the state, body and delay indicated by the test.
    private ShippingClient client(int status, String response, long delay) throws Exception {
        // The system chooses a local port available when using port 0.
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // It simulates the endpoint / quotes that you consult ShippingClient.
        server.createContext(
                "/quotes",
                exchange -> {
                    requestId.set(exchange.getRequestHeaders().getFirst("X-Request-ID"));
                    query.set(exchange.getRequestURI().getRawQuery());
                    // The delay allows a timeout to be triggered; the server is only waiting if requested.
                    try {
                        if (delay > 0) Thread.sleep(delay);
                    // If interrupted, keep the thread interruption signal.
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    // Convert the prepared response to UTF-8 bytes for HTTP.
                    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                    // The client will receive JSON with the status code indicated for each case.
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status, bytes.length);
                    // Write and close the body of the answer.
                    try (var output = exchange.getResponseBody()) {
                        output.write(bytes);
                    }
                });
        // Start the simulator and create the client by pointing to the assigned port.
        server.start();
        return new ShippingClient(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                // Set up a second to connect and 200 milliseconds to wait for reading.
                Duration.ofSeconds(1),
                Duration.ofMillis(200));
    }

    // JUnit runs this cleaning after each test, even if it fails.
    @AfterEach
    void stopServer() {
        // It stops the server only if it came to be created.
        if (server != null) server.stop(0);
    }

    // A valid contribution must keep the reference and the decimal amount.
    @Test
    void validQuotePreservesReferenceAndDecimalCost() throws Exception {
        // It responds with 200 and a known quote; quote makes the call to the local server.
        var quote =
                client(200, "{\"reference\":\"C-123\",\"cost\":7.50}", 0).quote(1, BigDecimal.TEN);
        // Check the values returned using decimal comparison for cost.
        assertThat(quote.reference()).isEqualTo("C-123");
        assertThat(quote.cost()).isEqualByComparingTo("7.50");
        assertThat(query.get()).contains("orderId=1", "amount=10");
    }

    @ParameterizedTest
    @ValueSource(strings = {"199.60", "200.00"})
    void sendsExactOrderAmountToShipping(String amount) throws Exception {
        var client = client(200, "{\"reference\":\"C-123\",\"cost\":7.50}", 0);

        client.quote(4, new BigDecimal(amount));

        assertThat(query.get().split("&")).contains("amount=" + amount);
    }

    @Test
    void forwardsRequestId() throws Exception {
        var client = client(200, "{\"reference\":\"C-123\",\"cost\":7.50}", 0);
        MDC.put("requestId", "request-123");
        try {
            client.quote(42, BigDecimal.TEN);
            assertThat(requestId.get()).isEqualTo("request-123");
            assertThat(query.get()).contains("orderId=42", "amount=10");
        } finally {
            MDC.remove("requestId");
        }
    }

    // Repeat the case for JSON absent, ill-formed or with fields outside the rules.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "not-json",
                "{\"reference\":\"x\",\"cost\":-1}",
                "{\"reference\":\"\",\"cost\":2}",
                "{\"reference\":\"x\",\"cost\":0.001}"
            })
    // Check that each invalid response is translated to BusinessException.
    void malformedOrInvalidQuotesBecomeBusinessUnavailable(String body) throws Exception {
        var client = client(200, body, 0);
        assertThatThrownBy(() -> client.quote(1, BigDecimal.TEN))
                .isInstanceOf(BusinessException.class);
    }

    // An HTTP 503 response from the carrier must be translated into business error.
    @Test
    void httpFailureBecomesBusinessUnavailable() throws Exception {
        var client = client(503, "{\"error\":\"maintenance\"}", 0);
        assertThatThrownBy(() -> client.quote(1, BigDecimal.TEN))
                .isInstanceOf(BusinessException.class);
    }

    // The client should not wait for the full second that the server takes.
    @Test
    void slowCarrierIsBoundedByReadTimeout() throws Exception {
        // It simulates a valid but too slow response for the 200 m reading timeout.
        var client = client(200, "{\"reference\":\"late\",\"cost\":2}", 1000);
        // It measures the time spent with a watch suitable for durations.
        long start = System.nanoTime();
        // Demand business error and check that the call ends within two seconds.
        assertThatThrownBy(() -> client.quote(1, BigDecimal.TEN))
                .isInstanceOf(BusinessException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    }
}
