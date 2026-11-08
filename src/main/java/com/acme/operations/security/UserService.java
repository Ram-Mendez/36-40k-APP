package com.acme.operations.security;

import com.acme.operations.common.AuditService;
import com.acme.operations.common.BusinessException;
import com.acme.operations.common.PageResponse;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

// Spring registra esta clase como servicio y permite inyectarla; las reglas las implementan sus métodos.
@Service
// Spring Security exige ADMIN al entrar por el proxy antes de ejecutar esta operación.
@PreAuthorize("hasRole('ADMIN')")
public class UserService {
    // JdbcTemplate ejecuta nuestro SQL y adapta filas; no decide las reglas de negocio.
    private final JdbcTemplate jdbc;
    // Codificador de contraseñas inyectado por Spring para no guardar contraseñas en claro.
    private final PasswordEncoder encoder;
    // Servicio de auditoría compartido para registrar las acciones de esta operación.
    private final AuditService audit;
    // Mapper compartido: devuelve datos públicos del usuario, nunca su password_hash.
    private static final RowMapper<UserDtos.View> MAPPER =
            (rs, n) ->
                    new UserDtos.View(
                            rs.getLong("id"),
                            rs.getString("username"),
                            // Convierte el texto persistido al enum Role utilizado por la aplicación.
                            Role.valueOf(rs.getString("role")),
                            rs.getBoolean("active"),
                            rs.getTimestamp("created_at").toInstant());

    // Spring suministra las dependencias a este único constructor; no hace falta escribir @Autowired.
    public UserService(JdbcTemplate jdbc, PasswordEncoder encoder, AuditService audit) {
        // Conserva la referencia recibida para que los métodos puedan reutilizar esta dependencia.
        this.jdbc = jdbc;
        // Conserva la referencia recibida para que los métodos puedan reutilizar esta dependencia.
        this.encoder = encoder;
        // Conserva la referencia recibida para que los métodos puedan reutilizar esta dependencia.
        this.audit = audit;
    }

    // Spring configura lectura por defecto; readOnly orienta la persistencia, no sustituye permisos.
    @Transactional(readOnly = true)
    // Lista cuentas con filtros opcionales y metadatos de paginación.
    public PageResponse<UserDtos.View> list(int page, int size, String query, Boolean active) {
        // Valida límites antes de ejecutar consultas que podrían devolver demasiadas filas.
        if (page < 0 || size < 1 || size > 100)
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PAGE",
                    "Page must be >= 0 and size between 1 and 100");
        // Forma un patrón LIKE; aquí % y _ de la entrada conservan su significado de comodín.
        var search = "%" + (query == null ? "" : query.toLowerCase(Locale.ROOT)) + "%";
        // El mismo fragmento de filtros se usa para listar y contar.
        var sql =
                " FROM app_user WHERE lower(username) LIKE ? AND (CAST(? AS boolean) IS NULL OR"
                    + " active=?)";
        // Ordena por usuario y aplica size como LIMIT y page*size como OFFSET.
        var content =
                jdbc.query(
                        "SELECT *" + sql + " ORDER BY username LIMIT ? OFFSET ?",
                        MAPPER,
                        search,
                        active,
                        active,
                        size,
                        (long) page * size);
        // Cuenta todas las coincidencias para informar del número total de páginas.
        var count =
                jdbc.queryForObject("SELECT count(*)" + sql, Long.class, search, active, active);
        // Normaliza un conteo ausente a cero antes de construir la respuesta.
        long total = count == null ? 0 : count;
        // Devuelve datos y totales; la división redondea hacia arriba las páginas.
        return new PageResponse<>(content, page, size, total, (int) ((total + size - 1) / size));
    }

    // Al entrar por el proxy, Spring abre o comparte transacción; confirma al terminar y revierte ante errores no
    // comprobados.
    @Transactional
    // Crea una cuenta; la autorización ADMIN está declarada sobre esta clase.
    public UserDtos.View create(UserDtos.Create request) {
        // Comprueba el máximo de bytes de BCrypt además de las validaciones del DTO.
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PASSWORD",
                    "Password must not exceed 72 UTF-8 bytes");
        // Normaliza el nombre para mantener la misma comparación que el login.
        var username = request.username().toLowerCase(Locale.ROOT);
        // Inserta el usuario y mapea la fila devuelta por RETURNING; solo guarda la contraseña codificada.
        var created =
                jdbc.queryForObject(
                        "INSERT INTO app_user(username,password_hash,role) VALUES (?,?,?) RETURNING"
                            + " *",
                        MAPPER,
                        username,
                        encoder.encode(request.password()),
                        request.role().name());
        // Registra quién creó la cuenta y qué rol se asignó.
        audit.record(
                "USER_CREATED",
                "USER",
                Long.toString(created.id()),
                "User " + created.username() + " role=" + created.role());
        // Devuelve la vista pública de la cuenta recién creada.
        return created;
    }

    // Al entrar por el proxy, Spring abre o comparte transacción; confirma al terminar y revierte ante errores no
    // comprobados.
    @Transactional
    // Cambia rol y estado protegiendo que siempre quede un administrador activo.
    public UserDtos.View update(long id, UserDtos.Update request) {
        // El bloqueo asesor dura hasta terminar la transacción y serializa cambios administrativos.
        jdbc.execute("SELECT pg_advisory_xact_lock(74100)");
        // Bloquea también la fila del usuario que se va a modificar.
        var user = findLocked(id);
        // Obtiene el nombre del administrador autenticado que está ejecutando la operación.
        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        // Impide que el administrador se desactive o se quite a sí mismo su rol.
        if (user.username().equals(actor) && (!request.active() || request.role() != Role.ADMIN)) {
            // 409 indica que ese cambio administrativo no está permitido.
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "SELF_ADMIN_CHANGE",
                    "Administrators cannot deactivate or demote themselves");
        }
        // Si se pretende quitar un administrador activo, cuenta cuántos quedan.
        if (user.active()
                && user.role() == Role.ADMIN
                && (!request.active() || request.role() != Role.ADMIN)) {
            // Cuenta los administradores mientras el bloqueo asesor evita modificaciones rivales.
            Long admins =
                    jdbc.queryForObject(
                            "SELECT count(*) FROM app_user WHERE active AND role='ADMIN'",
                            Long.class);
            // No permite retirar al último administrador activo.
            if (admins == null || admins <= 1)
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "LAST_ADMIN",
                        "At least one active administrator is required");
        }
        // Un cambio administrativo puede omitir el nombre; en ese caso conserva el valor bloqueado.
        String username =
                request.username() == null
                        ? user.username()
                        : request.username().toLowerCase(Locale.ROOT);
        // Guarda los nuevos valores solo después de pasar todas las reglas.
        jdbc.update(
                "UPDATE app_user SET role=?,username=?,active=? WHERE id=?",
                request.role().name(),
                username,
                request.active(),
                id);
        // Si se desactiva la cuenta también se revocan sus sesiones.
        if (!request.active())
            jdbc.update(
                    "UPDATE auth_session SET revoked_at=coalesce(revoked_at,current_timestamp)"
                        + " WHERE user_id=?",
                    id);
        // Audita el cambio dentro de la misma transacción.
        audit.record(
                "USER_UPDATED",
                "USER",
                Long.toString(id),
                "role=" + request.role() + ", active=" + request.active());
        // Consulta el estado final para devolver una vista actualizada.
        return jdbc.queryForObject("SELECT * FROM app_user WHERE id=?", MAPPER, id);
    }

    // Al entrar por el proxy, Spring abre o comparte transacción; confirma al terminar y revierte ante errores no
    // comprobados.
    @Transactional
    // Reutiliza las mismas reglas de update para desactivar, conservando el rol.
    public void deactivate(long id) {
        // Adquiere el bloqueo administrativo antes de leer el usuario.
        jdbc.execute("SELECT pg_advisory_xact_lock(74100)");
        // Lee el usuario protegido contra modificaciones simultáneas.
        var user = findLocked(id);
        // Es una llamada interna: no pasa de nuevo por el proxy; usa la transacción de deactivate.
        update(id, new UserDtos.Update(user.role(), user.username(),false));
    }

    // Busca un usuario y bloquea su fila hasta que acabe la transacción del llamador.
    private UserDtos.View findLocked(long id) {
        var users = jdbc.query("SELECT * FROM app_user WHERE id=? FOR UPDATE", MAPPER, id);
        // Una consulta sin filas se convierte en error 404.
        if (users.isEmpty())
            throw new BusinessException(
                    HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User does not exist");
        // Devuelve el único usuario encontrado.
        return users.getFirst();
    }
}
