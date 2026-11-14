package com.acme.operations.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.crypto.spec.SecretKeySpec;
// Spring uses this configuration to register the beans defined here.
@Configuration
// It activates the @PreAuthorize checks when you enter through Spring's proxies.
@EnableMethodSecurity
public class SecurityConfiguration {
    // Spring records this encoder to inject it into authentication and administration.
    @Bean
    // Use the same password encoder for sign-in, account creation and password changes.
    PasswordEncoder passwordEncoder() {
        // The cost 12 makes it more expensive to try stolen passwords by gross force.
        return new BCryptPasswordEncoder(12);
    }
    // It records a template to manage transactions through code blocks.
    @Bean
    // It allows AuthService to delimit transactional blocks explicitly.
    TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
        // Reuse the Spring transactional manager; execute coordinates commit and rollback.
        return new TransactionTemplate(manager);
    }
    // It records the key built from the external secret, without writing it in the repository.
    @Bean
    // Read the external secret and build the key used to sign and validate JWT.
    SecretKeySpec jwtKey(@Value("${security.jwt.secret}") String secret) {
        // Measure bytes UTF-8; the number of characters does not guarantee the size of the key.
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        // It does not allow to boot with a key too short to sign tokens safely.
        if (bytes.length < 32)
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 UTF-8 bytes");
        // It associates bytes with the HMAC algorithm; it does not generate a new random key.
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
    // Register the shared access firm tokens.
    @Bean
    // Provides the JWT firmer with the set key.
    JwtEncoder jwtEncoder(SecretKeySpec jwtKey) {
        // Nimbus generates the signature; the content of the JWT remains legible.
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
    }
    // Record the verifier that Spring Security will use on the Bearer requests.
    @Bean
    // Prepare the signature verifier and the additional validity rules.
    JwtDecoder jwtDecoder(SecretKeySpec jwtKey, JdbcTemplate jdbc) {
        // It only supports the HS256 algorithm with this symmetrical key.
        var decoder =
                NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
        // In addition to the signature, each token must correspond to a session still in force in the database.
        OAuth2TokenValidator<Jwt> sessionValidator =
                jwt -> {
                    // Rejects absent or ill-formed claims without making them a valid session.
                    try {
                        // Check that the user is still active and that the session is not revoked or expired.
                        var count =
                                jdbc.queryForObject(
                                        "SELECT count(*) FROM auth_session s JOIN app_user u ON"
                                            + " u.id=s.user_id WHERE s.id=? AND u.username=? AND"
                                            + " u.active AND s.revoked_at IS NULL AND"
                                            + " s.expires_at>current_timestamp",
                                        Long.class,
                                        // The sid claim identifies the auth_session row. The subject must match
                                        // the user for that session.
                                        UUID.fromString(jwt.getClaimAsString("sid")),
                                        jwt.getSubject());
                        // Only an existing coincidence allows this part of the token to be validated.
                        if (count != null && count == 1)
                            return OAuth2TokenValidatorResult.success();
                    // A Sid who cannot become UUID also invalidate the session.
                    } catch (IllegalArgumentException | NullPointerException exception) {
                        return invalidSession();
                    }
                    // If you did not find a valid session, return the same rejection.
                    return invalidSession();
                };
        // Accepts only tokens with valid dates, issued by this application and with active session.
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        // Check the token timestamps without clock skew and require this application’s issuer.
                        new JwtTimestampValidator(Duration.ZERO),
                        new JwtIssuerValidator("business-operations"),
                        sessionValidator));
        // Deliver to Spring the configured decoder; use it when processing Bearer tokens.
        return decoder;
    }
    // It produces a failed validation result, not a direct HTTP response.
    private static OAuth2TokenValidatorResult invalidSession() {
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Session revoked or inactive", null));
    }
    // It records the filter chain that protects HTTP routes.
    @Bean
    // Define the safety chain that Spring runs before reaching the drivers.
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            @Value("${security.swagger-enabled:false}") boolean swaggerEnabled)
            throws Exception {
        // Authenticate through the token sessions stored in auth_session. Configure Spring Security for
        // stateless requests and disable CSRF.
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // It states which routes are public and which require authentication.
                .authorizeHttpRequests(
                        requests -> {
                            // Login, token renewal and health check are public routes. It allows interface,
                            // authentication and health; business roles are checked separately.
                            requests.requestMatchers(
                                            "/",
                                            "/index.html",
                                            "/*.js",
                                            "/*.css",
                                            "/assets/**",
                                            "/favicon.ico",
                                            "/api/v1/auth/login",
                                            "/api/v1/auth/refresh",
                                            "/actuator/health",
                                            "/actuator/health/**")
                                    .permitAll();
                            // The documentation is only public in environments where it is explicitly enabled.
                            if (swaggerEnabled)
                                requests.requestMatchers(
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/swagger-ui.html")
                                        .permitAll();
                            // The other routes require an authenticated user.
                            requests.anyRequest().authenticated();
                        })
                // The 401 and 403 responses use the same JSON format as the rest of the API.
                .exceptionHandling(
                        errors ->
                                errors.authenticationEntryPoint(
                                                (request, response, error) ->
                                                        error(
                                                                request,
                                                                response,
                                                                mapper,
                                                                401,
                                                                "UNAUTHORIZED",
                                                                "Authentication required"))
                                        .accessDeniedHandler(
                                                (request, response, error) ->
                                                        error(
                                                                request,
                                                                response,
                                                                mapper,
                                                                403,
                                                                "ACCESS_DENIED",
                                                                "Insufficient permissions")))
                // Spring validates the received JWT and creates the identity of the petition.
                .oauth2ResourceServer(
                        resource ->
                                resource.authenticationEntryPoint(
                                                (request, response, error) ->
                                                        error(
                                                                request,
                                                                response,
                                                                mapper,
                                                                401,
                                                                "UNAUTHORIZED",
                                                                "Invalid or expired access token"))
                                        .accessDeniedHandler(
                                                (request, response, error) ->
                                                        error(
                                                                request,
                                                                response,
                                                                mapper,
                                                                403,
                                                                "ACCESS_DENIED",
                                                                "Insufficient permissions"))
                                        .jwt(
                                                jwt ->
                                                        jwt.jwtAuthenticationConverter(
                                                                token -> {
                                                                    // Read the current role from the base to reflect permissions
                                                                    // changes.
                                                                    var roles =
                                                                            jdbc.queryForList(
                                                                                    "SELECT role"
                                                                                        + " FROM"
                                                                                        + " app_user"
                                                                                        + " WHERE"
                                                                                        + " username=?"
                                                                                        + " AND active",
                                                                                    String.class,
                                                                                    token
                                                                                            .getSubject());
                                                                    // A token from a non-existing or inactive user does not
                                                                    // authenticate the request.
                                                                    if (roles.isEmpty())
                                                                        throw new OAuth2AuthenticationException(
                                                                                new OAuth2Error(
                                                                                        "invalid_token"));
                                                                    // It converts the token and the current role in the identity
                                                                    // of Spring Security.
                                                                    return new JwtAuthenticationToken(
                                                                            token,
                                                                            List.of(
                                                                                    new SimpleGrantedAuthority(
                                                                                            "ROLE_"
                                                                                                    + roles
                                                                                                            .getFirst())),
                                                                            token.getSubject());
                                                                })));
        // Build the chain that Spring will use to filter the requests.
        return http.build();
    }
    // He writes errors originating in the filters, outside the normal handling of MVC exceptions.
    private static void error(
            HttpServletRequest request,
            HttpServletResponse response,
            ObjectMapper mapper,
            int status,
            String code,
            String detail)
            throws java.io.IOException {
        // Prepares the response status and format; 401 also reports that Bearer is expected.
        response.setStatus(status);
        response.setContentType("application/problem+json");
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        // Returns useful data to the client and the identifier that allows tracking the request in logs.
        mapper.writeValue(
                response.getOutputStream(),
                Map.of(
                        "type",
                        "urn:business-operations:error:" + code.toLowerCase(java.util.Locale.ROOT),
                        "title",
                        status == 401 ? "Unauthorized" : "Forbidden",
                        "instance",
                        request.getRequestURI(),
                        "status",
                        status,
                        "detail",
                        detail,
                        "code",
                        code,
                        "requestId",
                        String.valueOf(MDC.get("requestId"))));
    }
}
