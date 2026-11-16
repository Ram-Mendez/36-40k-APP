package com.acme.operations.batch16;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.operations.common.RequestLoggingFilter;
import com.acme.operations.shipping.ShippingClient;
import com.sun.net.httpserver.HttpServer;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Runs a real local HTTP exchange through the production filter and shipping client. */
class Batch16TracePropagationTest {
    private HttpServer carrier;
    private ShippingClient client;
    private final AtomicReference<String> receivedRequestId = new AtomicReference<>();
    private final AtomicReference<String> receivedQuery = new AtomicReference<>();

    @BeforeEach
    void startCarrier() throws Exception {
        carrier = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        carrier.createContext("/quotes", exchange -> {
            receivedRequestId.set(exchange.getRequestHeaders().getFirst("X-Request-ID"));
            receivedQuery.set(exchange.getRequestURI().getRawQuery());
            byte[] body = "{\"reference\":\"BATCH16-QUOTE\",\"cost\":7.50}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            try (var output = exchange.getResponseBody()) { output.write(body); }
        });
        carrier.start();
        client = new ShippingClient("http://127.0.0.1:" + carrier.getAddress().getPort(),
                Duration.ofSeconds(2), Duration.ofSeconds(2));
    }

    @AfterEach
    void stopCarrier() {
        if (carrier != null) carrier.stop(0);
        MDC.clear();
    }

    @Test
    void callerIdReachesTheRemoteServiceUnchanged() throws Exception {
        var response = quoteThroughFilter("batch16-order-42");
        assertThat(response.getHeader("X-Request-ID")).isEqualTo("batch16-order-42");
        assertThat(receivedRequestId.get()).isEqualTo("batch16-order-42");
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    void generatedIdAlsoReachesTheRemoteService() throws Exception {
        var response = quoteThroughFilter(null);
        assertThat(response.getHeader("X-Request-ID")).isNotBlank();
        assertThat(receivedRequestId.get()).isEqualTo(response.getHeader("X-Request-ID"));
    }

    @Test
    void quoteDataAndDecimalAmountAreUnchanged() throws Exception {
        quoteThroughFilter("batch16-control");
        assertThat(receivedQuery.get().split("&")).contains("orderId=42", "amount=19.90");
    }

    private MockHttpServletResponse quoteThroughFilter(String requestId) throws Exception {
        var request = new MockHttpServletRequest("POST", "/api/v1/orders/42/complete");
        if (requestId != null) request.addHeader("X-Request-ID", requestId);
        var response = new MockHttpServletResponse();
        new RequestLoggingFilter().doFilter(request, response, (incoming, outgoing) -> {
            var quote = client.quote(42, new BigDecimal("19.90"));
            assertThat(quote.reference()).isEqualTo("BATCH16-QUOTE");
            assertThat(quote.cost()).isEqualByComparingTo("7.50");
        });
        return response;
    }
}
