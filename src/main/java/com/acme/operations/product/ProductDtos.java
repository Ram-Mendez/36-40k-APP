package com.acme.operations.product;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

// These request and response records carry data between the API and the service.
public final class ProductDtos {
    // This class groups related types or utilities and does not need an instance.
    private ProductDtos() {}

    // The controller uses @Valid to validate create and update requests.
    public record Input(
            // The SKU is mandatory, supports and supports, and must have between 2 and 64 characters.
            @NotBlank @Pattern(regexp = "[A-Za-z0-9_<>-]{2,64}") String sku,
            // It requires a non-empty name of up to 200 characters.
            @NotBlank @Size(max = 200) String name,
            // The price is mandatory, positive and compatible with twelve integers and two decimals.
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) @Schema(description = "Selling price in EUR per unit.") BigDecimal unitPrice,
            // It can be omitted when creating; when editing the service requires the current version.
            @PositiveOrZero @Schema(description = "Current record version used to detect concurrent changes.") Long version) {}

    // Output view: includes id and version generated or maintained by persistence.
    public record View(
            // Unique ID of this record.
            long id, String sku, String name, @Schema(description = "Selling price in EUR per unit.") BigDecimal unitPrice, boolean active, @Schema(description = "Current record version used to detect concurrent changes.") long version) {
        // explicitly convert the entity into the data that will return the API.
        public static View of(Product entity) {
            // Copies fields, not running a new consultation or modifying the product.
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
