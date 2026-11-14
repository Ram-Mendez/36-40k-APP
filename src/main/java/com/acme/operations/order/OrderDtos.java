package com.acme.operations.order;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// It is a group of contracts for the entry and exit of the order; it is not an entity or a repository.
public final class OrderDtos {
    // This class groups related types or utilities and does not need an instance.
    private OrderDtos() {}

    // To create an order, supply a positive customerId. The service checks that the customer exists.
    public record Create(@NotNull @Min(1) @Schema(description = "ID of the customer placing the order.") Long customerId) {}

    // A line entry: the amount is positive and limited; the product comes from the URL.
    public record SetLine(@NotNull @Min(1) @Max(1000000) @Schema(description = "Number of units in this line.") Long quantity) {}

    // Output from a line: product, units and price agreed for that order.
    public record Line(
            // Unique ID of this record.
            long id,
            // ID of the catalog product.
            @Schema(description = "ID of the catalog product.") long productId,
            // Product SKU.
            String sku,
            // Product name.
            String productName,
            // Units of this product on the line.
            @Schema(description = "Number of units in this line.") long quantity,
            // Unit price agreed, which may differ from the current price of the catalogue.
            @Schema(description = "Selling price in EUR per unit.") BigDecimal unitPrice,
            // Amount of this line: quantity multiplied by unit price.
            BigDecimal lineTotal) {}

    // Full output: it gathers the order header and its lines, not automatically filled.
    public record Order(
            // Unique ID of this record.
            long id,
            // Reference to the customer to whom the order belongs.
            @Schema(description = "ID of the customer placing the order.") long customerId,
            // Name of the client obtained when consulting your relationship to the order.
            String customerName,
            // Order status; determines which business operations the service allows.
            String status,
            // Amount of the order lines; the shipment has its separate field.
            BigDecimal total,
            // Version read by the caller. It is used to detect later changes.
            @Schema(description = "Current record version used to detect concurrent changes.") long version,
            // Reference of the shipping quotation, absent before completion.
            String shippingReference,
            // Quota cost of shipping, separated from total products.
            BigDecimal shippingCost,
            // Record creation time.
            Instant createdAt,
            // Instant of the last recorded update.
            Instant updatedAt,
            // List of order products; it may be empty when creating it.
            List<Line> lines) {}
}
