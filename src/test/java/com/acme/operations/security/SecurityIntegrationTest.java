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

// Try authentication and permissions with Spring and the PostgreSQL integration base.
class SecurityIntegrationTest extends AbstractIntegrationTest {
    // Use the firmer and verifier configured by the application to manufacture JWT test.
    @Autowired JwtEncoder encoder;
    @Autowired JwtDecoder decoder;

    // Non-existing user and incorrect password must receive the same 401 rejection.
    @Test
    void wrongPasswordAndUnknownUserHaveSameUnauthorizedResponse() throws Exception {
        // Repeat the same attempt with admin and a name that does not exist.
        for (String username : new String[] {"admin", "nonexistent"}) {
            // Send a wrong password to the actual login endpoint.
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
                    // Check the status and same INVALID_CREDENTIALS code in both cases.
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }
    }

    // Distinguish not to be authenticated from being authenticated without permission.
    @Test
    void authenticationAndOperationPermissionAreSeparate() throws Exception {
        // No token awaits 401; with READONLY awaits 403 on the administration route.
        mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token("readonly")))
                .andExpect(status().isForbidden());
        // With ADMIN the same consultation must work and return a list.
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + token("admin")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    // When rotating the previous session is invalidated; reuse the refresh revokes the family.
    @Test
    void tokenRotationRejectsOldAccessAndReplayRevokesSuccessor() throws Exception {
        // Login and keep both the original access token and the original refresh.
        var original = login("admin");
        // Renueva with the original refresh and collect the new pair of tokens.
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
        // The above access token is no longer accepted after rotation.
        mvc.perform(
                        get("/api/v1/users")
                                .header(
                                        "Authorization",
                                        "Bearer " + original.path("accessToken").asText()))
                .andExpect(status().isUnauthorized());
        // The newly issued token access works before detecting a re-use.
        mvc.perform(
                        get("/api/v1/users")
                                .header(
                                        "Authorization",
                                        "Bearer " + rotated.path("accessToken").asText()))
                .andExpect(status().isOk());
        // Reuse the above refresh: you must respond 401 with REFRESH_REPLAY.
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
        // After the replay, the family is revoked and the successor token also stops working.
        mvc.perform(
                        get("/api/v1/users")
                                .header(
                                        "Authorization",
                                        "Bearer " + rotated.path("accessToken").asText()))
                .andExpect(status().isUnauthorized());
        // He checks in PostgreSQL that the successor refresh session was revoked.
        assertThat(
                        jdbc.queryForObject(
                                "SELECT revoked_at IS NOT NULL FROM auth_session WHERE"
                                    + " refresh_hash=?",
                                Boolean.class,
                                AuthService.hash(rotated.path("refreshToken").asText())))
                .isTrue();
        // It shows that the reuse event was audited with the admin actor.
        assertThat(
                        jdbc.queryForObject(
                                "SELECT actor FROM audit_event WHERE action='REFRESH_REPLAY' ORDER"
                                    + " BY id DESC LIMIT 1",
                                String.class))
                .isEqualTo("admin");
    }

    // Close the invalid session of the tokens and the base only conserves the hash of the refresh.
    @Test
    void logoutRevokesBothTokensAndStoresOnlyRefreshHash() throws Exception {
        // You log in and get the credentials you will then try to use.
        var auth = login("admin");
        // It confirms that the login was registered in audit with the right user.
        assertThat(
                        jdbc.queryForObject(
                                "SELECT actor FROM audit_event WHERE action='LOGIN' ORDER BY id"
                                    + " DESC LIMIT 1",
                                String.class))
                .isEqualTo("admin");
        // Extract the refresh to compare its original value with what is stored.
        String refresh = auth.path("refreshToken").asText();
        // Check that refresh_hash does not contain the raw refresh token.
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM auth_session WHERE refresh_hash=?",
                                Long.class,
                                refresh))
                .isZero();
        // When looking for the hash, a session should be found.
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM auth_session WHERE refresh_hash=?",
                                Long.class,
                                AuthService.hash(refresh)))
                .isEqualTo(1);
        // Close the session using the access token and wait 204.
        mvc.perform(
                        post("/api/v1/auth/logout")
                                .header(
                                        "Authorization",
                                        "Bearer " + auth.path("accessToken").asText()))
                .andExpect(status().isNoContent());
        // After logout, the access token must be rejected.
        mvc.perform(
                        get("/api/v1/users")
                                .header(
                                        "Authorization",
                                        "Bearer " + auth.path("accessToken").asText()))
                .andExpect(status().isUnauthorized());
        // The refresh of the same family must also be rejected.
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

    // A defeated JWT and a defeated cool session are rejected even if they are authentic.
    @Test
    void expiredSignedAccessTokenAndExpiredRefreshAreRejected() throws Exception {
        // You get a valid session to reuse your Sid in the test token.
        var auth = login("admin");
        var original = decoder.decode(auth.path("accessToken").asText());
        // It builds claims with an expiry date already passed.
        var claims =
                JwtClaimsSet.builder()
                        .issuer("business-operations")
                        .subject("admin")
                        .issuedAt(Instant.now().minusSeconds(3600))
                        .expiresAt(Instant.now().minusSeconds(60))
                        .claim("sid", original.getClaimAsString("sid"))
                        .build();
        // Sign the expired JWT with the test key; the valid signature does not change its date.
        String expired =
                encoder.encode(
                                JwtEncoderParameters.from(
                                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                        .getTokenValue();
        // Check that access with that JWT returns 401.
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
        // Force at the base the expiry of the refresh session.
        jdbc.update(
                "UPDATE auth_session SET expires_at=current_timestamp-interval '1 second' WHERE"
                    + " refresh_hash=?",
                AuthService.hash(auth.path("refreshToken").asText()));
        // It shows that an expired session cannot be renewed either.
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

    // A JWT signed with an incorrect format claim Sid must also be rejected.
    @Test
    void signedTokenWithMalformedSessionClaimIsUnauthorized() throws Exception {
        // It creates a valid token but sets a list where the session UUID identifier is expected.
        var claims =
                JwtClaimsSet.builder()
                        .issuer("business-operations")
                        .subject("admin")
                        .issuedAt(Instant.now())
                        .expiresAt(Instant.now().plusSeconds(60))
                        .claim("sid", java.util.List.of("not", "a", "uuid"))
                        .build();
        // Sign it and check that when using the API, it returns 401.
        String malformed =
                encoder.encode(
                                JwtEncoderParameters.from(
                                        JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                        .getTokenValue();
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + malformed))
                .andExpect(status().isUnauthorized());
    }

    // Check that the new role applies on the next request and that deactivation invalidates existing sessions.
    @Test
    void userRoleChangesTakeEffectImmediatelyAndDeactivationRevokesSessions() throws Exception {
        // Create an OPERATIONS user and get a token of it.
        String admin = token("admin");
        String username = unique("operator");
        long id = createUser(admin, username, "OPERATIONS");
        String access = token(username);
        // At first you have no permission to list users.
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden());
        // An administrator changes his role to ADMIN by retaining the active account.
        mvc.perform(
                        put("/api/v1/users/" + id)
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"role\":\"ADMIN\",\"active\":true}"))
                .andExpect(status().isOk());
        // With the same token, the following request already reflects the current role of the base.
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk());
        // Disable the account; from that moment your token stops authenticating.
        mvc.perform(delete("/api/v1/users/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/users").header("Authorization", "Bearer " + access))
                .andExpect(status().isUnauthorized());
        // Check the deactivation in PostgreSQL and check that you can no longer log in.
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

    // The administrator cannot take away his own role or duplicate an existing name.
    @Test
    void cannotDemoteOwnAdminAndDuplicateUsernameIsConflict() throws Exception {
        // Get the token and the id from the admin account.
        String admin = token("admin");
        Long id = jdbc.queryForObject("SELECT id FROM app_user WHERE username='admin'", Long.class);
        // He tries to change his own role to READONLY and demands the SELF_ADMIN_CHANGE conflict.
        mvc.perform(
                        put("/api/v1/users/" + id)
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"role\":\"READONLY\",\"active\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SELF_ADMIN_CHANGE"));
        // Try to create ADMIN in capital letters; normalization must prevent duplication.
        mvc.perform(
                        post("/api/v1/users")
                                .header("Authorization", "Bearer " + admin)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"username\":\"ADMIN\",\"password\":\"DevPassword123!\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isConflict());
        // He verifies that the original role remains ADMIN.
        assertThat(jdbc.queryForObject("SELECT role FROM app_user WHERE id=?", String.class, id))
                .isEqualTo("ADMIN");
    }

    // An invalid name and a short password should not create a user.
    @Test
    void invalidUserInputDoesNotInsertAnyUser() throws Exception {
        // Save the initial total of accounts to compare after rejection.
        long count = jdbc.queryForObject("SELECT count(*) FROM app_user", Long.class);
        // Send invalid data and wait for response 400.
        mvc.perform(
                        post("/api/v1/users")
                                .header("Authorization", "Bearer " + token("admin"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"username\":\"bad"
                                            + " spaces\",\"password\":\"short\",\"role\":\"READONLY\"}"))
                .andExpect(status().isBadRequest());
        // Check that the number of accounts has not changed.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user", Long.class))
                .isEqualTo(count);
    }

    // Changing password requires the previous one and revokes all sessions only if it is successful.
    @Test
    void passwordChangeChecksOldSecretThenRevokesEverySession() throws Exception {
        // Create a user and open two independent sessions to check its revocation.
        String username = unique("password");
        long id = createUser(token("admin"), username, "READONLY");
        var first = login(username);
        var second = login(username);
        // Save the previous hash to detect if the password was changed.
        String oldHash =
                jdbc.queryForObject(
                        "SELECT password_hash FROM app_user WHERE id=?", String.class, id);
        // First send an incorrect current password and wait 401.
        mvc.perform(
                        put("/api/v1/auth/password")
                                .header(
                                        "Authorization",
                                        "Bearer " + first.path("accessToken").asText())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"currentPassword\":\"incorrect\",\"newPassword\":\"ChangedPassword456!\"}"))
                .andExpect(status().isUnauthorized());
        // He verifies that the hash did not change and the two sessions are still active.
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
        // Repeat the change with the correct current password and wait 204.
        mvc.perform(
                        put("/api/v1/auth/password")
                                .header(
                                        "Authorization",
                                        "Bearer " + first.path("accessToken").asText())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"currentPassword\":\"DevPassword123!\",\"newPassword\":\"ChangedPassword456!\"}"))
                .andExpect(status().isNoContent());
        // Check that the hash changed and that no previous session remains active.
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
        // The second access token and its refresh should no longer work.
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
        // The old password is no longer valid; the new one allows you to log in again.
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

    // Two simultaneous renovations of the same refresh only allow a success and detect replay.
    @Test
    void concurrentRefreshCanIssueOnlyOneSuccessorAndReplayRevokesIt() throws Exception {
        // It starts session and prepares a single body with the refresh shared by both threads.
        var auth = login("admin");
        byte[] body =
                mapper.writeValueAsBytes(
                        Map.of("refreshToken", auth.path("refreshToken").asText()));
        // The latch will keep both tasks waiting until the test releases them.
        var start = new CountDownLatch(1);
        // Create two threads to send the renovations in parallel.
        try (var executor = Executors.newFixedThreadPool(2)) {
            // The first task awaits the signal and sends the refresh.
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
            // The second task does the same with the same token.
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
            // Release both and wait for your answers, with a limit of 20 seconds.
            start.countDown();
            var first = one.get(20, TimeUnit.SECONDS).getResponse();
            var second = two.get(20, TimeUnit.SECONDS).getResponse();
            // One must respond 200 and the other 401; no one is predicted to win.
            assertThat(new int[] {first.getStatus(), second.getStatus()})
                    .containsExactlyInAnyOrder(200, 401);
            // He recovers the tokens of the successful attempt and checks that the replay also revoked the
            // successor.
            var successful = json((first.getStatus() == 200 ? first : second).getContentAsString());
            mvc.perform(
                            get("/api/v1/users")
                                    .header(
                                            "Authorization",
                                            "Bearer " + successful.path("accessToken").asText()))
                    .andExpect(status().isUnauthorized());
            // See the basis for verifying that there were no active sessions left in the family.
            Long active =
                    jdbc.queryForObject(
                            "SELECT count(*) FROM auth_session WHERE family_id=(SELECT family_id"
                                + " FROM auth_session WHERE refresh_hash=?) AND revoked_at IS NULL",
                            Long.class,
                            AuthService.hash(auth.path("refreshToken").asText()));
            assertThat(active).isZero();
        }
    }

    // Assistant: log in with the test data password and return the JSON.
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

    // Helper: create a user with the indicated role and return your id for the tests.
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
