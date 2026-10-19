package com.acme.operations.report;

import java.math.BigDecimal;

public final class ReportDtos {
    private ReportDtos() {}

    public record CustomerSales(
            long customerId,
            String customerName,
            long completedOrders,
            BigDecimal netSales,
            BigDecimal shippingCost) {}

    public record LowStock(
            long productId,
            String sku,
            String productName,
            long onHand,
            long reserved,
            long available,
            long confirmedOrders) {}
}
