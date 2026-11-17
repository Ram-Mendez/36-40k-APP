package com.acme.operations;

import static org.assertj.core.api.Assertions.*;
import com.acme.operations.shipping.ShippingClient;
import com.acme.operations.common.BusinessException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.math.BigDecimal;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class Batch17RecoveryTest {
    @Test
    void sameClientRecoversWithoutApplicationRestart() throws Exception {
        var status = new AtomicInteger(503);
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/quotes", exchange -> {
            calls.incrementAndGet();
            byte[] body = "{\"reference\":\"RECOVERED\",\"cost\":7.50}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status.get(), body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try {
            var client = new ShippingClient("http://127.0.0.1:" + server.getAddress().getPort(),
                    Duration.ofSeconds(2), Duration.ofSeconds(2));
            assertThatThrownBy(() -> client.quote(801, BigDecimal.TEN)).isInstanceOf(BusinessException.class);
            status.set(200);
            assertThat(client.quote(801, BigDecimal.TEN).cost()).isEqualByComparingTo("7.50");
            assertThat(calls.get()).isEqualTo(2);
        } finally { server.stop(0); }
    }
}
