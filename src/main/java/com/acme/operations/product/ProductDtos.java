package com.acme.operations.product;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public final class ProductDtos {
    private ProductDtos() {}

    public record Input(
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{2,64}") String sku,
            @NotBlank @Size(max = 200) String name,
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal unitPrice,
            @PositiveOrZero Long version) {}

    public record View(
            long id, String sku, String name, BigDecimal unitPrice, boolean active, long version) {
        public static View of(Product entity) {
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
