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

@Service
public class AuthService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final JwtEncoder encoder;
    private final TransactionTemplate transactions;
    private final AuditService audit;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final SecureRandom random = new SecureRandom();
    private final String dummyHash;

    public AuthService(
            JdbcTemplate jdbc,
            PasswordEncoder passwords,
            JwtEncoder encoder,
            TransactionTemplate transactions,
            AuditService audit,
            @Value("${security.jwt.access-ttl:PT15M}") Duration accessTtl,
            @Value("${security.jwt.refresh-ttl:P7D}") Duration refreshTtl) {
        this.jdbc = jdbc;
        this.passwords = passwords;
        this.encoder = encoder;
        this.transactions = transactions;
        this.audit = audit;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
        if (accessTtl.isNegative()
                || accessTtl.isZero()
                || refreshTtl.isNegative()
                || refreshTtl.isZero()) {
            throw new IllegalArgumentException("Token lifetimes must be positive");
        }
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    public AuthDtos.Tokens login(String username, String password) {
        return transactions.execute(
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
                                    username.toLowerCase(Locale.ROOT));
                    var user = users.isEmpty() ? null : users.getFirst();
                    boolean matches;
                    try {
                        matches =
                                passwords.matches(password, user == null ? dummyHash : user.hash());
                    } catch (IllegalArgumentException exception) {
                        matches = false;
                    }
                    if (user == null || !matches || !user.active())
                        throw unauthorized("INVALID_CREDENTIALS", "Invalid username or password");
                    var tokens =
                            issue(
                                    user.id(),
                                    user.username(),
                                    UUID.randomUUID(),
                                    Instant.now().plus(refreshTtl));
                    audit.recordAs(
                            user.username(),
                            "LOGIN",
                            "USER",
                            Long.toString(user.id()),
                            "Session created");
                    return tokens;
                });
    }

    public AuthDtos.Tokens refresh(String token) {
        // Return the failure from the transaction: replay revocation must commit before returning
        // 401.
        var result =
                transactions.execute(
                        status -> {
                            var userIds =
                                    jdbc.query(
                                            "SELECT user_id FROM auth_session WHERE refresh_hash=?",
                                            (rs, n) -> rs.getLong(1),
                                            hash(token));
                            if (userIds.isEmpty())
                                return new RefreshResult(null, "INVALID_REFRESH_TOKEN");
                            // Every session mutation locks its user first, matching user
                            // deactivation and avoiding inverted locks.
                            jdbc.queryForObject(
                                    "SELECT id FROM app_user WHERE id=? FOR UPDATE",
                                    Long.class,
                                    userIds.getFirst());
                            var sessions =
                                    jdbc.query(
                                            "SELECT"
                                                + " s.id,s.family_id,s.user_id,s.expires_at,s.revoked_at,s.used_at,u.username,u.active"
                                                + " FROM auth_session s JOIN app_user u ON"
                                                + " u.id=s.user_id WHERE s.refresh_hash=? FOR"
                                                + " UPDATE OF s",
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
                            if (sessions.isEmpty())
                                return new RefreshResult(null, "INVALID_REFRESH_TOKEN");
                            var session = sessions.getFirst();
                            if (session.used()) {
                                revokeFamily(session.familyId());
                                audit.recordAs(
                                        session.username(),
                                        "REFRESH_REPLAY",
                                        "USER",
                                        Long.toString(session.userId()),
                                        "Session family revoked");
                                return new RefreshResult(null, "REFRESH_REPLAY");
                            }
                            if (session.revoked()
                                    || !session.active()
                                    || !session.expiresAt().isAfter(Instant.now())) {
                                return new RefreshResult(null, "INVALID_REFRESH_TOKEN");
                            }
                            jdbc.update(
                                    "UPDATE auth_session SET"
                                        + " used_at=current_timestamp,revoked_at=current_timestamp"
                                        + " WHERE id=?",
                                    session.id());
                            return new RefreshResult(
                                    issue(
                                            session.userId(),
                                            session.username(),
                                            session.familyId(),
                                            session.expiresAt()),
                                    null);
                        });
        if (result == null || result.error() != null)
            throw unauthorized(
                    result == null ? "INVALID_REFRESH_TOKEN" : result.error(),
                    "Refresh token rejected; sign in again");
        return result.tokens();
    }

    @PreAuthorize("isAuthenticated()")
    public void logout(String sessionId) {
        transactions.executeWithoutResult(
                status -> {
                    var userId =
                            jdbc.queryForObject(
                                    "SELECT user_id FROM auth_session WHERE id=?",
                                    Long.class,
                                    UUID.fromString(sessionId));
                    jdbc.queryForObject(
                            "SELECT id FROM app_user WHERE id=? FOR UPDATE", Long.class, userId);
                    var family =
                            jdbc.queryForObject(
                                    "SELECT family_id FROM auth_session WHERE id=?",
                                    UUID.class,
                                    UUID.fromString(sessionId));
                    revokeFamily(family);
                    audit.record("LOGOUT", "SESSION", sessionId, "Session family revoked");
                });
    }

    @PreAuthorize("isAuthenticated() and #username == authentication.name")
    public void changePassword(String username, AuthDtos.PasswordChange request) {
        if (request.newPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PASSWORD",
                    "Password must not exceed 72 UTF-8 bytes");
        }
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
                    if (!matches)
                        throw unauthorized("INVALID_CREDENTIALS", "Current password is invalid");
                    jdbc.update(
                            "UPDATE app_user SET password_hash=? WHERE id=?",
                            passwords.encode(request.newPassword()),
                            user.id());
                    jdbc.update(
                            "UPDATE auth_session SET"
                                + " revoked_at=coalesce(revoked_at,current_timestamp) WHERE"
                                + " user_id=?",
                            user.id());
                    audit.record(
                            "PASSWORD_CHANGED",
                            "USER",
                            Long.toString(user.id()),
                            "All sessions revoked");
                });
    }

    private void revokeFamily(UUID familyId) {
        jdbc.update(
                "UPDATE auth_session SET revoked_at=coalesce(revoked_at,current_timestamp) WHERE"
                    + " family_id=?",
                familyId);
    }

    private AuthDtos.Tokens issue(
            long userId, String username, UUID family, Instant refreshExpiry) {
        var sessionId = UUID.randomUUID();
        var bytes = new byte[48];
        random.nextBytes(bytes);
        var refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        jdbc.update(
                "INSERT INTO auth_session(id,user_id,family_id,refresh_hash,expires_at) VALUES"
                    + " (?,?,?,?,?)",
                sessionId,
                userId,
                family,
                hash(refresh),
                Timestamp.from(refreshExpiry));
        var now = Instant.now();
        var claims =
                JwtClaimsSet.builder()
                        .issuer("business-operations")
                        .subject(username)
                        .issuedAt(now)
                        .expiresAt(now.plus(accessTtl))
                        .id(UUID.randomUUID().toString())
                        .claim("uid", userId)
                        .claim("sid", sessionId.toString())
                        .build();
        var jwt =
                encoder.encode(
                        JwtEncoderParameters.from(
                                JwsHeader.with(MacAlgorithm.HS256).build(), claims));
        return new AuthDtos.Tokens(jwt.getTokenValue(), refresh, "Bearer", accessTtl.toSeconds());
    }

    static String hash(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static BusinessException unauthorized(String code, String detail) {
        return new BusinessException(HttpStatus.UNAUTHORIZED, code, detail);
    }

    private record LoginUser(long id, String username, String hash, boolean active) {}

    private record Session(
            UUID id,
            UUID familyId,
            long userId,
            Instant expiresAt,
            boolean revoked,
            boolean used,
            String username,
            boolean active) {}

    private record RefreshResult(AuthDtos.Tokens tokens, String error) {}
}
