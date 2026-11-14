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

// Spring manages this component and injects it wherever needed.
@Component
// Spring only creates this component if security.bootstrap.enabled is enabled.
@ConditionalOnProperty(prefix = "security.bootstrap", name = "enabled", havingValue = "true")
// AppliationRunner causes Spring to run run during the start.
public class BootstrapAdminInitializer implements ApplicationRunner {
    // JdbcTemplate executes the SQL and maps the result rows.
    private final JdbcTemplate jdbc;
    // Password encoding injected by Spring to avoid saving clear passwords.
    private final PasswordEncoder encoder;
    // Use the shared audit service to record this operation.
    private final AuditService audit;
    // Initial name obtained from configuration, not from an HTTP request.
    private final String username;
    // Initial secret received from configuration; when it persists it is coded with BCrypt.
    private final String password;
    // Spring passes JDBC, the encoder, audit and external credentials when creating the component.
    public BootstrapAdminInitializer(
            JdbcTemplate jdbc,
            PasswordEncoder encoder,
            AuditService audit,
            // Spring solves properties and, as an alternative, environment variables.
            @Value("${security.bootstrap.username:${BOOTSTRAP_ADMIN_USERNAME:}}") String username,
            @Value("${security.bootstrap.password:${BOOTSTRAP_ADMIN_PASSWORD:}}") String password) {
        // Keep the injected dependency for later calls.
        this.jdbc = jdbc;
        // Keep the injected dependency for later calls.
        this.encoder = encoder;
        // Keep the injected dependency for later calls.
        this.audit = audit;
        // It retains the configuration credentials for the initial supply.
        this.username = username;
        // Keep the secret received; it will be encoded before saving it in PostgreSQL.
        this.password = password;
    }
    // The compiler checks that this method overrides the inherited declaration.
    @Override
    // Spring starts or joins a transaction when the call goes through the proxy. It commits on success and rolls
    // back on unchecked exceptions.
    @Transactional
    // Create the initial account inside a transaction at startup.
    public void run(ApplicationArguments arguments) {
        // It prevents two application instances from creating the administrator at the same time.
        jdbc.execute("SELECT pg_advisory_xact_lock(74100)");
        // Check if there is any account before creating the administrator.
        Long count = jdbc.queryForObject("SELECT count(*) FROM app_user", Long.class);
        // The process only provides the first account; it never changes existing users.
        if (count != null && count > 0) return;
        // Valid the name format to avoid an invalid initial account.
        if (!username.matches("[a-zA-Z0-9._-]{3,80}"))
            // If valid configuration is missing, the boot fails with an explanatory message.
            throw new IllegalStateException(
                    "Set a valid BOOTSTRAP_ADMIN_USERNAME for initial provisioning");
        // It requires minimum length, maximum BCrypt bytes and not the development password.
        if (password.length() < 12
                // BCrypt supports at most 72 bytes; UTF-8 can use several bytes per character.
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || "DevPassword123!".equals(password)) {
            // Abortion here prevents the storage of an administrator with unadmitted credentials.
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_PASSWORD must contain at least 12 characters, at most 72 UTF-8"
                        + " bytes, and differ from development credentials");
        }
        // Insert the ADMIN account and get your id generated for the audit.
        Long id =
                jdbc.queryForObject(
                        "INSERT INTO app_user(username,password_hash,role) VALUES (?,?,'ADMIN')"
                            + " RETURNING id",
                        Long.class,
                        // Save the user in small cases to maintain standard names.
                        username.toLowerCase(Locale.ROOT),
                        // The database receives the hash of the password, not the original password.
                        encoder.encode(password));
        // Record the new account ID in the audit log in the same transaction as account creation.
        audit.record(
                "ADMIN_BOOTSTRAPPED",
                "USER",
                String.valueOf(id),
                "Initial administrator provisioned");
    }
}
