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

@Service
@PreAuthorize("hasRole('ADMIN')")
public class UserService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private static final RowMapper<UserDtos.View> MAPPER =
            (rs, n) ->
                    new UserDtos.View(
                            rs.getLong("id"),
                            rs.getString("username"),
                            Role.valueOf(rs.getString("role")),
                            rs.getBoolean("active"),
                            rs.getTimestamp("created_at").toInstant());

    public UserService(JdbcTemplate jdbc, PasswordEncoder encoder, AuditService audit) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserDtos.View> list(int page, int size, String query, Boolean active) {
        if (page < 0 || size < 1 || size > 100)
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PAGE",
                    "Page must be >= 0 and size between 1 and 100");
        var search = "%" + (query == null ? "" : query.toLowerCase(Locale.ROOT)) + "%";
        var sql =
                " FROM app_user WHERE lower(username) LIKE ? AND (CAST(? AS boolean) IS NULL OR"
                    + " active=?)";
        var content =
                jdbc.query(
                        "SELECT *" + sql + " ORDER BY username LIMIT ? OFFSET ?",
                        MAPPER,
                        search,
                        active,
                        active,
                        size,
                        (long) page * size);
        var count =
                jdbc.queryForObject("SELECT count(*)" + sql, Long.class, search, active, active);
        long total = count == null ? 0 : count;
        return new PageResponse<>(content, page, size, total, (int) ((total + size - 1) / size));
    }

    @Transactional
    public UserDtos.View create(UserDtos.Create request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PASSWORD",
                    "Password must not exceed 72 UTF-8 bytes");
        var username = request.username().toLowerCase(Locale.ROOT);
        var created =
                jdbc.queryForObject(
                        "INSERT INTO app_user(username,password_hash,role) VALUES (?,?,?) RETURNING"
                            + " *",
                        MAPPER,
                        username,
                        encoder.encode(request.password()),
                        request.role().name());
        audit.record(
                "USER_CREATED",
                "USER",
                Long.toString(created.id()),
                "User " + created.username() + " role=" + created.role());
        return created;
    }

    @Transactional
    public UserDtos.View update(long id, UserDtos.Update request) {
        // Serialize administrative role changes so a concurrent pair cannot remove the last
        // administrator.
        jdbc.execute("SELECT pg_advisory_xact_lock(74100)");
        var user = findLocked(id);
        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        if (user.username().equals(actor) && (!request.active() || request.role() != Role.ADMIN)) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "SELF_ADMIN_CHANGE",
                    "Administrators cannot deactivate or demote themselves");
        }
        if (user.active()
                && user.role() == Role.ADMIN
                && (!request.active() || request.role() != Role.ADMIN)) {
            Long admins =
                    jdbc.queryForObject(
                            "SELECT count(*) FROM app_user WHERE active AND role='ADMIN'",
                            Long.class);
            if (admins == null || admins <= 1)
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "LAST_ADMIN",
                        "At least one active administrator is required");
        }
        jdbc.update(
                "UPDATE app_user SET role=?,active=? WHERE id=?",
                request.role().name(),
                request.active(),
                id);
        if (!request.active())
            jdbc.update(
                    "UPDATE auth_session SET revoked_at=coalesce(revoked_at,current_timestamp)"
                        + " WHERE user_id=?",
                    id);
        audit.record(
                "USER_UPDATED",
                "USER",
                Long.toString(id),
                "role=" + request.role() + ", active=" + request.active());
        return jdbc.queryForObject("SELECT * FROM app_user WHERE id=?", MAPPER, id);
    }

    @Transactional
    public void deactivate(long id) {
        jdbc.execute("SELECT pg_advisory_xact_lock(74100)");
        var user = findLocked(id);
        update(id, new UserDtos.Update(user.role(), false));
    }

    private UserDtos.View findLocked(long id) {
        var users = jdbc.query("SELECT * FROM app_user WHERE id=? FOR UPDATE", MAPPER, id);
        if (users.isEmpty())
            throw new BusinessException(
                    HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User does not exist");
        return users.getFirst();
    }
}
