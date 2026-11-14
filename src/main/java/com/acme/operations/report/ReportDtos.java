package com.acme.operations.report;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

// These request and response records carry data between the API and the service.
public final class ReportDtos {
    // This class groups related types or utilities and does not need an instance.
    private ReportDtos() {}

    // A sales row adds completed orders from the same customer.
    public record CustomerSales(
            // Reference to the customer to whom the order belongs.
            @Schema(description = "ID of the customer placing the order.") long customerId,
            // Name of the client obtained when consulting your relationship to the order.
            String customerName,
            // Number of orders completed included by the date filter.
            long completedOrders,
            // Amount of orders; shipping costs are kept separate.
            BigDecimal netSales,
            // Quota cost of shipping, separated from total products.
            BigDecimal shippingCost) {}

    // A row per active product, the available of which meets the low stock threshold.
    public record LowStock(
            // ID of the catalog product.
            @Schema(description = "ID of the catalog product.") long productId,
            // Product SKU.
            String sku,
            // Product name.
            String productName,
            // Registered physicist and part of that physicist engaged in reserves.
            @Schema(description = "Units physically in stock.") long onHand,
            // Physical units committed to orders.
            @Schema(description = "Units held for confirmed customer orders.") long reserved,
            // Available for new reservations: less reserved physicist.
            @Schema(description = "On-hand units minus reserved units.") long available,
            // Product reservation records count, not number of units.
            @Schema(description = "Number of confirmed orders reserving this product.") long confirmedOrders) {}
}
