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
@Configuration
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        // El coste 12 hace más caro probar contraseñas robadas por fuerza bruta.
        return new BCryptPasswordEncoder(12);
    }
    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
        return new TransactionTemplate(manager);
    }
    @Bean
    SecretKeySpec jwtKey(@Value("${security.jwt.secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        // No permite arrancar con una clave demasiado corta para firmar tokens de forma segura.
        if (bytes.length < 32)
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 UTF-8 bytes");
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
    @Bean
    JwtEncoder jwtEncoder(SecretKeySpec jwtKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
    }
    @Bean
    JwtDecoder jwtDecoder(SecretKeySpec jwtKey, JdbcTemplate jdbc) {
        var decoder =
                NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
        // Además de la firma, cada token debe corresponder a una sesión aún vigente en la base de datos.
        OAuth2TokenValidator<Jwt> sessionValidator =
                jwt -> {
                    try {
                        // Comprueba que el usuario siga activo y que la sesión no esté revocada ni vencida.
                        var count =
                                jdbc.queryForObject(
                                        "SELECT count(*) FROM auth_session s JOIN app_user u ON"
                                            + " u.id=s.user_id WHERE s.id=? AND u.username=? AND"
                                            + " u.active AND s.revoked_at IS NULL AND"
                                            + " s.expires_at>current_timestamp",
                                        Long.class,
                                        UUID.fromString(jwt.getClaimAsString("sid")),
                                        jwt.getSubject());
                        if (count != null && count == 1)
                            return OAuth2TokenValidatorResult.success();
                    } catch (IllegalArgumentException | NullPointerException exception) {
                        return invalidSession();
                    }
                    return invalidSession();
                };
        // Acepta solo tokens con fechas válidas, emitidos por esta aplicación y con sesión activa.
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        new JwtTimestampValidator(Duration.ZERO),
                        new JwtIssuerValidator("business-operations"),
                        sessionValidator));
        return decoder;
    }
    private static OAuth2TokenValidatorResult invalidSession() {
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Session revoked or inactive", null));
    }
    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            @Value("${security.swagger-enabled:false}") boolean swaggerEnabled)
            throws Exception {
        // La API no mantiene sesión en el servidor: cada petición debe traer su propio token.
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        requests -> {
                            // Login, renovación del token y health check son rutas públicas.
                            requests.requestMatchers(
                                            "/api/v1/auth/login",
                                            "/api/v1/auth/refresh",
                                            "/actuator/health",
                                            "/actuator/health/**")
                                    .permitAll();
                            // La documentación solo queda pública en entornos donde se habilita explícitamente.
                            if (swaggerEnabled)
                                requests.requestMatchers(
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/swagger-ui.html")
                                        .permitAll();
                            // El resto de rutas exige un usuario autenticado.
                            requests.anyRequest().authenticated();
                        })
                // Las respuestas 401 y 403 usan el mismo formato JSON que el resto de la API.
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
                // Spring valida el JWT recibido y crea la identidad de la petición.
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
                                                                    // Lee el rol actual desde la base para reflejar cambios de permisos.
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
                                                                    // Un token de un usuario inexistente o inactivo no autentica la petición.
                                                                    if (roles.isEmpty())
                                                                        throw new OAuth2AuthenticationException(
                                                                                new OAuth2Error(
                                                                                        "invalid_token"));
                                                                    // Convierte el token y el rol vigente en la identidad de Spring Security.
                                                                    return new JwtAuthenticationToken(
                                                                            token,
                                                                            List.of(
                                                                                    new SimpleGrantedAuthority(
                                                                                            "ROLE_"
                                                                                                    + roles
                                                                                                            .getFirst())),
                                                                            token.getSubject());
                                                                })));
        return http.build();
    }
    private static void error(
            HttpServletRequest request,
            HttpServletResponse response,
            ObjectMapper mapper,
            int status,
            String code,
            String detail)
            throws java.io.IOException {
        // Prepara el estado y formato de respuesta; 401 también informa que se espera Bearer.
        response.setStatus(status);
        response.setContentType("application/problem+json");
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        // Devuelve datos útiles para el cliente y el identificador que permite rastrear la petición en logs.
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
