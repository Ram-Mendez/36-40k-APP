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
// Spring procesa esta configuración para registrar los objetos definidos con @Bean.
@Configuration
// Activa las comprobaciones de @PreAuthorize al entrar por los proxies de Spring.
@EnableMethodSecurity
public class SecurityConfiguration {
    // Spring registra este codificador para inyectarlo en autenticación y administración.
    @Bean
    // Proporciona el codificador que login, altas y cambios de contraseña comparten.
    PasswordEncoder passwordEncoder() {
        // El coste 12 hace más caro probar contraseñas robadas por fuerza bruta.
        return new BCryptPasswordEncoder(12);
    }
    // Registra una plantilla para gestionar transacciones mediante bloques de código.
    @Bean
    // Permite que AuthService delimite bloques transaccionales de forma explícita.
    TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
        // Reutiliza el gestor transaccional de Spring; execute coordina commit y rollback.
        return new TransactionTemplate(manager);
    }
    // Registra la clave construida desde el secreto externo, sin escribirla en el repositorio.
    @Bean
    // Lee el secreto externo y construye la clave utilizada para firmar y validar JWT.
    SecretKeySpec jwtKey(@Value("${security.jwt.secret}") String secret) {
        // Mide bytes UTF-8; el número de caracteres no garantiza el tamaño de la clave.
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        // No permite arrancar con una clave demasiado corta para firmar tokens de forma segura.
        if (bytes.length < 32)
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 UTF-8 bytes");
        // Asocia los bytes con el algoritmo HMAC; no genera una nueva clave aleatoria.
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
    // Registra el firmador compartido de access tokens.
    @Bean
    // Proporciona el firmador de JWT con la clave configurada.
    JwtEncoder jwtEncoder(SecretKeySpec jwtKey) {
        // Nimbus genera la firma; el contenido del JWT sigue siendo legible.
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
    }
    // Registra el verificador que Spring Security utilizará en las peticiones Bearer.
    @Bean
    // Prepara el verificador de firma y las reglas adicionales de validez.
    JwtDecoder jwtDecoder(SecretKeySpec jwtKey, JdbcTemplate jdbc) {
        // Solo admite el algoritmo HS256 con esta clave simétrica.
        var decoder =
                NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
        // Además de la firma, cada token debe corresponder a una sesión aún vigente en la base de datos.
        OAuth2TokenValidator<Jwt> sessionValidator =
                jwt -> {
                    // Rechaza claims ausentes o mal formados sin convertirlos en una sesión válida.
                    try {
                        // Comprueba que el usuario siga activo y que la sesión no esté revocada ni vencida.
                        var count =
                                jdbc.queryForObject(
                                        "SELECT count(*) FROM auth_session s JOIN app_user u ON"
                                            + " u.id=s.user_id WHERE s.id=? AND u.username=? AND"
                                            + " u.active AND s.revoked_at IS NULL AND"
                                            + " s.expires_at>current_timestamp",
                                        Long.class,
                                        // El claim sid identifica auth_session y subject debe coincidir con el usuario de esa sesión.
                                        UUID.fromString(jwt.getClaimAsString("sid")),
                                        jwt.getSubject());
                        // Solo una coincidencia vigente permite validar esta parte del token.
                        if (count != null && count == 1)
                            return OAuth2TokenValidatorResult.success();
                    // Un sid que no puede convertirse a UUID también invalida la sesión.
                    } catch (IllegalArgumentException | NullPointerException exception) {
                        return invalidSession();
                    }
                    // Si no encontró sesión válida, devuelve el mismo rechazo.
                    return invalidSession();
                };
        // Acepta solo tokens con fechas válidas, emitidos por esta aplicación y con sesión activa.
        decoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        // Valida fechas sin margen adicional y exige el emisor de esta aplicación.
                        new JwtTimestampValidator(Duration.ZERO),
                        new JwtIssuerValidator("business-operations"),
                        sessionValidator));
        // Entrega a Spring el decoder configurado; lo usará al procesar Bearer tokens.
        return decoder;
    }
    // Produce un resultado de validación fallido, no una respuesta HTTP directa.
    private static OAuth2TokenValidatorResult invalidSession() {
        return OAuth2TokenValidatorResult.failure(
                new OAuth2Error("invalid_token", "Session revoked or inactive", null));
    }
    // Registra la cadena de filtros que protege las rutas HTTP.
    @Bean
    // Define la cadena de seguridad que Spring ejecuta antes de llegar a los controladores.
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JdbcTemplate jdbc,
            ObjectMapper mapper,
            @Value("${security.swagger-enabled:false}") boolean swaggerEnabled)
            throws Exception {
        // No usa HttpSession para autenticar; sí guarda sesiones de tokens en auth_session.
        // Desactiva CSRF y configura autenticación sin HttpSession, basada en el token de cada petición.
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Declara qué rutas son públicas y cuáles exigen autenticación.
                .authorizeHttpRequests(
                        requests -> {
                            // Login, renovación del token y health check son rutas públicas.
                            // Permite la interfaz, autenticación y health; los roles de negocio se comprueban aparte.
                            requests.requestMatchers(
                                            "/",
                                            "/index.html",
                                            "/app.js",
                                            "/styles.css",
                                            "/**/*.js",
                                            "/**/*.css",
                                            "/assets/**",
                                            "/favicon.ico",
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
        // Construye la cadena que usará Spring para filtrar las peticiones.
        return http.build();
    }
    // Escribe errores originados en los filtros, fuera del manejo normal de excepciones de MVC.
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
