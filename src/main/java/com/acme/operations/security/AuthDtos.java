package com.acme.operations.security;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// These request and response records carry data between the API and the service.
public final class AuthDtos {
    // This class groups related types or utilities and does not need an instance.
    private AuthDtos() {}

    // Input credentials; restrictions are activated by @Valid on the controller.
    public record Login(
            // Limit the user and reject empty text before consulting the base.
            @NotBlank @Size(max = 80) String username,
            // Password received only to verify it; it is not returned as part of the tokens.
            @NotBlank @Size(max = 128) String password) {}

    // It receives the opaque renovation token, other than the JWT used to call the API.
    public record Refresh(@NotBlank @Size(max = 200) String refreshToken) {}

    // Current password and replace so that an authenticated account can renew its secret.
    public record ChangePassword(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 12, max = 72) String newPassword) {}

    // Data that the user sends to update his account.
    public record UpdateUser(

            // New username: between 3 and 80 valid characters.
            @NotBlank
            @Pattern(regexp = "[a-zA-Z0-9._-]{3,80}")
            String username,

            // Current password to authorize change.
            @NotBlank String currentPassword,

            // New password with a minimum of 12 characters.
            @NotBlank @Size(min = 12, max = 72)
            String newPassword
    ) {}
    // Login or refresh response: access token, refresh token, scheme and duration in seconds.
    public record Tokens(
            String accessToken, String refreshToken, String tokenType, long expiresIn) {}
}
