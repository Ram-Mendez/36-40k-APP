package com.acme.operations.replenishment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import java.time.Instant;

public final class ReplenishmentDtos {
    private ReplenishmentDtos() {}

    // Creation omits version; replacing an existing policy requires its current version.
    public record PolicyInput(
            @NotNull @Min(0) @Max(1000000000) Long minimumStock,
            @NotNull @Min(0) @Max(1000000000) Long targetStock,
            @Positive Long preferredSupplierId,
            @NotNull Boolean active,
            @PositiveOrZero Long version) {}

    public record Policy(
            long productId, String sku, String productName, boolean productActive,
            long minimumStock, long targetStock, Long preferredSupplierId,
            String supplierName, Boolean supplierActive, boolean active, long version,
            Instant createdAt, Instant updatedAt) {}

    // These are advisory quantities. Reading a proposal never creates a purchase order.
    public record Proposal(
            long productId, String sku, String productName,
            long minimumStock, long targetStock, long available, long incoming,
            long projected, long suggestedQuantity, Long preferredSupplierId,
            String supplierName, Boolean supplierActive, boolean purchasable,
            String blockingReason, long policyVersion) {}
}
