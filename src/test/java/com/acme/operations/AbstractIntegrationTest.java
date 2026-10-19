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
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.13-alpine");
    protected static final AtomicInteger CARRIER_STATUS = new AtomicInteger(200);
    protected static final AtomicInteger CARRIER_CALLS = new AtomicInteger();
    protected static final AtomicReference<CountDownLatch> CARRIER_ENTERED =
            new AtomicReference<>();
    protected static final AtomicReference<CountDownLatch> CARRIER_RELEASE =
            new AtomicReference<>();
    protected static final HttpServer CARRIER;

    static {
        POSTGRES.start();
        try {
            CARRIER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            CARRIER.createContext(
                    "/",
                    exchange -> {
                        CARRIER_CALLS.incrementAndGet();
                        exchange.getRequestBody().readAllBytes();
                        CountDownLatch entered = CARRIER_ENTERED.get();
                        CountDownLatch release = CARRIER_RELEASE.get();
                        if (entered != null && release != null) {
                            entered.countDown();
                            try {
                                release.await(10, TimeUnit.SECONDS);
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                            }
                        }
                        byte[] response =
                                (CARRIER_STATUS.get() == 200
                                                ? "{\"reference\":\"TEST-TRACKING\",\"cost\":12.50}"
                                                : "{\"error\":\"Carrier unavailable\"}")
                                        .getBytes(StandardCharsets.UTF_8);
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(CARRIER_STATUS.get(), response.length);
                        try (var body = exchange.getResponseBody()) {
                            body.write(response);
                        }
                    });
            CARRIER.start();
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add(
                "shipping.base-url", () -> "http://127.0.0.1:" + CARRIER.getAddress().getPort());
    }

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected JdbcTemplate jdbc;

    protected String token(String username) throws Exception {
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
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("accessToken")
                .asText();
    }

    protected JsonNode json(String body) throws IOException {
        return mapper.readTree(body);
    }

    protected String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
