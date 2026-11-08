package com.acme.operations.product;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

// Agrupa DTO de entrada y salida; los records transportan datos y no consultan la base automáticamente.
public final class ProductDtos {
    // Constructor privado: esta clase agrupa tipos o utilidades y no necesita instancias.
    private ProductDtos() {}

    // Entrada de alta o edición; @Valid en el controlador activa estas restricciones.
    public record Input(
            // El SKU es obligatorio, admite < y >, y debe tener entre 2 y 64 caracteres.
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_<>-]{2,64}") String sku,
            // Exige un nombre no vacío de hasta 200 caracteres.
            @NotBlank @Size(max = 200) String name,
            // El precio es obligatorio, positivo y compatible con doce enteros y dos decimales.
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal unitPrice,
            // Puede omitirse al crear; al editar el servicio exige la versión actual.
            @PositiveOrZero Long version) {}

    // Vista de salida: incluye id y versión generados o mantenidos por persistencia.
    public record View(
            // Identificador propio de este registro, distinto de las referencias a cliente o producto.
            long id, String sku, String name, BigDecimal unitPrice, boolean active, long version) {
        // Convierte explícitamente la entidad en los datos que devolverá la API.
        public static View of(Product entity) {
            // Copia campos, sin ejecutar una nueva consulta ni modificar el producto.
            return new View(
                    entity.getId(),
                    entity.getSku(),
                    entity.getName(),
                    entity.getUnitPrice(),
                    entity.isActive(),
                    entity.getVersion());
        }
    }
}
