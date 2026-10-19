package com.acme.operations.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {
    private AuthDtos() {}

    public record Login(
            @NotBlank @Size(max = 80) String username,
            @NotBlank @Size(max = 128) String password) {}

    public record Refresh(@NotBlank @Size(max = 200) String refreshToken) {}

    public record PasswordChange(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = 12, max = 72) String newPassword) {}

    public record Tokens(
            String accessToken, String refreshToken, String tokenType, long expiresIn) {}
}
