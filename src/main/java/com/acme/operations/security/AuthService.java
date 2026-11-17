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

// Spring registers this class as a service. Its methods implement the business rules.
@Service
public class AuthService {
    // Run parametered SQL using the connection associated with the current transaction.
    private final JdbcTemplate jdbc;
    // Verify and generate password hashes; never compare passwords saved in clear.
    private final PasswordEncoder passwords;
    // Sign the tokens access with the Spring JWT configuration.
    private final JwtEncoder encoder;
    // Use execute and executeWithoutResult to define explicit transaction boundaries.
    private final TransactionTemplate transactions;
    // It records security actions along with session changes.
    private final AuditService audit;
    // Access time token; determines expiresIn, expressed in seconds.
    private final Duration accessTtl;
    // Maximum duration of the refresh tokens family.
    private final Duration refreshTtl;
    // Cryptographic generator for the refresh token to be unpredictable.
    private final SecureRandom random = new SecureRandom();
    // Fictional hash used to verify also when the user does not exist.
    private final String dummyHash;

    // Spring injects collaborators and durations in this unique builder.
    public AuthService(
            JdbcTemplate jdbc,
            PasswordEncoder passwords,
            JwtEncoder encoder,
            TransactionTemplate transactions,
            AuditService audit,
            // Spring reads the configuration durations; PT15M and P7D are the default values.
            @Value("${security.jwt.access-ttl:PT15M}") Duration accessTtl,
            @Value("${security.jwt.refresh-ttl:P7D}") Duration refreshTtl) {
        // Save the units received; end prevents the replacement of these references later.
        this.jdbc = jdbc;
        // Keep the shared password encoder.
        this.passwords = passwords;
        // Keep the JWT firmer set up by Spring.
        this.encoder = encoder;
        // It retains the template that delimits the authentication transactions.
        this.transactions = transactions;
        // Keep the audit to record changes within the transaction.
        this.audit = audit;
        // Save the access duration and maximum renewal duration.
        this.accessTtl = accessTtl;
        // Save how long a family of refresh tokens can last.
        this.refreshTtl = refreshTtl;
        // The two durations must be positive not to issue already expired tokens.
        if (accessTtl.isNegative()
                || accessTtl.isZero()
                || refreshTtl.isNegative()
                || refreshTtl.isZero()) {
            // An invalid configuration prevents the service from being built and properly started.
            throw new IllegalArgumentException("Token lifetimes must be positive");
        }
        // Prepares the fictitious hash once; reduces time differences when looking for non-existent users.
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    // Authors credentials and creates a persistent session with your tokens.
    public AuthDtos.Tokens login(String username, String password) {
        // TransactionTemplate executes this block in transaction; the execution errors cause rollback.
        return transactions.execute(
                status -> {
                    // Lock the user row to coordinate login with administrative changes to the same account.
                    var users =
                            jdbc.query(
                                    "SELECT id,username,password_hash,active FROM app_user WHERE"
                                        + " username=? FOR UPDATE",
                                    // The mapper builds LoginUser from the returned columns.
                                    (rs, n) ->
                                            new LoginUser(
                                                    rs.getLong("id"),
                                                    rs.getString("username"),
                                                    rs.getString("password_hash"),
                                                    rs.getBoolean("active")),
                                    // Normalize the name before assigning it to the SQL.
                                    username.toLowerCase(Locale.ROOT));
                    // It represents an absent user with null to check later without revealing if it exists.
                    var user = users.isEmpty() ? null : users.getFirst();
                    // It will save the result of the password comparison.
                    boolean matches;
                    // It turns coder errors into rejected credentials.
                    try {
                        // Compare with the actual hash or fictitious; do not unfigure any password.
                        matches =
                                passwords.matches(password, user == null ? dummyHash : user.hash());
                    // A format that the encoder does not accept also counts as the wrong password.
                    } catch (IllegalArgumentException exception) {
                        matches = false;
                    }
                    // Discard absent user, incorrect password or inactive account with the same error.
                    if (user == null || !matches || !user.active())
                        throw unauthorized("INVALID_CREDENTIALS", "Invalid username or password");
                    // Emite tokens for a new family whose expiry is now fixed.
                    var tokens =
                            issue(
                                    user.id(),
                                    user.username(),
                                    UUID.randomUUID(),
                                    Instant.now().plus(refreshTtl));
                    // Use the supplied username in the login audit event because authentication has not
                    // finished.
                    audit.recordAs(
                            user.username(),
                            "LOGIN",
                            "USER",
                            Long.toString(user.id()),
                            "Session created");
                    // Return the tokens; execute confirms the transaction before delivering them to the caller.
                    return tokens;
                });
    }

    // Rot a refresh token: invalidate the previous one and deliver a new one from the same family.
    public AuthDtos.Tokens refresh(String token) {
        // Returns the error as a data to confirm the revocation before throwing the 401 out of the block.
        var result =
                transactions.execute(
                        status -> {
                            // Locates the user by the token hash; the base does not store the token refresh in
                            // clear.
                            var userIds =
                                    jdbc.query(
                                            "SELECT user_id FROM auth_session WHERE refresh_hash=?",
                                            (rs, n) -> rs.getLong(1),
                                            hash(token));
                            // An unknown token is rejected without creating a new session.
                            if (userIds.isEmpty())
                                return new RefreshResult(null, "INVALID_REFRESH_TOKEN");
                            // It blocks the user first to maintain the same order as other session operations.
                            jdbc.queryForObject(
                                    "SELECT id FROM app_user WHERE id=? FOR UPDATE",
                                    Long.class,
                                    userIds.getFirst());
                            // Then block the session and read its dates and user status.
                            var sessions =
                                    jdbc.query(
                                            "SELECT"
                                                + " s.id,s.family_id,s.user_id,s.expires_at,s.revoked_at,s.used_at,u.username,u.active"
                                                + " FROM auth_session s JOIN app_user u ON"
                                                + " u.id=s.user_id WHERE s.refresh_hash=? FOR"
                                                + " UPDATE OF s",
                                            // Convert dates and marks of use or revocation in the record Session.
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
                            // If the session no longer appears, return the rejection result.
                            if (sessions.isEmpty())
                                return new RefreshResult(null, "INVALID_REFRESH_TOKEN");
                            // Select the session found to decide if it can still be rotated.
                            var session = sessions.getFirst();
                            // A token used again indicates replay: his whole family is invalidated.
                            if (session.used()) {
                                // The revocation also reaches the tokens previously issued in that family.
                                revokeFamily(session.familyId());
                                // It registers the re-use so that it can be investigated in audit.
                                audit.recordAs(
                                        session.username(),
                                        "REFRESH_REPLAY",
                                        "USER",
                                        Long.toString(session.userId()),
                                        "Session family revoked");
                                // It leaves the block without yet to launch: so the revocation is confirmed.
                                return new RefreshResult(null, "REFRESH_REPLAY");
                            }
                            // A revoked, expired or inactive user session cannot be renewed.
                            if (session.revoked()
                                    || !session.active()
                                    || !session.expiresAt().isAfter(Instant.now())) {
                                return new RefreshResult(null, "INVALID_REFRESH_TOKEN");
                            }
                            // Mark the current token as used and revoked before issuing your replacement.
                            jdbc.update(
                                    "UPDATE auth_session SET"
                                        + " used_at=current_timestamp,revoked_at=current_timestamp"
                                        + " WHERE id=?",
                                    session.id());
                            // It emits the new session while maintaining original family and expiry; it does not
                            // extend its maximum life.
                            return new RefreshResult(
                                    issue(
                                            session.userId(),
                                            session.username(),
                                            session.familyId(),
                                            session.expiresAt()),
                                    null);
                        });
        // Whether outside the transactional block, it transforms the rejection result into HTTP 401.
        if (result == null || result.error() != null)
            throw unauthorized(
                    result == null ? "INVALID_REFRESH_TOKEN" : result.error(),
                    "Refresh token rejected; sign in again");
        // It delivers the newly issued tokens when the rotation ended correctly.
        return result.tokens();
    }

    // Spring requires an authenticated identity before entering the method.
    @PreAuthorize("isAuthenticated()")
    // Close the session family to which the authenticated JWT belongs.
    public void logout(String sessionId) {
        // Lock, revoke access and record the audit event in one transaction.
        transactions.executeWithoutResult(
                status -> {
                    // He gets the owner of the session that came from the main JWT.
                    var userId =
                            jdbc.queryForObject(
                                    "SELECT user_id FROM auth_session WHERE id=?",
                                    Long.class,
                                    UUID.fromString(sessionId));
                    // It blocks the user before mutating sessions, as well as refresh and administration.
                    jdbc.queryForObject(
                            "SELECT id FROM app_user WHERE id=? FOR UPDATE", Long.class, userId);
                    // He gets the ID from the session family.
                    var family =
                            jdbc.queryForObject(
                                    "SELECT family_id FROM auth_session WHERE id=?",
                                    UUID.class,
                                    UUID.fromString(sessionId));
                    // Update the associated sessions and keep your history.
                    jdbc.update(
                             "UPDATE auth_session SET revoked_at=coalesce(revoked_at,current_timestamp)"
                                 + " WHERE family_id=? AND used_at IS NULL",
                             family);
                    // Leave the closure record inside the same transaction.
                    audit.record("LOGOUT", "SESSION", sessionId, "Session family revoked");
                });
    }

    // Only the authenticated account can change your own password.
    @PreAuthorize("isAuthenticated() and #username == authentication.name")
    public void changePassword(String username, AuthDtos.ChangePassword request) {
        // BCrypt supports at most 72 bytes, although the DTO already limits the number of characters.
        if (request.newPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PASSWORD",
                    "Password exceeds 72 UTF-8 bytes");
        }

        // The lock coordinates this change with sign-in, token refresh and administration.
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

                    // It updates the hash and revokes all sessions as a single atomic operation.
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

        // BCrypt supports up to 72 bytes.
        if (request.newPassword()
                .getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PASSWORD",
                    "Password exceeds 72 UTF-8 bytes");
        }

        // Group all changes in a transaction.
        transactions.executeWithoutResult(status -> {

            // Lock the user's row until you finish.
            var users = jdbc.query(
                    "SELECT id,username,password_hash,active " +
                            "FROM app_user WHERE username=? FOR UPDATE",
                    (rs, n) -> new LoginUser(
                            rs.getLong("id"),
                            rs.getString("username"),
                            rs.getString("password_hash"),
                            rs.getBoolean("active")),
                    username);

            // Recover the found user.
            var user = users.isEmpty() ? null : users.getFirst();

            // Check the current password.
            boolean matches;
            try {
                matches = user != null
                        && user.active()
                        && passwords.matches(
                        request.currentPassword(), user.hash());
            } catch (IllegalArgumentException exception) {
                matches = false;
            }

            // Reject the change if the password does not match.
            if (!matches) {
                throw unauthorized(
                        "INVALID_CREDENTIALS",
                        "Current password is invalid");
            }

            // Normalize the name to save in small cases.
            String newUsername =
                    request.username().toLowerCase(Locale.ROOT);

            // It updates name and password in a single operation.
            jdbc.update(
                    "UPDATE app_user " +
                            "SET username=?, password_hash=? WHERE id=?",
                    newUsername,
                    passwords.encode(request.newPassword()),
                    user.id());

            // Invalid all previous meetings.
            jdbc.update(
                    "UPDATE auth_session " +
                            "SET revoked_at=coalesce(revoked_at,current_timestamp) " +
                            "WHERE user_id=?",
                    user.id());

            // Audit the change without recording passwords.
            audit.record(
                    "USER_UPDATED",
                    "USER",
                    Long.toString(user.id()),
                    "Profile updated; sessions revoked");
        });
    }


    // It reviews all family sessions by retaining a previous date of revocation.
    private void revokeFamily(UUID familyId) {
        jdbc.update(
                "UPDATE auth_session SET revoked_at=coalesce(revoked_at,current_timestamp) WHERE"
                    + " family_id=?",
                familyId);
    }

    // It creates a session and the two tokens; they call it blocks that already have a transaction.
    private AuthDtos.Tokens issue(
            long userId, String username, UUID family, Instant refreshExpiry) {
        // Generates the id that will link the JWT to the auth_session.
        var sessionId = UUID.randomUUID();
        // Book 48 bytes and fill them with cryptographic randomness.
        var bytes = new byte[48];
        random.nextBytes(bytes);
        // It encodes these bytes as a URL-suitable text, without Base64 filling.
        var refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        // The session and hash of the refresh persist; the original token is only returned to the customer.
        jdbc.update(
                "INSERT INTO auth_session(id,user_id,family_id,refresh_hash,expires_at) VALUES"
                    + " (?,?,?,?,?)",
                sessionId,
                userId,
                family,
                hash(refresh),
                Timestamp.from(refreshExpiry));
        // Use the same instant as reference for JWT issue and expiry.
        var now = Instant.now();
        // Build the claims: emitter, user, dates, identifier and link to the session.
        var claims =
                JwtClaimsSet.builder()
                        .issuer("business-operations")
                        .subject(username)
                        .issuedAt(now)
                        .expiresAt(now.plus(accessTtl))
                        // jti identifies the token; uid and Sid link with user and session.
                        .id(UUID.randomUUID().toString())
                        .claim("uid", userId)
                        .claim("sid", sessionId.toString())
                        .build();
        // The encoder signs the claims with HS256; signing does not amount to encryption the content.
        var jwt =
                encoder.encode(
                        JwtEncoderParameters.from(
                                JwsHeader.with(MacAlgorithm.HS256).build(), claims));
        // Return access token, refresh token, Bearer scheme and access duration in seconds.
        return new AuthDtos.Tokens(jwt.getTokenValue(), refresh, "Bearer", accessTtl.toSeconds());
    }

    // Calculate a SHA-256 hash to search for the refresh without saving its original value.
    static String hash(String value) {
        // It manages the cryptographic supplier's exception, not a rejection of credentials.
        try {
            // Turn the binary summary into hexadecimal to store and compare it.
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        // If SHA-256 was not available, it is an internal configuration or platform failure.
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    // It builds the exception 401; ApiExceptionHandler then turns it into HTTP response.
    private static BusinessException unauthorized(String code, String detail) {
        return new BusinessException(HttpStatus.UNAUTHORIZED, code, detail);
    }

    // Internal login data; hash is not included in tokens response.
    private record LoginUser(long id, String username, String hash, boolean active) {}

    // Use the locked session data to decide whether the token can be refreshed.
    private record Session(
            // Identifiers of this session, your rotation family and the owner.
            UUID id,
            UUID familyId,
            long userId,
            // Deadline and marks that determine whether to renew.
            Instant expiresAt,
            boolean revoked,
            boolean used,
            // Identity and current status of the associated user.
            String username,
            boolean active) {}

    // It transports tokens or an error code outside the transactional block.
    private record RefreshResult(AuthDtos.Tokens tokens, String error) {}
}
