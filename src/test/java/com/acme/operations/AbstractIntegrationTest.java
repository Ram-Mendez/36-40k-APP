package com.acme.operations;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** One real PostgreSQL instance per test JVM; Ryuk cleans it after the suite. */
// Spring starts the full application context for the inherited tests of this class.
@SpringBootTest
// Prepare MockMvc to pass requests through the drivers and filters without opening an HTTP port of the
// application.
@AutoConfigureMockMvc
// It activates the test configuration, including development data and Swagger.
@ActiveProfiles("test")
// It brings together infrastructure and shared functions; this class alone is not a case of proof.
public abstract class AbstractIntegrationTest {
    // It allows to use a dedicated PostgreSQL of tests when Docker is not available. If not configured,
    // Testcontainers are maintained as default infrastructure.
    private static final String EXTERNAL_DATABASE_URL = System.getProperty("test.database.url", "");
    // Testcontainers will create a real and isolated PostgreSQL using this image; it needs Docker.
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.13-alpine");
    // Check which state returns the simulated carrier; each test can change it.
    protected static final AtomicInteger CARRIER_STATUS = new AtomicInteger(200);
    // He tells the carrier the calls so he can check if an operation consulted him.
    protected static final AtomicInteger CARRIER_CALLS = new AtomicInteger();
    // These references allow for the coordination of tests that will pause an HTTP call in another thread.
    protected static final AtomicReference<CountDownLatch> CARRIER_ENTERED =
            new AtomicReference<>();
    protected static final AtomicReference<CountDownLatch> CARRIER_RELEASE =
            new AtomicReference<>();
    // Local HTTP test server that replaces the external transport service.
    protected static final HttpServer CARRIER;

    // The static block prepares the shared infrastructure before running the tests.
    static {
        // Start the actual container and wait for the database to be available.
        if (EXTERNAL_DATABASE_URL.isBlank()) POSTGRES.start();
        // If the local server cannot boot, the test class does not initialize.
        try {
            // The port 0 asks the system a port available so as not to fix a specific one.
            CARRIER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            // Install a handler that responds to requests received by the simulated carrier.
            CARRIER.createContext(
                    "/",
                    exchange -> {
                        // Record the call and consume the body before building the test response.
                        CARRIER_CALLS.incrementAndGet();
                        exchange.getRequestBody().readAllBytes();
                        // The latch let you know when a request came in and decide when to let it go.
                        CountDownLatch entered = CARRIER_ENTERED.get();
                        CountDownLatch release = CARRIER_RELEASE.get();
                        // Only the carrier stops when a test has prepared both coordination mechanisms.
                        if (entered != null && release != null) {
                            // Notice to the test thread that the external call has already started.
                            entered.countDown();
                            // Wait no more than 10 seconds to avoid blocking the test indefinitely.
                            try {
                                release.await(10, TimeUnit.SECONDS);
                            // Restore the thread interruption signal if the waiting is interrupted.
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                            }
                        }
                        // With 200 returns a fixed quote; with another state returns an error message.
                        byte[] response =
                                (CARRIER_STATUS.get() == 200
                                                ? "{\"reference\":\"TEST-TRACKING\",\"cost\":12.50}"
                                                : "{\"error\":\"Carrier unavailable\"}")
                                        .getBytes(StandardCharsets.UTF_8);
                        // Send the configured state and simulated JSON to the HTTP client of the application.
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(CARRIER_STATUS.get(), response.length);
                        // Close the response flow after writing the bytes.
                        try (var body = exchange.getResponseBody()) {
                            body.write(response);
                        }
                    });
            // Start accepting requests at the assigned port.
            CARRIER.start();
        // Propagates the boot failure as initialization error, not as a successful test.
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    // Spring uses these dynamic values when building the test context.
    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry properties) {
        // The test application is connected to the container, not to the user's local database.
        if (EXTERNAL_DATABASE_URL.isBlank()) {
            properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
            properties.add("spring.datasource.username", POSTGRES::getUsername);
            properties.add("spring.datasource.password", POSTGRES::getPassword);
        } else {
            properties.add("spring.datasource.url", () -> EXTERNAL_DATABASE_URL);
            properties.add("spring.datasource.username", () -> System.getProperty("test.database.username", "business"));
            properties.add("spring.datasource.password", () -> System.getProperty("test.database.password", ""));
        }
        // Point shipping to the simulated server that just started.
        properties.add(
                "shipping.base-url", () -> "http://127.0.0.1:" + CARRIER.getAddress().getPort());
    }

    // MockMvc runs requests against Spring and allows you to inspect your response.
    @Autowired protected MockMvc mvc;
    // ObjectMapper transforms Java objects to JSON and reads the returned bodies.
    @Autowired protected ObjectMapper mapper;
    // JdbcTemplate allows to directly check the continued status in PostgreSQL.
    @Autowired protected JdbcTemplate jdbc;

    // Login with a user of the test data and return your access token.
    protected String token(String username) throws Exception {
        // Send credentials to the actual login endpoint within MockMvc.
        return json(mvc.perform(
                                post("/api/v1/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                mapper.writeValueAsBytes(
                                                        Map.of(
                                                                "username",
                                                                username,
                                                                "password",
                                                                "DevPassword123!"))))
                        // It requires response 200 before trying to read the token; if it fails, the test stops.
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                // Read accessToken from the JSON response and use it as a Bearer token.
                .path("accessToken")
                .asText();
    }

    // Convert the JSON text from a response to a tree that tests can consult.
    protected JsonNode json(String body) throws IOException {
        return mapper.readTree(body);
    }

    // It generates a random suffix value to avoid collisions between test data.
    protected String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
