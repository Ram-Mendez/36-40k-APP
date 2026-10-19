package com.acme.operations.security;

import jakarta.validation.constraints.*;

import java.time.Instant;

public final class UserDtos {
    private UserDtos() {}

    public record Create(
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9._-]{3,80}") String username,
            @NotBlank @Size(min = 12, max = 72) String password,
            @NotNull Role role) {}

    public record Update(@NotNull Role role, @NotNull Boolean active) {}

    public record View(long id, String username, Role role, boolean active, Instant createdAt) {}
}
