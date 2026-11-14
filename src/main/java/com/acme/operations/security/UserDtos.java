package com.acme.operations.security;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.*;

import java.time.Instant;

// These request and response records carry data between the API and the service.
public final class UserDtos {
    // This class groups related types or utilities and does not need an instance.
    private UserDtos() {}

    // Data sent by an administrator to create an access account, not a commercial client.
    public record Create(
            // Required user with limited characters and length.
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9._-]{3,80}") String username,
            // Initial password; the service stores your hash and checks the limit in bytes.
            @NotBlank @Size(min = 12, max = 72) String password,
            // The role must be present and be one of the values of the enum Role.
            @NotNull Role role) {}

    // It only allows to change role and status; it does not accept a hash or a password in this operation.
    public record Update(
            @NotNull Role role,
            @Nullable @Pattern(regexp = "[a-zA-Z0-9._-]{3,80}") String username,
            @NotNull Boolean active) {}

    // Public view of the account; excludes password and hash.
    public record View(long id, String username, Role role, boolean active, Instant createdAt) {}
}
