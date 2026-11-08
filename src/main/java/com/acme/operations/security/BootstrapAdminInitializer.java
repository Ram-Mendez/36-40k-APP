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

// Spring crea y gestiona este componente para poder inyectarlo donde se necesita.
@Component
// Spring solo crea este componente si security.bootstrap.enabled está habilitado.
@ConditionalOnProperty(prefix = "security.bootstrap", name = "enabled", havingValue = "true")
// ApplicationRunner hace que Spring ejecute run durante el arranque.
public class BootstrapAdminInitializer implements ApplicationRunner {
    // JdbcTemplate ejecuta nuestro SQL y adapta filas; no decide las reglas de negocio.
    private final JdbcTemplate jdbc;
    // Codificador de contraseñas inyectado por Spring para no guardar contraseñas en claro.
    private final PasswordEncoder encoder;
    // Servicio de auditoría compartido para registrar las acciones de esta operación.
    private final AuditService audit;
    // Nombre inicial obtenido de configuración, no de una petición HTTP.
    private final String username;
    // Secreto inicial recibido de configuración; al persistirlo se codifica con BCrypt.
    private final String password;
    // Spring pasa JDBC, el codificador, auditoría y las credenciales externas al crear el componente.
    public BootstrapAdminInitializer(
            JdbcTemplate jdbc,
            PasswordEncoder encoder,
            AuditService audit,
            // Spring resuelve las propiedades y, como alternativa, las variables de entorno.
            @Value("${security.bootstrap.username:${BOOTSTRAP_ADMIN_USERNAME:}}") String username,
            @Value("${security.bootstrap.password:${BOOTSTRAP_ADMIN_PASSWORD:}}") String password) {
        // Conserva la referencia recibida para que los métodos puedan reutilizar esta dependencia.
        this.jdbc = jdbc;
        // Conserva la referencia recibida para que los métodos puedan reutilizar esta dependencia.
        this.encoder = encoder;
        // Conserva la referencia recibida para que los métodos puedan reutilizar esta dependencia.
        this.audit = audit;
        // Conserva las credenciales de configuración para el aprovisionamiento inicial.
        this.username = username;
        // Conserva el secreto recibido; se codificará antes de guardarlo en PostgreSQL.
        this.password = password;
    }
    // El compilador comprueba que este método implementa o sobrescribe el contrato heredado.
    @Override
    // Al entrar por el proxy, Spring abre o comparte transacción; confirma al terminar y revierte ante errores no
    // comprobados.
    @Transactional
    // Ejecuta el alta inicial en transacción; no es un endpoint.
    public void run(ApplicationArguments arguments) {
        // Evita que dos instancias de la aplicación creen el administrador al mismo tiempo.
        jdbc.execute("SELECT pg_advisory_xact_lock(74100)");
        // Comprueba si ya existe alguna cuenta antes de crear el administrador.
        Long count = jdbc.queryForObject("SELECT count(*) FROM app_user", Long.class);
        // El proceso solo aprovisiona la primera cuenta; nunca cambia usuarios existentes.
        if (count != null && count > 0) return;
        // Valida el formato del nombre para evitar una cuenta inicial inválida.
        if (!username.matches("[a-zA-Z0-9._-]{3,80}"))
            // Si falta configuración válida, el arranque falla con un mensaje explicativo.
            throw new IllegalStateException(
                    "Set a valid BOOTSTRAP_ADMIN_USERNAME for initial provisioning");
        // Exige longitud mínima, máximo de bytes BCrypt y que no sea la contraseña de desarrollo.
        if (password.length() < 12
                // BCrypt admite como máximo 72 bytes; UTF-8 puede usar varios bytes por carácter.
                || password.getBytes(StandardCharsets.UTF_8).length > 72
                || "DevPassword123!".equals(password)) {
            // Abortar aquí impide guardar un administrador con credenciales no admitidas.
            throw new IllegalStateException(
                    "BOOTSTRAP_ADMIN_PASSWORD must contain at least 12 characters, at most 72 UTF-8"
                        + " bytes, and differ from development credentials");
        }
        // Inserta la cuenta ADMIN y obtiene su id generado para la auditoría.
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
        // Registra la acción junto con los cambios; si la transacción se revierte, también se deshace su auditoría.
        audit.record(
                "ADMIN_BOOTSTRAPPED",
                "USER",
                String.valueOf(id),
                "Initial administrator provisioned");
    }
}
