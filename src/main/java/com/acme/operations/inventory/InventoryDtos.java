package com.acme.operations.inventory;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class InventoryDtos {
    private InventoryDtos() {}

    public record Balance(
            long productId,
            String sku,
            String productName,
            long onHand,
            long reserved,
            long available) {}

    public record Adjustment(@NotNull Long quantity, @NotBlank @Size(max = 500) String reason) {}

    public record Movement(
            long id,
            long productId,
            Long orderId,
            String kind,
            long quantity,
            String reason,
            Instant createdAt) {}

    public record Reservation(long orderId, long productId, String sku, long quantity) {}
}
