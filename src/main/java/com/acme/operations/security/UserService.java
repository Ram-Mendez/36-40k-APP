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

// Spring registers this class as a service. Its methods implement the business rules.
@Service
// Spring Security checks for the ADMIN role before running this operation.
@PreAuthorize("hasRole('ADMIN')")
public class UserService {
    // JdbcTemplate executes the SQL and maps the result rows.
    private final JdbcTemplate jdbc;
    // Password encoding injected by Spring to avoid saving clear passwords.
    private final PasswordEncoder encoder;
    // Use the shared audit service to record this operation.
    private final AuditService audit;
    // Map public user fields. Keep password_hash out of the response.
    private static final RowMapper<UserDtos.View> MAPPER =
            (rs, n) ->
                    new UserDtos.View(
                            rs.getLong("id"),
                            rs.getString("username"),
                            // Convert the text persisting to the enum Role used by the application.
                            Role.valueOf(rs.getString("role")),
                            rs.getBoolean("active"),
                            rs.getTimestamp("created_at").toInstant());

    // Spring injects the dependencies through the single constructor.
    public UserService(JdbcTemplate jdbc, PasswordEncoder encoder, AuditService audit) {
        // Keep the injected dependency for later calls.
        this.jdbc = jdbc;
        // Keep the injected dependency for later calls.
        this.encoder = encoder;
        // Keep the injected dependency for later calls.
        this.audit = audit;
    }

    // Use read-only transactions by default. Spring Security checks access separately.
    @Transactional(readOnly = true)
    // List counts with optional filters and pagination metadata.
    public PageResponse<UserDtos.View> list(int page, int size, String query, Boolean active) {
        // Valid limits before running consultations that could return too many rows.
        if (page < 0 || size < 1 || size > 100)
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PAGE",
                    "Page must be >= 0 and size between 1 and 100");
        // Build a LIKE pattern. This query treats % and _ as SQL wildcards.
        var search = "%" + (query == null ? "" : query.toLowerCase(Locale.ROOT)) + "%";
        // The same filter fragment is used to list and count.
        var sql =
                " FROM app_user WHERE lower(username) LIKE ? AND (CAST(? AS boolean) IS NULL OR"
                    + " active=?)";
        // Order per user and apply size as LIMIT and page * size as OFSET.
        var content =
                jdbc.query(
                        "SELECT *" + sql + " ORDER BY username LIMIT ? OFFSET ?",
                        MAPPER,
                        search,
                        active,
                        active,
                        size,
                        (long) page * size);
        // It counts all the matches to report the total number of pages.
        var count =
                jdbc.queryForObject("SELECT count(*)" + sql, Long.class, search, active, active);
        // Normalize an absent count to zero before building the response.
        long total = count == null ? 0 : count;
        // Returns data and totals; the division round up the pages.
        return new PageResponse<>(content, page, size, total, (int) ((total + size - 1) / size));
    }

    // Spring starts or joins a transaction when the call goes through the proxy. It commits on success and rolls
    // back on unchecked exceptions.
    @Transactional
    // It creates an account; the ADMIN authorization is declared on this class.
    public UserDtos.View create(UserDtos.Create request) {
        // Check the maximum of BCrypt bytes in addition to the DTO validation.
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72)
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PASSWORD",
                    "Password must not exceed 72 UTF-8 bytes");
        // Normalize the name to maintain the same comparison as the login.
        var username = request.username().toLowerCase(Locale.ROOT);
        // Insert the user and map the row returned by RETURNING; only save the coded password.
        var created =
                jdbc.queryForObject(
                        "INSERT INTO app_user(username,password_hash,role) VALUES (?,?,?) RETURNING"
                            + " *",
                        MAPPER,
                        username,
                        encoder.encode(request.password()),
                        request.role().name());
        // He records who created the account and what role was assigned.
        audit.record(
                "USER_CREATED",
                "USER",
                Long.toString(created.id()),
                "User " + created.username() + " role=" + created.role());
        // Returns the public view of the newly created account.
        return created;
    }

    // Spring starts or joins a transaction when the call goes through the proxy. It commits on success and rolls
    // back on unchecked exceptions.
    @Transactional
    // It changes role and is protecting an active administrator.
    public UserDtos.View update(long id, UserDtos.Update request) {
        // The transaction holds this advisory lock to serialize administrative changes.
        jdbc.execute("SELECT pg_advisory_xact_lock(74100)");
        // It also blocks the row of the user to be modified.
        var user = findLocked(id);
        // You get the name of the authenticated administrator who is running the operation.
        String actor = SecurityContextHolder.getContext().getAuthentication().getName();
        // It prevents the administrator from deactivating or taking away his or her role.
        if (user.username().equals(actor) && (!request.active() || request.role() != Role.ADMIN)) {
            // 409 indicates that such administrative change is not permitted.
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "SELF_ADMIN_CHANGE",
                    "Administrators cannot deactivate or demote themselves");
        }
        // If you want to remove an active administrator, tell how many are left.
        if (user.active()
                && user.role() == Role.ADMIN
                && (!request.active() || request.role() != Role.ADMIN)) {
            // Count administrators while the advisory lock prevents concurrent role changes.
            Long admins =
                    jdbc.queryForObject(
                            "SELECT count(*) FROM app_user WHERE active AND role='ADMIN'",
                            Long.class);
            // It does not allow the last active administrator to be removed.
            if (admins == null || admins <= 1)
                throw new BusinessException(
                        HttpStatus.CONFLICT,
                        "LAST_ADMIN",
                        "At least one active administrator is required");
        }
        // An administrative change may omit the name; in that case it retains the blocked value.
        String username =
                request.username() == null
                        ? user.username()
                        : request.username().toLowerCase(Locale.ROOT);
        // Save the new values only after passing all the rules.
        jdbc.update(
                "UPDATE app_user SET role=?,username=?,active=? WHERE id=?",
                request.role().name(),
                username,
                request.active(),
                id);
        // If the account is disabled, its meetings are also revoked.
        if (!request.active())
            jdbc.update(
                    "UPDATE auth_session SET revoked_at=coalesce(revoked_at,current_timestamp)"
                        + " WHERE user_id=?",
                    id);
        // Audit the change within the same transaction.
        audit.record(
                "USER_UPDATED",
                "USER",
                Long.toString(id),
                "role=" + request.role() + ", active=" + request.active());
        // Check the final state to return an updated view.
        return jdbc.queryForObject("SELECT * FROM app_user WHERE id=?", MAPPER, id);
    }

    // Spring starts or joins a transaction when the call goes through the proxy. It commits on success and rolls
    // back on unchecked exceptions.
    @Transactional
    // Reuse the same update rules to disable, retaining the role.
    public void deactivate(long id) {
        // Get the administrative lock before reading the user.
        jdbc.execute("SELECT pg_advisory_xact_lock(74100)");
        // Read the user protected against simultaneous modifications.
        var user = findLocked(id);
        // It's an internal call: it doesn't go through the proxy again; it uses the deactivate transaction.
        update(id, new UserDtos.Update(user.role(), user.username(),false));
    }

    // Find a user and lock your line until the call transaction is over.
    private UserDtos.View findLocked(long id) {
        var users = jdbc.query("SELECT * FROM app_user WHERE id=? FOR UPDATE", MAPPER, id);
        // A no-row query becomes a 404 error.
        if (users.isEmpty())
            throw new BusinessException(
                    HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User does not exist");
        // Returns the only user found.
        return users.getFirst();
    }
}
