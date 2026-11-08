package com.acme.operations.security;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Null;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Agrupa DTO de entrada y salida; los records transportan datos y no consultan la base automáticamente.
public final class AuthDtos {
    // Constructor privado: esta clase agrupa tipos o utilidades y no necesita instancias.
    private AuthDtos() {}

    // Credenciales de entrada; las restricciones se activan mediante @Valid en el controlador.
    public record Login(
            // Limita el usuario y rechaza texto vacío antes de consultar la base.
            @NotBlank @Size(max = 80) String username,
            // Contraseña recibida solo para verificarla; no se devuelve como parte de los tokens.
            @NotBlank @Size(max = 128) String password) {}

    // Recibe el token opaco de renovación, distinto del JWT utilizado para llamar a la API.
    public record Refresh(@NotBlank @Size(max = 200) String refreshToken) {}

    // Contraseña actual y sustituta para que una cuenta autenticada pueda renovar su secreto.
    public record ChangePassword(
            @NotBlank String currentPassword,
            @NotBlank @Size(min = 12, max = 72) String newPassword) {}

    // Datos que el usuario envía para actualizar su cuenta.
    public record UpdateUser(

            // Nuevo nombre de usuario: entre 3 y 80 caracteres válidos.
            @NotBlank
            @Pattern(regexp = "[a-zA-Z0-9._-]{3,80}")
            String username,

            // Contraseña actual para autorizar el cambio.
            @NotBlank String currentPassword,

            // Nueva contraseña con un mínimo de 12 caracteres.
            @NotBlank @Size(min = 12, max = 72)
            String newPassword
    ) {}
    // Respuesta del login o refresh: access token, refresh token, esquema y duración en segundos.
    public record Tokens(
            String accessToken, String refreshToken, String tokenType, long expiresIn) {}
}
