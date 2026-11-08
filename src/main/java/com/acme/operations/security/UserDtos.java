package com.acme.operations.security;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.*;

import java.time.Instant;

// Agrupa DTO de entrada y salida; los records transportan datos y no consultan la base automáticamente.
public final class UserDtos {
    // Constructor privado: esta clase agrupa tipos o utilidades y no necesita instancias.
    private UserDtos() {}

    // Datos que envía un administrador para crear una cuenta de acceso, no un cliente comercial.
    public record Create(
            // Usuario obligatorio con caracteres y longitud limitados.
            @NotBlank @Pattern(regexp = "[a-zA-Z0-9._-]{3,80}") String username,
            // Contraseña inicial; el servicio almacena su hash y comprueba el límite en bytes.
            @NotBlank @Size(min = 12, max = 72) String password,
            // El rol debe estar presente y ser uno de los valores del enum Role.
            @NotNull Role role) {}

    // Solo permite cambiar rol y estado; no acepta un hash ni una contraseña en esta operación.
    public record Update(
            @NotNull Role role,
            @Nullable @Pattern(regexp = "[a-zA-Z0-9._-]{3,80}") String username,
            @NotNull Boolean active) {}

    // Vista pública de la cuenta; excluye contraseña y hash.
    public record View(long id, String username, Role role, boolean active, Instant createdAt) {}
}
