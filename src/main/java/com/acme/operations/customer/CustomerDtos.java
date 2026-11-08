package com.acme.operations.customer;

import jakarta.validation.constraints.*;

// Agrupa DTO de entrada y salida; los records transportan datos y no consultan la base automáticamente.
public final class CustomerDtos {
    // Constructor privado: esta clase agrupa tipos o utilidades y no necesita instancias.
    private CustomerDtos() {}

    // Entrada del cliente; las restricciones se evalúan al recibir un cuerpo con @Valid.
    public record Input(
            // Nombre obligatorio y limitado al tamaño admitido.
            @NotBlank @Size(max = 200) String name,
            // El identificador fiscal debe cumplir el patrón, no basta con que sea un texto.
            @NotBlank @Pattern(regexp = "[A-Za-z0-9<>-]{5,32}") String taxId,
            // Exige un correo no vacío, con formato de email y longitud limitada.
            @NotBlank @Email @Size(max = 254) String email,
            // Dirección obligatoria con un máximo de 500 caracteres.
            @NotBlank @Size(max = 500) String address,
            // La versión permite detectar ediciones sobre datos antiguos; puede faltar durante el alta.
            @PositiveOrZero Long version) {}

    // DTO público del cliente; evita devolver directamente la entidad JPA.
    public record View(
            // Identificador propio de este registro, distinto de las referencias a cliente o producto.
            long id,
            // Nombre descriptivo incluido en la vista de respuesta.
            String name,
            // Identificador fiscal del cliente.
            String taxId,
            // Correo de contacto del cliente.
            String email,
            // Dirección del cliente.
            String address,
            // Indica si el recurso sigue habilitado sin haber borrado su historial.
            boolean active,
            // Versión leída del recurso para detectar modificaciones posteriores.
            long version) {
        // Copia la entidad ya obtenida a su representación de API.
        public static View of(Customer entity) {
            // Los getters leen valores del objeto; este constructor no crea otro cliente en la base.
            return new View(
                    entity.getId(),
                    entity.getName(),
                    entity.getTaxId(),
                    entity.getEmail(),
                    entity.getAddress(),
                    entity.isActive(),
                    entity.getVersion());
        }
    }
}
