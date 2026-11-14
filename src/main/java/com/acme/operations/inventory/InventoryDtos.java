package com.acme.operations.inventory;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

// These request and response records carry data between the API and the service.
public final class InventoryDtos {
    // This class groups related types or utilities and does not need an instance.
    private InventoryDtos() {}

    // Current summary by product: physical stocks, reserves and units still available.
    public record Balance(
            // ID of the catalog product.
            @Schema(description = "ID of the catalog product.") long productId,
            // Product SKU.
            String sku,
            // Product name.
            String productName,
            // Registered physical stocks, including units that are reserved.
            @Schema(description = "Units physically in stock.") long onHand,
            // Engaged units that are still physically in storage.
            @Schema(description = "Units held for confirmed customer orders.") long reserved,
            // Units available for other orders: onHand minus reserved.
            @Schema(description = "On-hand units minus reserved units.") long available) {}

    // Physical adjustment and motive input; service checks range and not zero.
    public record Adjustment(
            @NotNull @Schema(description = "Signed change in physical stock. Use a positive number to add units and a negative number to remove them.")
            Long quantity,
            @NotBlank @Size(max = 500) String reason) {}

    // Historical movement that explains a change; it does not in itself represent the current balance.
    public record Movement(
            // Unique ID of this record.
            long id,
            // ID of the catalog product.
            @Schema(description = "ID of the catalog product.") long productId,
            // Long supports null when the movement does not come from an order, such as a manual adjustment.
            @Schema(description = "ID of the customer sales order.") Long orderId,
            // Type of movement and quantity recorded; the meaning of the sign depends on the type.
            @Schema(description = "Type of stock movement.") String kind,
            // Units of this product on the line.
            @Schema(description = "Number of units in this line.") long quantity,
            String reason,
            // Date of creation of the movement log.
            Instant createdAt) {}

    // Detail of a reservation: which order compromises how many units of which product.
    public record Reservation(
            @Schema(description = "ID of the customer sales order.")
            long orderId,
            @Schema(description = "ID of the catalog product.")
            long productId,
            String sku,
            @Schema(description = "Number of units in this line.")
            long quantity) {}
}
