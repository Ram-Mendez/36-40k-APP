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

class SecurityIntegrationTest extends AbstractIntegrationTest {
    @Autowired JwtEncoder encoder;
    @Autowired JwtDecoder decoder;

    @Test
    void wrongPasswordAndUnknownUserHaveSameUnauthorizedResponse() throws Exception {
        for (String username : new String[] {"admin", "nonexistent"}) {
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
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
    }

    @Test
    void authenticationAndOperationPermissionAreSeparate() throws Exception {
        mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token("readonly")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void tokenRotationRejectsOldAccessAndReplayRevokesSuccessor() throws Exception {
        var original = login("admin");
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
        mvc.perform(
                        get("/api/v1/users")
                                .header(
                                        "Authorization",
                                        "Bearer " + original.path("accessToken").asText()))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        get("/api/v1/users")
                                .header(
                                        "Authorization",
                                        "Bearer " + rotated.path("accessToken").asText()))
                .andExpect(status().isOk());
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
        mvc.perform(
                        get("/api/v1/users")
                                .header(
                                        "Authorization",
                                        "Bearer " + rotated.path("accessToken").asText()))
                .andExpect(status().isUnauthorized());
        assertThat(
                        jdbc.queryForObject(
                                "SELECT revoked_at IS NOT NULL FROM auth_session WHERE"
                                    + " refresh_hash=?",
                                Boolean.class,
                                AuthService.hash(rotated.path("refreshToken").asText())))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT actor FROM audit_event WHERE action='REFRESH_REPLAY' ORDER"
                                    + " BY id DESC LIMIT 1",
                                String.class))
                .isEqualTo("admin");
    }

    @Test
    void logoutRevokesBothTokensAndStoresOnlyRefreshHash() throws Exception {
        var auth = login("admin");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT actor FROM audit_event WHERE action='LOGIN' ORDER BY id"
                                    + " DESC LIMIT 1",
                                String.class))
                .isEqualTo("admin");
        String refresh = auth.path("refreshToken").asText();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM auth_session WHERE refresh_hash=?",
                                Long.class,
                                refresh))
                .isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM auth_session WHERE refresh_hash=?",
                                Long.class,
                                AuthService.hash(refresh)))
                .isEqualTo(1);
        mvc.perform(
                        post("/api/v1/auth/logout")
                                .header(
                                        "Authorization",
                                        "Bearer " + auth.path("accessToken").asText()))
                .andExpect(status().isNoContent());
        mvc.perform(
                        get("/api/v1/users")
                                .header(
                                        "Authorization",
                                        "Bearer " + auth.path("accessToken").asText()))
                .andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/v1/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(mapper.writeValueAsBytes(Map.of("refreshToken", refresh))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredSignedAccessTokenAndExpiredRefreshAreRejected() throws Exception {
        var auth = login("admin");
        var original = decoder.decode(auth.path("accessToken").asText());
        var claims =
                JwtClaimsSet.builder()
                        .issuer("business-operations")
                        .subject("admin")
                        .issuedAt(Instant.now().minusSeconds(3600))
                        .expiresAt(Instant.now().minusSeconds(60))
                        .claim("sid", original.getClaimAsString("sid"))
                        .build();
        String expired =
                encoder.encode(
                                JwtEncoderParameters.from(
                                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                        .getTokenValue();
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
        jdbc.update(
                "UPDATE auth_session SET expires_at=current_timestamp-interval '1 second' WHERE"
                    + " refresh_hash=?",
                AuthService.hash(auth.path("refreshToken").asText()));
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

    @Test
    void signedTokenWithMalformedSessionClaimIsUnauthorized() throws Exception {
        var claims =
                JwtClaimsSet.builder()
                        .issuer("business-operations")
                        .subject("admin")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(60))
                        .claim("sid", java.util.List.of("not", "a", "uuid"))
                        .build();
        String malformed =
                encoder.encode(
                                JwtEncoderParameters.from(
                                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                        .getTokenValue();
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + malformed))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userRoleChangesTakeEffectImmediatelyAndDeactivationRevokesSessions() throws Exception {
        String admin = token("admin");
        String username = unique("operator");
        long id = createUser(admin, username, "OPERATIONS");
        String access = token(username);
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden());
        mvc.perform(
                        put("/api/v1/users/" + id)
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"role\":\"ADMIN\",\"active\":true}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/users/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
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

    @Test
    void cannotDemoteOwnAdminAndDuplicateUsernameIsConflict() throws Exception {
        String admin = token("admin");
        Long id = jdbc.queryForObject("SELECT id FROM app_user WHERE username='admin'", Long.class);
        mvc.perform(
                        put("/api/v1/users/" + id)
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"role\":\"READONLY\",\"active\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_ADMIN_CHANGE"));
        mvc.perform(
                        post("/api/v1/users")
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"username\":\"ADMIN\",\"password\":\"DevPassword123!\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT role FROM app_user WHERE id=?", String.class, id))
                .isEqualTo("ADMIN");
    }

    @Test
    void invalidUserInputDoesNotInsertAnyUser() throws Exception {
        long count = jdbc.queryForObject("SELECT count(*) FROM app_user", Long.class);
        mvc.perform(
                        post("/api/v1/users")
                                .header("Authorization", "Bearer " + token("admin"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"username\":\"bad"
                                            + " spaces\",\"password\":\"short\",\"role\":\"READONLY\"}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user", Long.class))
                .isEqualTo(count);
    }

    @Test
    void passwordChangeChecksOldSecretThenRevokesEverySession() throws Exception {
        String username = unique("password");
        long id = createUser(token("admin"), username, "READONLY");
        var first = login(username);
        var second = login(username);
        String oldHash =
                jdbc.queryForObject(
                        "SELECT password_hash FROM app_user WHERE id=?", String.class, id);
        mvc.perform(
                        put("/api/v1/auth/password")
                                .header(
                                        "Authorization",
                                        "Bearer " + first.path("accessToken").asText())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"currentPassword\":\"incorrect\",\"newPassword\":\"ChangedPassword456!\"}"))
                .andExpect(status().isUnauthorized());
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
        mvc.perform(
                        put("/api/v1/auth/password")
                                .header(
                                        "Authorization",
                                        "Bearer " + first.path("accessToken").asText())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"currentPassword\":\"DevPassword123!\",\"newPassword\":\"ChangedPassword456!\"}"))
                .andExpect(status().isNoContent());
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

    @Test
    void concurrentRefreshCanIssueOnlyOneSuccessorAndReplayRevokesIt() throws Exception {
        var auth = login("admin");
        byte[] body =
                mapper.writeValueAsBytes(
                        Map.of("refreshToken", auth.path("refreshToken").asText()));
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
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
            start.countDown();
            var first = one.get(20, TimeUnit.SECONDS).getResponse();
            var second = two.get(20, TimeUnit.SECONDS).getResponse();
            assertThat(new int[] {first.getStatus(), second.getStatus()})
                    .containsExactlyInAnyOrder(200, 401);
            var successful = json((first.getStatus() == 200 ? first : second).getContentAsString());
            mvc.perform(
                            get("/api/v1/users")
                                    .header(
                                            "Authorization",
                                            "Bearer " + successful.path("accessToken").asText()))
                    .andExpect(status().isUnauthorized());
            Long active =
                    jdbc.queryForObject(
                            "SELECT count(*) FROM auth_session WHERE family_id=(SELECT family_id"
                                + " FROM auth_session WHERE refresh_hash=?) AND revoked_at IS NULL",
                            Long.class,
                            AuthService.hash(auth.path("refreshToken").asText()));
            assertThat(active).isZero();
        }
    }

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
