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

// Prueba el cliente con un pequeño servidor HTTP local, no con el transportista real.
class ShippingClientTest {
    // Referencia al servidor de prueba para poder detenerlo al terminar cada caso.
    private HttpServer server;
    private final AtomicReference<String> requestId = new AtomicReference<>();
    private final AtomicReference<String> query = new AtomicReference<>();

    // Construye un servidor que devolverá el estado, cuerpo y demora indicados por el test.
    private ShippingClient client(int status, String response, long delay) throws Exception {
        // El sistema elige un puerto local disponible al usar el puerto 0.
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // Simula el endpoint /quotes que consulta ShippingClient.
        server.createContext(
                "/quotes",
                exchange -> {
                    requestId.set(exchange.getRequestHeaders().getFirst("X-Request-ID"));
                    query.set(exchange.getRequestURI().getRawQuery());
                    // La demora permite provocar un timeout; el servidor espera solo si se le solicita.
                    try {
                        if (delay > 0) Thread.sleep(delay);
                    // Si se interrumpe, conserva la señal de interrupción del hilo.
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                    // Convierte la respuesta preparada a bytes UTF-8 para enviarla por HTTP.
                    byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                    // El cliente recibirá JSON con el código de estado indicado por cada caso.
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status, bytes.length);
                    // Escribe y cierra el cuerpo de la respuesta.
                    try (var output = exchange.getResponseBody()) {
                        output.write(bytes);
                    }
                });
        // Arranca el simulador y crea el cliente apuntando al puerto asignado.
        server.start();
        return new ShippingClient(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                // Configura un segundo para conectar y 200 milisegundos para esperar la lectura.
                Duration.ofSeconds(1),
                Duration.ofMillis(200));
    }

    // JUnit ejecuta esta limpieza después de cada prueba, incluso si falla.
    @AfterEach
    void stopServer() {
        // Detiene el servidor solo si llegó a crearse.
        if (server != null) server.stop(0);
    }

    // Una cotización válida debe conservar la referencia y el importe decimal.
    @Test
    void validQuotePreservesReferenceAndDecimalCost() throws Exception {
        // Responde con 200 y una cotización conocida; quote realiza la llamada al servidor local.
        var quote =
                client(200, "{\"reference\":\"C-123\",\"cost\":7.50}", 0).quote(1, BigDecimal.TEN);
        // Comprueba los valores devueltos usando comparación decimal para el coste.
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

    // Repite el caso para JSON ausente, mal formado o con campos fuera de las reglas.
    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "not-json",
                "{\"reference\":\"x\",\"cost\":-1}",
                "{\"reference\":\"\",\"cost\":2}",
                "{\"reference\":\"x\",\"cost\":0.001}"
            })
    // Comprueba que cada respuesta inválida se traduzca a BusinessException.
    void malformedOrInvalidQuotesBecomeBusinessUnavailable(String body) throws Exception {
        var client = client(200, body, 0);
        assertThatThrownBy(() -> client.quote(1, BigDecimal.TEN))
                .isInstanceOf(BusinessException.class);
    }

    // Una respuesta HTTP 503 del transportista debe traducirse a error de negocio.
    @Test
    void httpFailureBecomesBusinessUnavailable() throws Exception {
        var client = client(503, "{\"error\":\"maintenance\"}", 0);
        assertThatThrownBy(() -> client.quote(1, BigDecimal.TEN))
                .isInstanceOf(BusinessException.class);
    }

    // El cliente no debe esperar el segundo completo que tarda el servidor.
    @Test
    void slowCarrierIsBoundedByReadTimeout() throws Exception {
        // Simula una respuesta válida pero demasiado lenta para el timeout de lectura de 200 ms.
        var client = client(200, "{\"reference\":\"late\",\"cost\":2}", 1000);
        // Mide el tiempo transcurrido con un reloj apropiado para duraciones.
        long start = System.nanoTime();
        // Exige el error de negocio y comprueba que la llamada termine antes de dos segundos.
        assertThatThrownBy(() -> client.quote(1, BigDecimal.TEN))
                .isInstanceOf(BusinessException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
    }
}
