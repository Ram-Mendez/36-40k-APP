package com.acme.operations.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.acme.operations.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

// Prueba autenticación y permisos con Spring y la base PostgreSQL de integración.
class SecurityIntegrationTest extends AbstractIntegrationTest {
    // Usa el firmador y verificador configurados por la aplicación para fabricar JWT de prueba.
    @Autowired JwtEncoder encoder;
    @Autowired JwtDecoder decoder;

    // Usuario inexistente y contraseña incorrecta deben recibir el mismo rechazo 401.
    @Test
    void wrongPasswordAndUnknownUserHaveSameUnauthorizedResponse() throws Exception {
        // Repite el mismo intento con admin y con un nombre que no existe.
        for (String username : new String[] {"admin", "nonexistent"}) {
            // Envía una contraseña incorrecta por el endpoint real de login.
            mvc.perform(
                            post("/api/v1/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(
                                            mapper.writeValueAsBytes(
                                                    Map.of(
                                                            "username",
                                                            username,
                                                            "password",
                                                            "wrong-password"))))
                    // Comprueba el estado y el mismo código INVALID_CREDENTIALS en ambos casos.
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
    }

    // Distingue no estar autenticado de estar autenticado sin permiso.
    @Test
    void authenticationAndOperationPermissionAreSeparate() throws Exception {
        // Sin token espera 401; con READONLY espera 403 en la ruta de administración.
        mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token("readonly")))
                .andExpect(status().isForbidden());
        // Con ADMIN la misma consulta debe funcionar y devolver una lista.
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // Al rotar se invalida la sesión anterior; reutilizar el refresh revoca la familia.
    @Test
    void tokenRotationRejectsOldAccessAndReplayRevokesSuccessor() throws Exception {
        // Inicia sesión y conserva tanto el access token como el refresh originales.
        var original = login("admin");
        // Renueva con el refresh original y recoge el nuevo par de tokens.
        var rotated =
                json(
                        mvc.perform(
                                        post("/api/v1/auth/refresh")
                                                .contentType(MediaType.APPLICATION_JSON)
                                                .content(
                                                        mapper.writeValueAsBytes(
                                                                Map.of(
                                                                        "refreshToken",
                                                                        original.path(
                                                                                        "refreshToken")
                                                                                .asText()))))
                                .andExpect(status().isOk())
                                .andReturn()
                                .getResponse()
                                .getContentAsString());
        // El access token anterior deja de ser aceptado tras la rotación.
        mvc.perform(
                        get("/api/v1/users")
                                .header(
                                        "Authorization",
                                        "Bearer " + original.path("accessToken").asText()))
                .andExpect(status().isUnauthorized());
        // El access token recién emitido funciona antes de detectar una reutilización.
        mvc.perform(
                        get("/api/v1/users")
                                .header(
                                        "Authorization",
                                        "Bearer " + rotated.path("accessToken").asText()))
                .andExpect(status().isOk());
        // Reutiliza el refresh anterior: debe responder 401 con REFRESH_REPLAY.
        mvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        mapper.writeValueAsBytes(
                                                Map.of(
                                                        "refreshToken",
                                                        original.path("refreshToken").asText()))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_REPLAY"));
        // Después del replay, la familia se revoca y el token sucesor también deja de funcionar.
        mvc.perform(
                        get("/api/v1/users")
                                .header(
                                        "Authorization",
                                        "Bearer " + rotated.path("accessToken").asText()))
                .andExpect(status().isUnauthorized());
        // Comprueba en PostgreSQL que la sesión del refresh sucesor fue revocada.
        assertThat(
                        jdbc.queryForObject(
                                "SELECT revoked_at IS NOT NULL FROM auth_session WHERE"
                                    + " refresh_hash=?",
                                Boolean.class,
                                AuthService.hash(rotated.path("refreshToken").asText())))
                .isTrue();
        // Comprueba que el evento de reutilización quedó auditado con el actor admin.
        assertThat(
                        jdbc.queryForObject(
                                "SELECT actor FROM audit_event WHERE action='REFRESH_REPLAY' ORDER"
                                    + " BY id DESC LIMIT 1",
                                String.class))
                .isEqualTo("admin");
    }

    // Cerrar sesión invalida los tokens y la base conserva solo el hash del refresh.
    @Test
    void logoutRevokesBothTokensAndStoresOnlyRefreshHash() throws Exception {
        // Inicia sesión y obtiene las credenciales que luego intentará usar.
        var auth = login("admin");
        // Confirma que el login quedó registrado en auditoría con el usuario correcto.
        assertThat(
                        jdbc.queryForObject(
                                "SELECT actor FROM audit_event WHERE action='LOGIN' ORDER BY id"
                                    + " DESC LIMIT 1",
                                String.class))
                .isEqualTo("admin");
        // Extrae el refresh para comparar su valor original con lo almacenado.
        String refresh = auth.path("refreshToken").asText();
        // El refresh en texto directo no debe aparecer como refresh_hash en la tabla.
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM auth_session WHERE refresh_hash=?",
                                Long.class,
                                refresh))
                .isZero();
        // Al buscar por el hash sí debe encontrarse una sesión.
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM auth_session WHERE refresh_hash=?",
                                Long.class,
                                AuthService.hash(refresh)))
                .isEqualTo(1);
        // Cierra la sesión usando el access token y espera 204.
        mvc.perform(
                        post("/api/v1/auth/logout")
                                .header(
                                        "Authorization",
                                        "Bearer " + auth.path("accessToken").asText()))
                .andExpect(status().isNoContent());
        // El access token ya no debe permitir acceder tras logout.
        mvc.perform(
                        get("/api/v1/users")
                                .header(
                                        "Authorization",
                                        "Bearer " + auth.path("accessToken").asText()))
                .andExpect(status().isUnauthorized());
        // El refresh de la misma familia también debe ser rechazado.
        mvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsBytes(Map.of("refreshToken", refresh))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutRejectsTheSameBearerOnProductsAndPreservesOtherLogins() throws Exception {
        String sessionToClose = "Bearer " + token("readonly");
        String otherSession = "Bearer " + token("readonly");
        String products = "/api/v1/products";

        mvc.perform(get(products).header("Authorization", sessionToClose))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", sessionToClose))
                .andExpect(status().isNoContent());
        mvc.perform(get(products).header("Authorization", sessionToClose))
                .andExpect(status().isUnauthorized());

        mvc.perform(get(products).header("Authorization", otherSession))
                .andExpect(status().isOk());
        String newSession = "Bearer " + token("readonly");
        mvc.perform(get(products).header("Authorization", newSession))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/users").header("Authorization", newSession))
                .andExpect(status().isForbidden());
    }

    // Un JWT vencido y una sesión refresh vencida se rechazan aunque sean auténticos.
    @Test
    void expiredSignedAccessTokenAndExpiredRefreshAreRejected() throws Exception {
        // Obtiene una sesión válida para reutilizar su sid en el token de prueba.
        var auth = login("admin");
        var original = decoder.decode(auth.path("accessToken").asText());
        // Construye claims con fecha de expiración ya pasada.
        var claims =
                JwtClaimsSet.builder()
                        .issuer("business-operations")
                        .subject("admin")
                        .issuedAt(Instant.now().minusSeconds(3600))
                        .expiresAt(Instant.now().minusSeconds(60))
                        .claim("sid", original.getClaimAsString("sid"))
                        .build();
        // Firma el JWT caducado con la clave de prueba; la firma válida no cambia su fecha.
        String expired =
                encoder.encode(
                                JwtEncoderParameters.from(
                                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                        .getTokenValue();
        // Comprueba que el acceso con ese JWT devuelva 401.
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
        // Fuerza en la base la caducidad de la sesión de refresh.
        jdbc.update(
                "UPDATE auth_session SET expires_at=current_timestamp-interval '1 second' WHERE"
                    + " refresh_hash=?",
                AuthService.hash(auth.path("refreshToken").asText()));
        // Comprueba que tampoco se pueda renovar una sesión vencida.
        mvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        mapper.writeValueAsBytes(
                                                Map.of(
                                                        "refreshToken",
                                                        auth.path("refreshToken").asText()))))
                .andExpect(status().isUnauthorized());
    }

    // Un JWT firmado con un claim sid de formato incorrecto también debe rechazarse.
    @Test
    void signedTokenWithMalformedSessionClaimIsUnauthorized() throws Exception {
        // Crea un token vigente pero pone una lista donde se espera el identificador UUID de sesión.
        var claims =
                JwtClaimsSet.builder()
                        .issuer("business-operations")
                        .subject("admin")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(60))
                        .claim("sid", java.util.List.of("not", "a", "uuid"))
                        .build();
        // Lo firma y comprueba que al usarlo la API devuelve 401.
        String malformed =
                encoder.encode(
                                JwtEncoderParameters.from(
                                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                        .getTokenValue();
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + malformed))
                .andExpect(status().isUnauthorized());
    }

    // Un cambio de rol debe aplicarse al siguiente acceso y la baja debe invalidar sesiones.
    @Test
    void userRoleChangesTakeEffectImmediatelyAndDeactivationRevokesSessions() throws Exception {
        // Crea un usuario OPERATIONS y obtiene un token suyo.
        String admin = token("admin");
        String username = unique("operator");
        long id = createUser(admin, username, "OPERATIONS");
        String access = token(username);
        // Al principio no tiene permiso para listar usuarios.
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden());
        // Un administrador cambia su rol a ADMIN conservando la cuenta activa.
        mvc.perform(
                        put("/api/v1/users/" + id)
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"role\":\"ADMIN\",\"active\":true}"))
                .andExpect(status().isOk());
        // Con el mismo token, la siguiente petición ya refleja el rol actual de la base.
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());
        // Desactiva la cuenta; desde ese momento su token deja de autenticar.
        mvc.perform(delete("/api/v1/users/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
        // Verifica en PostgreSQL la desactivación y comprueba que ya no pueda iniciar sesión.
        assertThat(jdbc.queryForObject("SELECT active FROM app_user WHERE id=?", Boolean.class, id))
                .isFalse();
        mvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        mapper.writeValueAsBytes(
                                                Map.of(
                                                        "username",
                                                        username,
                                                        "password",
                                                        "DevPassword123!"))))
                .andExpect(status().isUnauthorized());
    }

    // El administrador no puede quitarse su propio rol ni duplicar un nombre existente.
    @Test
    void cannotDemoteOwnAdminAndDuplicateUsernameIsConflict() throws Exception {
        // Obtiene el token y el id de la cuenta admin.
        String admin = token("admin");
        Long id = jdbc.queryForObject("SELECT id FROM app_user WHERE username='admin'", Long.class);
        // Intenta cambiar su propio rol a READONLY y exige el conflicto SELF_ADMIN_CHANGE.
        mvc.perform(
                        put("/api/v1/users/" + id)
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"role\":\"READONLY\",\"active\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_ADMIN_CHANGE"));
        // Intenta crear ADMIN en mayúsculas; la normalización debe impedir el duplicado.
        mvc.perform(
                        post("/api/v1/users")
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"username\":\"ADMIN\",\"password\":\"DevPassword123!\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isConflict());
        // Verifica que el rol original sigue siendo ADMIN.
        assertThat(jdbc.queryForObject("SELECT role FROM app_user WHERE id=?", String.class, id))
                .isEqualTo("ADMIN");
    }

    // Un nombre inválido y una contraseña corta no deben crear un usuario.
    @Test
    void invalidUserInputDoesNotInsertAnyUser() throws Exception {
        // Guarda el total inicial de cuentas para comparar después del rechazo.
        long count = jdbc.queryForObject("SELECT count(*) FROM app_user", Long.class);
        // Envía datos inválidos y espera respuesta 400.
        mvc.perform(
                        post("/api/v1/users")
                                .header("Authorization", "Bearer " + token("admin"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"username\":\"bad"
                                            + " spaces\",\"password\":\"short\",\"role\":\"READONLY\"}"))
                .andExpect(status().isBadRequest());
        // Comprueba que el número de cuentas no haya cambiado.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user", Long.class))
                .isEqualTo(count);
    }

    // Cambiar contraseña exige la anterior y revoca todas las sesiones solo si tiene éxito.
    @Test
    void passwordChangeChecksOldSecretThenRevokesEverySession() throws Exception {
        // Crea un usuario y abre dos sesiones independientes para comprobar su revocación.
        String username = unique("password");
        long id = createUser(token("admin"), username, "READONLY");
        var first = login(username);
        var second = login(username);
        // Guarda el hash anterior para detectar si la contraseña llegó a modificarse.
        String oldHash =
                jdbc.queryForObject(
                        "SELECT password_hash FROM app_user WHERE id=?", String.class, id);
        // Primero envía una contraseña actual incorrecta y espera 401.
        mvc.perform(
                        put("/api/v1/auth/password")
                                .header(
                                        "Authorization",
                                        "Bearer " + first.path("accessToken").asText())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"currentPassword\":\"incorrect\",\"newPassword\":\"ChangedPassword456!\"}"))
                .andExpect(status().isUnauthorized());
        // Verifica que el hash no cambió y las dos sesiones siguen activas.
        assertThat(
                        jdbc.queryForObject(
                                "SELECT password_hash FROM app_user WHERE id=?", String.class, id))
                .isEqualTo(oldHash);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM auth_session WHERE user_id=? AND revoked_at"
                                    + " IS NULL",
                                Long.class,
                                id))
                .isEqualTo(2);
        // Repite el cambio con la contraseña actual correcta y espera 204.
        mvc.perform(
                        put("/api/v1/auth/password")
                                .header(
                                        "Authorization",
                                        "Bearer " + first.path("accessToken").asText())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"currentPassword\":\"DevPassword123!\",\"newPassword\":\"ChangedPassword456!\"}"))
                .andExpect(status().isNoContent());
        // Comprueba que cambió el hash y que ninguna sesión anterior queda activa.
        assertThat(
                        jdbc.queryForObject(
                                "SELECT password_hash FROM app_user WHERE id=?", String.class, id))
                .isNotEqualTo(oldHash);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM auth_session WHERE user_id=? AND revoked_at"
                                    + " IS NULL",
                                Long.class,
                                id))
                .isZero();
        // El segundo access token y su refresh ya no deben funcionar.
        mvc.perform(
                        get("/api/v1/users")
                                .header(
                                        "Authorization",
                                        "Bearer " + second.path("accessToken").asText()))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        mapper.writeValueAsBytes(
                                                Map.of(
                                                        "refreshToken",
                                                        second.path("refreshToken").asText()))))
                .andExpect(status().isUnauthorized());
        // La contraseña antigua deja de ser válida; la nueva permite volver a iniciar sesión.
        mvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        mapper.writeValueAsBytes(
                                                Map.of(
                                                        "username",
                                                        username,
                                                        "password",
                                                        "DevPassword123!"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        mapper.writeValueAsBytes(
                                                Map.of(
                                                        "username",
                                                        username,
                                                        "password",
                                                        "ChangedPassword456!"))))
                .andExpect(status().isOk());
    }

    // Dos renovaciones simultáneas del mismo refresh solo permiten un éxito y detectan replay.
    @Test
    void concurrentRefreshCanIssueOnlyOneSuccessorAndReplayRevokesIt() throws Exception {
        // Inicia sesión y prepara un único cuerpo con el refresh compartido por ambos hilos.
        var auth = login("admin");
        byte[] body =
                mapper.writeValueAsBytes(
                        Map.of("refreshToken", auth.path("refreshToken").asText()));
        // El latch mantendrá ambas tareas esperando hasta que la prueba las libere.
        var start = new CountDownLatch(1);
        // Crea dos hilos para enviar las renovaciones en paralelo.
        try (var executor = Executors.newFixedThreadPool(2)) {
            // La primera tarea espera la señal y envía el refresh.
            var one =
                    executor.submit(
                            () -> {
                                start.await();
                                return mvc.perform(
                                                post("/api/v1/auth/refresh")
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content(body))
                                        .andReturn();
                            });
            // La segunda tarea hace lo mismo con el mismo token.
            var two =
                    executor.submit(
                            () -> {
                                start.await();
                                return mvc.perform(
                                                post("/api/v1/auth/refresh")
                                                        .contentType(MediaType.APPLICATION_JSON)
                                                        .content(body))
                                        .andReturn();
                            });
            // Libera a ambas y espera sus respuestas, con un límite de 20 segundos.
            start.countDown();
            var first = one.get(20, TimeUnit.SECONDS).getResponse();
            var second = two.get(20, TimeUnit.SECONDS).getResponse();
            // Una debe responder 200 y la otra 401; no se predice cuál gana.
            assertThat(new int[] {first.getStatus(), second.getStatus()})
                    .containsExactlyInAnyOrder(200, 401);
            // Recupera los tokens del intento exitoso y comprueba que el replay revocó también al sucesor.
            var successful = json((first.getStatus() == 200 ? first : second).getContentAsString());
            mvc.perform(
                            get("/api/v1/users")
                                    .header(
                                            "Authorization",
                                            "Bearer " + successful.path("accessToken").asText()))
                    .andExpect(status().isUnauthorized());
            // Consulta la base para verificar que no quedaron sesiones activas en la familia.
            Long active =
                    jdbc.queryForObject(
                            "SELECT count(*) FROM auth_session WHERE family_id=(SELECT family_id"
                                + " FROM auth_session WHERE refresh_hash=?) AND revoked_at IS NULL",
                            Long.class,
                            AuthService.hash(auth.path("refreshToken").asText()));
            assertThat(active).isZero();
        }
    }

    // Ayudante: inicia sesión con la contraseña de los datos de prueba y devuelve el JSON.
    private JsonNode login(String username) throws Exception {
        return json(
                mvc.perform(
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
                        .getContentAsString());
    }

    // Ayudante: crea un usuario con el rol indicado y devuelve su id para las pruebas.
    private long createUser(String admin, String username, String role) throws Exception {
        return json(mvc.perform(
                                post("/api/v1/users")
                                        .header("Authorization", "Bearer " + admin)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                mapper.writeValueAsBytes(
                                                        Map.of(
                                                                "username",
                                                                username,
                                                                "password",
                                                                "DevPassword123!",
                                                                "role",
                                                                role))))
                        .andExpect(status().isCreated())
                        .andReturn()
                        .getResponse()
                        .getContentAsString())
                .path("id")
                .asLong();
    }
}
