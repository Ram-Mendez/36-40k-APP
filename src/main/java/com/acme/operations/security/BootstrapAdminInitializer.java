package com.acme.operations.security;

import com.acme.operations.common.AuditService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Component
@ConditionalOnProperty(prefix = "security.bootstrap", name = "enabled", havingValue = "true")
public class BootstrapAdminInitializer implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final String username;
    private final String password;
    public BootstrapAdminInitializer(
            JdbcTemplate jdbc,
            PasswordEncoder encoder,
            AuditService audit,
            @Value("${security.bootstrap.username:${BOOTSTRAP_ADMIN_USERNAME:}}") String username,
            @Value("${security.bootstrap.password:${BOOTSTRAP_ADMIN_PASSWORD:}}") String password) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.audit = audit;
        this.username = username;
        this.password = password;
    }
    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        // Evita que dos instancias de la aplicación creen el administrador al mismo tiempo.
        jdbc.execute("SELECT pg_advisory_xact_lock(74100)");
        Long count = jdbc.queryForObject("SELECT count(*) FROM app_user", Long.class);
        // El proceso solo aprovisiona la primera cuenta; nunca cambia usuarios existentes.
        if (count != null && count > 0) return;
        if (!username.matches("[a-zA-Z0-9._-]{3,80}"))
            throw new IllegalStateException(
                    "Set a valid BOOTSTRAP_ADMIN_USERNAME for initial provisioning");
        if (password.length() < 12
                // BCrypt admite como máximo 72 bytes; UTF-8 puede usar varios bytes por carácter.
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || "DevPassword123!".equals(password)) {
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_PASSWORD must contain at least 12 characters, at most 72 UTF-8"
                        + " bytes, and differ from development credentials");
        }
        Long id =
                jdbc.queryForObject(
                        "INSERT INTO app_user(username,password_hash,role) VALUES (?,?,'ADMIN')"
                            + " RETURNING id",
                        Long.class,
                        // Guarda el usuario en minúsculas para mantener nombres normalizados.
                        username.toLowerCase(Locale.ROOT),
                        // La base de datos recibe el hash de la contraseña, no la contraseña original.
                        encoder.encode(password));
        // Deja trazabilidad del alta junto con el identificador de la cuenta creada.
        audit.record(
                "ADMIN_BOOTSTRAPPED",
                "USER",
                String.valueOf(id),
                "Initial administrator provisioned");
    }
}
