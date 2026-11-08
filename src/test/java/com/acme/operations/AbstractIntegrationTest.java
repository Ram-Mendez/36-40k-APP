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
// Spring arranca el contexto completo de la aplicación para las pruebas que hereden de esta clase.
@SpringBootTest
// Prepara MockMvc para pasar peticiones por los controladores y filtros sin abrir un puerto HTTP de la
// aplicación.
@AutoConfigureMockMvc
// Activa la configuración de test, incluidos los datos de desarrollo y Swagger.
@ActiveProfiles("test")
// Reúne infraestructura y funciones compartidas; esta clase por sí sola no es un caso de prueba.
public abstract class AbstractIntegrationTest {
    // Testcontainers creará un PostgreSQL real y aislado usando esta imagen; necesita Docker.
    protected static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16.13-alpine");
    // Controla qué estado devuelve el transportista simulado; cada prueba puede cambiarlo.
    protected static final AtomicInteger CARRIER_STATUS = new AtomicInteger(200);
    // Cuenta las llamadas al transportista para poder comprobar si una operación lo consultó.
    protected static final AtomicInteger CARRIER_CALLS = new AtomicInteger();
    // Estas referencias permiten coordinar pruebas que pausarán una llamada HTTP en otro hilo.
    protected static final AtomicReference<CountDownLatch> CARRIER_ENTERED =
            new AtomicReference<>();
    protected static final AtomicReference<CountDownLatch> CARRIER_RELEASE =
            new AtomicReference<>();
    // Servidor HTTP local de prueba que sustituye al servicio externo de transporte.
    protected static final HttpServer CARRIER;

    // El bloque estático prepara la infraestructura compartida antes de ejecutar las pruebas.
    static {
        // Arranca el contenedor real y espera a que la base de datos esté disponible.
        POSTGRES.start();
        // Si el servidor local no puede arrancar, la clase de pruebas no se inicializa.
        try {
            // El puerto 0 pide al sistema un puerto disponible para no fijar uno concreto.
            CARRIER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            // Instala un manejador que responde a las peticiones que reciba el transportista simulado.
            CARRIER.createContext(
                    "/",
                    exchange -> {
                        // Registra la llamada y consume el cuerpo antes de construir la respuesta de prueba.
                        CARRIER_CALLS.incrementAndGet();
                        exchange.getRequestBody().readAllBytes();
                        // Los latch permiten saber cuándo entró una petición y decidir cuándo dejarla continuar.
                        CountDownLatch entered = CARRIER_ENTERED.get();
                        CountDownLatch release = CARRIER_RELEASE.get();
                        // Solo pausa el transportista cuando una prueba ha preparado ambos mecanismos de coordinación.
                        if (entered != null && release != null) {
                            // Avisa al hilo de prueba de que la llamada externa ya ha comenzado.
                            entered.countDown();
                            // Espera como máximo 10 segundos para evitar bloquear la prueba indefinidamente.
                            try {
                                release.await(10, TimeUnit.SECONDS);
                            // Restaura la señal de interrupción del hilo si se interrumpe la espera.
                            } catch (InterruptedException exception) {
                                Thread.currentThread().interrupt();
                            }
                        }
                        // Con 200 devuelve una cotización fija; con otro estado devuelve un mensaje de error.
                        byte[] response =
                                (CARRIER_STATUS.get() == 200
                                                ? "{\"reference\":\"TEST-TRACKING\",\"cost\":12.50}"
                                                : "{\"error\":\"Carrier unavailable\"}")
                                        .getBytes(StandardCharsets.UTF_8);
                        // Envía el estado configurado y el JSON simulado al cliente HTTP de la aplicación.
                        exchange.getResponseHeaders().add("Content-Type", "application/json");
                        exchange.sendResponseHeaders(CARRIER_STATUS.get(), response.length);
                        // Cierra el flujo de respuesta después de escribir los bytes.
                        try (var body = exchange.getResponseBody()) {
                            body.write(response);
                        }
                    });
            // Empieza a aceptar peticiones en el puerto asignado.
            CARRIER.start();
        // Propaga el fallo de arranque como error de inicialización, no como prueba exitosa.
        } catch (IOException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    // Spring usa estos valores dinámicos al construir el contexto de las pruebas.
    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry properties) {
        // La aplicación de prueba se conecta al contenedor, no a la base de datos local del usuario.
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        // Apunta shipping al servidor simulado que acaba de arrancar.
        properties.add(
                "shipping.base-url", () -> "http://127.0.0.1:" + CARRIER.getAddress().getPort());
    }

    // MockMvc ejecuta peticiones contra Spring y permite inspeccionar su respuesta.
    @Autowired protected MockMvc mvc;
    // ObjectMapper transforma objetos Java a JSON y lee los cuerpos devueltos.
    @Autowired protected ObjectMapper mapper;
    // JdbcTemplate permite comprobar directamente el estado persistido en PostgreSQL.
    @Autowired protected JdbcTemplate jdbc;

    // Hace login con un usuario de los datos de prueba y devuelve su access token.
    protected String token(String username) throws Exception {
        // Envía credenciales por el endpoint real de login dentro de MockMvc.
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
                        // Exige respuesta 200 antes de intentar leer el token; si falla, la prueba se detiene.
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                // Extrae accessToken del JSON para usarlo como Bearer en otras peticiones.
                .path("accessToken")
                .asText();
    }

    // Convierte el texto JSON de una respuesta en un árbol que las pruebas pueden consultar.
    protected JsonNode json(String body) throws IOException {
        return mapper.readTree(body);
    }

    // Genera un valor con sufijo aleatorio para evitar colisiones entre datos de prueba.
    protected String unique(String prefix) {
        return prefix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
