package com.acme.operations.security;

import com.acme.operations.common.AuditService;
import com.acme.operations.common.BusinessException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

// Spring registra esta clase como servicio y permite inyectarla; las reglas las implementan sus métodos.
@Service
public class AuthService {
    // Ejecuta SQL parametrizado usando la conexión asociada a la transacción actual.
    private final JdbcTemplate jdbc;
    // Verifica y genera hashes de contraseña; nunca compara contraseñas guardadas en claro.
    private final PasswordEncoder passwords;
    // Firma los access tokens con la configuración JWT de Spring.
    private final JwtEncoder encoder;
    // Delimita transacciones explícitas en las funciones execute y executeWithoutResult.
    private final TransactionTemplate transactions;
    // Registra acciones de seguridad junto con los cambios de sesión.
    private final AuditService audit;
    // Duración del access token; determina expiresIn, expresado en segundos.
    private final Duration accessTtl;
    // Duración máxima de la familia de refresh tokens.
    private final Duration refreshTtl;
    // Generador criptográfico para que el refresh token sea impredecible.
    private final SecureRandom random = new SecureRandom();
    // Hash ficticio usado para verificar también cuando el usuario no existe.
    private final String dummyHash;

    // Spring inyecta colaboradores y duraciones en este único constructor.
    public AuthService(
            JdbcTemplate jdbc,
            PasswordEncoder passwords,
            JwtEncoder encoder,
            TransactionTemplate transactions,
            AuditService audit,
            // Spring lee las duraciones de configuración; PT15M y P7D son los valores por defecto.
            @Value("${security.jwt.access-ttl:PT15M}") Duration accessTtl,
            @Value("${security.jwt.refresh-ttl:P7D}") Duration refreshTtl) {
        // Guarda las dependencias recibidas; final impide reemplazar estas referencias después.
        this.jdbc = jdbc;
        // Conserva el codificador compartido de contraseñas.
        this.passwords = passwords;
        // Conserva el firmador de JWT configurado por Spring.
        this.encoder = encoder;
        // Conserva la plantilla que delimita las transacciones de autenticación.
        this.transactions = transactions;
        // Conserva la auditoría para registrar cambios dentro de la transacción.
        this.audit = audit;
        // Guarda la duración de acceso y la duración máxima de renovación.
        this.accessTtl = accessTtl;
        // Guarda cuánto puede durar una familia de refresh tokens.
        this.refreshTtl = refreshTtl;
        // Las dos duraciones deben ser positivas para no emitir tokens ya caducados.
        if (accessTtl.isNegative()
                || accessTtl.isZero()
                || refreshTtl.isNegative()
                || refreshTtl.isZero()) {
            // Una configuración inválida impide construir el servicio y arrancar correctamente.
            throw new IllegalArgumentException("Token lifetimes must be positive");
        }
        // Prepara el hash ficticio una vez; reduce diferencias de tiempo al buscar usuarios inexistentes.
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    // Autentica las credenciales y crea una sesión persistida con sus tokens.
    public AuthDtos.Tokens login(String username, String password) {
        // TransactionTemplate ejecuta este bloque en transacción; los errores de ejecución provocan rollback.
        return transactions.execute(
                status -> {
                    // Lee y bloquea el usuario para coordinar login con cambios administrativos de esa cuenta.
                    var users =
                            jdbc.query(
                                    "SELECT id,username,password_hash,active FROM app_user WHERE"
                                        + " username=? FOR UPDATE",
                                    // El mapper construye LoginUser a partir de las columnas devueltas.
                                    (rs, n) ->
                                            new LoginUser(
                                                    rs.getLong("id"),
                                                    rs.getString("username"),
                                                    rs.getString("password_hash"),
                                                    rs.getBoolean("active")),
                                    // Normaliza el nombre antes de asignarlo al ? del SQL.
                                    username.toLowerCase(Locale.ROOT));
                    // Representa un usuario ausente con null para verificar después sin revelar si existe.
                    var user = users.isEmpty() ? null : users.getFirst();
                    // Guardará el resultado de la comparación de contraseña.
                    boolean matches;
                    // Convierte errores del codificador en credenciales rechazadas.
                    try {
                        // Compara con el hash real o el ficticio; no descifra ninguna contraseña.
                        matches =
                                passwords.matches(password, user == null ? dummyHash : user.hash());
                    // Un formato que el codificador no acepte también cuenta como contraseña incorrecta.
                    } catch (IllegalArgumentException exception) {
                        matches = false;
                    }
                    // Rechaza usuario ausente, contraseña incorrecta o cuenta inactiva con el mismo error.
                    if (user == null || !matches || !user.active())
                        throw unauthorized("INVALID_CREDENTIALS", "Invalid username or password");
                    // Emite tokens para una familia nueva cuya caducidad se fija ahora.
                    var tokens =
                            issue(
                                    user.id(),
                                    user.username(),
                                    UUID.randomUUID(),
                                    Instant.now().plus(refreshTtl));
                    // Audita el login con el usuario explícito porque todavía no hay identidad autenticada.
                    audit.recordAs(
                            user.username(),
                            "LOGIN",
                            "USER",
                            Long.toString(user.id()),
                            "Session created");
                    // Devuelve los tokens; execute confirma la transacción antes de entregarlos al llamador.
                    return tokens;
                });
    }

    // Rota un refresh token: invalida el anterior y entrega uno nuevo de la misma familia.
    public AuthDtos.Tokens refresh(String token) {
        // Devuelve el error como dato para confirmar la revocación antes de lanzar el 401 fuera del bloque.
        var result =
                transactions.execute(
                        status -> {
                            // Localiza el usuario por el hash del token; la base no almacena el refresh token en claro.
                            var userIds =
                                    jdbc.query(
                                            "SELECT user_id FROM auth_session WHERE refresh_hash=?",
                                            (rs, n) -> rs.getLong(1),
                                            hash(token));
                            // Un token desconocido se rechaza sin crear una nueva sesión.
                            if (userIds.isEmpty())
                                return new RefreshResult(null, "INVALID_REFRESH_TOKEN");
                            // Bloquea primero al usuario para mantener el mismo orden que las demás operaciones de sesión.
                            jdbc.queryForObject(
                                    "SELECT id FROM app_user WHERE id=? FOR UPDATE",
                                    Long.class,
                                    userIds.getFirst());
                            // Después bloquea la sesión y lee sus fechas y el estado del usuario.
                            var sessions =
                                    jdbc.query(
                                            "SELECT"
                                                + " s.id,s.family_id,s.user_id,s.expires_at,s.revoked_at,s.used_at,u.username,u.active"
                                                + " FROM auth_session s JOIN app_user u ON"
                                                + " u.id=s.user_id WHERE s.refresh_hash=? FOR"
                                                + " UPDATE OF s",
                                            // Convierte fechas y marcas de uso o revocación en el record Session.
                                            (rs, n) ->
                                                    new Session(
                                                            rs.getObject("id", UUID.class),
                                                            rs.getObject("family_id", UUID.class),
                                                            rs.getLong("user_id"),
                                                            rs.getTimestamp("expires_at")
                                                                    .toInstant(),
                                                            rs.getTimestamp("revoked_at") != null,
                                                            rs.getTimestamp("used_at") != null,
                                                            rs.getString("username"),
                                                            rs.getBoolean("active")),
                                            hash(token));
                            // Si la sesión ya no aparece, devuelve el resultado de rechazo.
                            if (sessions.isEmpty())
                                return new RefreshResult(null, "INVALID_REFRESH_TOKEN");
                            // Selecciona la sesión encontrada para decidir si aún puede rotarse.
                            var session = sessions.getFirst();
                            // Un token usado otra vez indica replay: se invalida toda su familia.
                            if (session.used()) {
                                // La revocación también alcanza los tokens emitidos anteriormente en esa familia.
                                revokeFamily(session.familyId());
                                // Registra la reutilización para que pueda investigarse en auditoría.
                                audit.recordAs(
                                        session.username(),
                                        "REFRESH_REPLAY",
                                        "USER",
                                        Long.toString(session.userId()),
                                        "Session family revoked");
                                // Sale del bloque sin lanzar todavía: así la revocación sí se confirma.
                                return new RefreshResult(null, "REFRESH_REPLAY");
                            }
                            // Una sesión revocada, vencida o de usuario inactivo no puede renovarse.
                            if (session.revoked()
                                    || !session.active()
                                    || !session.expiresAt().isAfter(Instant.now())) {
                                return new RefreshResult(null, "INVALID_REFRESH_TOKEN");
                            }
                            // Marca el token actual como usado y revocado antes de emitir su sustituto.
                            jdbc.update(
                                    "UPDATE auth_session SET"
                                        + " used_at=current_timestamp,revoked_at=current_timestamp"
                                        + " WHERE id=?",
                                    session.id());
                            // Emite la nueva sesión manteniendo familia y caducidad originales; no prolonga su vida máxima.
                            return new RefreshResult(
                                    issue(
                                            session.userId(),
                                            session.username(),
                                            session.familyId(),
                                            session.expiresAt()),
                                    null);
                        });
        // Ya fuera del bloque transaccional, transforma el resultado de rechazo en HTTP 401.
        if (result == null || result.error() != null)
            throw unauthorized(
                    result == null ? "INVALID_REFRESH_TOKEN" : result.error(),
                    "Refresh token rejected; sign in again");
        // Entrega los tokens recién emitidos cuando la rotación terminó correctamente.
        return result.tokens();
    }

    // Spring exige que exista una identidad autenticada antes de entrar al método.
    @PreAuthorize("isAuthenticated()")
    // Cierra la familia de sesiones a la que pertenece el JWT autenticado.
    public void logout(String sessionId) {
        // Agrupa bloqueo, revocación y auditoría en una transacción sin valor de retorno.
        transactions.executeWithoutResult(
                status -> {
                    // Obtiene el dueño de la sesión que llegó desde el principal JWT.
                    var userId =
                            jdbc.queryForObject(
                                    "SELECT user_id FROM auth_session WHERE id=?",
                                    Long.class,
                                    UUID.fromString(sessionId));
                    // Bloquea al usuario antes de mutar sus sesiones, igual que refresh y administración.
                    jdbc.queryForObject(
                            "SELECT id FROM app_user WHERE id=? FOR UPDATE", Long.class, userId);
                    // Obtiene el identificador de la familia de sesiones.
                    var family =
                            jdbc.queryForObject(
                                    "SELECT family_id FROM auth_session WHERE id=?",
                                    UUID.class,
                                    UUID.fromString(sessionId));
                    // Actualiza las sesiones asociadas y conserva su historial.
                    jdbc.update(
                             "UPDATE auth_session SET revoked_at=coalesce(revoked_at,current_timestamp)"
                                 + " WHERE family_id=? AND used_at IS NOT NULL",
                             family);
                    // Deja registro del cierre dentro de la misma transacción.
                    audit.record("LOGOUT", "SESSION", sessionId, "Session family revoked");
                });
    }

    // Solo la cuenta autenticada puede cambiar su propia contraseña.
    @PreAuthorize("isAuthenticated() and #username == authentication.name")
    public void changePassword(String username, AuthDtos.ChangePassword request) {
        // BCrypt admite como máximo 72 bytes, aunque el DTO ya limite el número de caracteres.
        if (request.newPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PASSWORD",
                    "Password exceeds 72 UTF-8 bytes");
        }

        // El bloqueo coordina el cambio con login, refresh y las operaciones administrativas.
        transactions.executeWithoutResult(
                status -> {
                    var users =
                            jdbc.query(
                                    "SELECT id,username,password_hash,active FROM app_user WHERE"
                                        + " username=? FOR UPDATE",
                                    (rs, n) ->
                                            new LoginUser(
                                                    rs.getLong("id"),
                                                    rs.getString("username"),
                                                    rs.getString("password_hash"),
                                                    rs.getBoolean("active")),
                                    username);
                    var user = users.isEmpty() ? null : users.getFirst();

                    boolean matches;
                    try {
                        matches =
                                user != null
                                        && user.active()
                                        && passwords.matches(
                                                request.currentPassword(), user.hash());
                    } catch (IllegalArgumentException exception) {
                        matches = false;
                    }
                    if (!matches) {
                        throw unauthorized(
                                "INVALID_CREDENTIALS", "Current password is invalid");
                    }

                    // Actualiza el hash y revoca todas las sesiones como una sola operación atómica.
                    jdbc.update(
                            "UPDATE app_user SET password_hash=? WHERE id=?",
                            passwords.encode(request.newPassword()),
                            user.id());
                    jdbc.update(
                            "UPDATE auth_session SET"
                                + " revoked_at=coalesce(revoked_at,current_timestamp) WHERE user_id=?",
                            user.id());
                    audit.record(
                            "PASSWORD_CHANGED",
                            "USER",
                            Long.toString(user.id()),
                            "Password changed; sessions revoked");
                });
    }


    @PreAuthorize("isAuthenticated() and #username == authentication.name")
    public void updateUser(String username, AuthDtos.UpdateUser request) {

        // BCrypt admite como máximo 72 bytes.
        if (request.newPassword()
                .getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PASSWORD",
                    "Password exceeds 72 UTF-8 bytes");
        }

        // Agrupa todos los cambios en una transacción.
        transactions.executeWithoutResult(status -> {

            // Bloquea la fila del usuario hasta terminar.
            var users = jdbc.query(
                    "SELECT id,username,password_hash,active " +
                            "FROM app_user WHERE username=? FOR UPDATE",
                    (rs, n) -> new LoginUser(
                            rs.getLong("id"),
                            rs.getString("username"),
                            rs.getString("password_hash"),
                            rs.getBoolean("active")),
                    username);

            // Recupera el usuario encontrado.
            var user = users.isEmpty() ? null : users.getFirst();

            // Comprueba la contraseña actual.
            boolean matches;
            try {
                matches = user != null
                        && user.active()
                        && passwords.matches(
                        request.currentPassword(), user.hash());
            } catch (IllegalArgumentException exception) {
                matches = false;
            }

            // Rechaza el cambio si la contraseña no coincide.
            if (!matches) {
                throw unauthorized(
                        "INVALID_CREDENTIALS",
                        "Current password is invalid");
            }

            // Normaliza el nombre para guardarlo en minúsculas.
            String newUsername =
                    request.username().toLowerCase(Locale.ROOT);

            // Actualiza nombre y contraseña en una única operación.
            jdbc.update(
                    "UPDATE app_user " +
                            "SET username=?, password_hash=? WHERE id=?",
                    newUsername,
                    passwords.encode(request.newPassword()),
                    user.id());

            // Invalida todas las sesiones anteriores.
            jdbc.update(
                    "UPDATE auth_session " +
                            "SET revoked_at=coalesce(revoked_at,current_timestamp) " +
                            "WHERE user_id=?",
                    user.id());

            // Audita el cambio sin registrar contraseñas.
            audit.record(
                    "USER_UPDATED",
                    "USER",
                    Long.toString(user.id()),
                    "Profile updated; sessions revoked");
        });
    }


    // Revoca todas las sesiones de la familia conservando una fecha previa de revocación.
    private void revokeFamily(UUID familyId) {
        jdbc.update(
                "UPDATE auth_session SET revoked_at=coalesce(revoked_at,current_timestamp) WHERE"
                    + " family_id=?",
                familyId);
    }

    // Crea una sesión y los dos tokens; lo llaman bloques que ya tienen transacción.
    private AuthDtos.Tokens issue(
            long userId, String username, UUID family, Instant refreshExpiry) {
        // Genera el id que vinculará el JWT con auth_session.
        var sessionId = UUID.randomUUID();
        // Reserva 48 bytes y los rellena con aleatoriedad criptográfica.
        var bytes = new byte[48];
        random.nextBytes(bytes);
        // Codifica esos bytes como texto apto para URL, sin relleno Base64.
        var refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        // Persiste la sesión y el hash del refresh; el token original solo se devuelve al cliente.
        jdbc.update(
                "INSERT INTO auth_session(id,user_id,family_id,refresh_hash,expires_at) VALUES"
                    + " (?,?,?,?,?)",
                sessionId,
                userId,
                family,
                hash(refresh),
                Timestamp.from(refreshExpiry));
        // Usa el mismo instante como referencia para emisión y caducidad del JWT.
        var now = Instant.now();
        // Construye los claims: emisor, usuario, fechas, identificador y vínculo con la sesión.
        var claims =
                JwtClaimsSet.builder()
                        .issuer("business-operations")
                        .subject(username)
                        .issuedAt(now)
                        .expiresAt(now.plus(accessTtl))
                        // jti identifica el token; uid y sid enlazan con usuario y sesión.
                        .id(UUID.randomUUID().toString())
                        .claim("uid", userId)
                        .claim("sid", sessionId.toString())
                        .build();
        // El encoder firma los claims con HS256; firmar no equivale a cifrar el contenido.
        var jwt =
                encoder.encode(
                        JwtEncoderParameters.from(
                                JwsHeader.with(MacAlgorithm.HS256).build(), claims));
        // Devuelve access token, refresh token, esquema Bearer y duración del acceso en segundos.
        return new AuthDtos.Tokens(jwt.getTokenValue(), refresh, "Bearer", accessTtl.toSeconds());
    }

    // Calcula un hash SHA-256 para buscar el refresh sin guardar su valor original.
    static String hash(String value) {
        // Gestiona la excepción del proveedor criptográfico, no un rechazo de credenciales.
        try {
            // Convierte el resumen binario en hexadecimal para almacenarlo y compararlo.
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        // Si SHA-256 no estuviera disponible, es un fallo interno de configuración o plataforma.
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    // Construye la excepción 401; ApiExceptionHandler la convierte después en respuesta HTTP.
    private static BusinessException unauthorized(String code, String detail) {
        return new BusinessException(HttpStatus.UNAUTHORIZED, code, detail);
    }

    // Datos internos de login; el hash no se incluye en la respuesta de tokens.
    private record LoginUser(long id, String username, String hash, boolean active) {}

    // Foto de una sesión leída bajo bloqueo para decidir si puede renovarse.
    private record Session(
            // Identificadores de esta sesión, su familia de rotación y el usuario propietario.
            UUID id,
            UUID familyId,
            long userId,
            // Fecha límite y marcas que determinan si se permite renovar.
            Instant expiresAt,
            boolean revoked,
            boolean used,
            // Identidad y estado actual del usuario asociado.
            String username,
            boolean active) {}

    // Transporta tokens o un código de error fuera del bloque transaccional.
    private record RefreshResult(AuthDtos.Tokens tokens, String error) {}
}
