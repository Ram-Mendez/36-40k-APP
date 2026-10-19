import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

/** Local carrier contract simulator. GET quotes has no remote side effects. */
public class CarrierSimulator {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(9096), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext(
                "/health",
                exchange -> {
                    byte[] body = "{\"status\":\"UP\"}".getBytes(StandardCharsets.UTF_8);
                    exchange.sendResponseHeaders(200, body.length);
                    try (var out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                });
        server.createContext(
                "/quotes",
                exchange -> {
                    String mode = System.getenv().getOrDefault("CARRIER_MODE", "normal");
                    if (mode.equals("slow")) {
                        try {
                            Thread.sleep(5000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                    String query = exchange.getRequestURI().getRawQuery();
                    String orderId =
                            query == null
                                    ? "unknown"
                                    : java.util.Arrays.stream(query.split("&"))
                                            .filter(p -> p.startsWith("orderId="))
                                            .map(p -> p.substring(8))
                                            .findFirst()
                                            .orElse("unknown");
                    if (!orderId.matches("[0-9]+")) orderId = "unknown";
                    int status = mode.equals("unavailable") ? 503 : 200;
                    String json =
                            mode.equals("malformed")
                                    ? "{invalid-json"
                                    : status == 503
                                            ? "{\"error\":\"temporarily unavailable\"}"
                                            : "{\"reference\":\"QUOTE-"
                                                    + orderId
                                                    + "\",\"cost\":7.50}";
                    byte[] body = json.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(status, body.length);
                    System.out.println(
                            "carrier method="
                                    + exchange.getRequestMethod()
                                    + " orderId="
                                    + orderId
                                    + " status="
                                    + status
                                    + " requestId="
                                    + exchange.getRequestHeaders().getFirst("X-Request-ID"));
                    try (var out = exchange.getResponseBody()) {
                        out.write(body);
                    }
                });
        server.start();
        System.out.println("Carrier simulator listening on 9096");
    }
}
