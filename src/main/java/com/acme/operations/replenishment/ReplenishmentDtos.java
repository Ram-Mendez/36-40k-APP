package com.acme.operations.replenishment;

import io.swagger.v3.oas.annotations.media.Schema;

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
            @NotNull @Min(0) @Max(1000000000) @Schema(description = "Stock level used to trigger a purchase proposal.")
            Long minimumStock,
            @NotNull @Min(0) @Max(1000000000) @Schema(description = "Stock level to reach after replenishment.")
            Long targetStock,
            @Positive @Schema(description = "ID of the supplier suggested for replenishment.")
            Long preferredSupplierId,
            @NotNull @Schema(description = "Whether the product is considered for replenishment.")
            Boolean active,
            @PositiveOrZero @Schema(description = "Current record version used to detect concurrent changes.")
            Long version) {}

    public record Policy(
            @Schema(description = "ID of the catalog product.")
            long productId,
            String sku,
            String productName,
            boolean productActive,
            @Schema(description = "Stock level used to trigger a purchase proposal.")
            long minimumStock,
            @Schema(description = "Stock level to reach after replenishment.")
            long targetStock,
            @Schema(description = "ID of the supplier suggested for replenishment.")
            Long preferredSupplierId,
            String supplierName,
            @Schema(description = "Whether the assigned supplier is active.")
            Boolean supplierActive,
            @Schema(description = "Whether the product is considered for replenishment.")
            boolean active,
            @Schema(description = "Current record version used to detect concurrent changes.")
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    // These are advisory quantities. Reading a proposal never creates a purchase order.
    public record Proposal(
            @Schema(description = "ID of the catalog product.")
            long productId,
            String sku,
            String productName,
            @Schema(description = "Stock level used to trigger a purchase proposal.")
            long minimumStock,
            @Schema(description = "Stock level to reach after replenishment.")
            long targetStock,
            @Schema(description = "On-hand units minus reserved units.")
            long available,
            @Schema(description = "Units on open purchase orders still due from suppliers.")
            long incoming,
            @Schema(description = "Available units plus incoming units.")
            long projected,
            @Schema(description = "Number of units recommended for purchase.")
            long suggestedQuantity,
            @Schema(description = "ID of the supplier suggested for replenishment.")
            Long preferredSupplierId,
            String supplierName,
            @Schema(description = "Whether the assigned supplier is active.")
            Boolean supplierActive,
            @Schema(description = "Whether a purchase can be prepared for this proposal.")
            boolean purchasable,
            String blockingReason,
            long policyVersion) {}
}
