package com.acme.operations.order;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class OrderDtos {
    private OrderDtos() {}

    public record Create(@NotNull @Min(1) Long customerId) {}

    public record SetLine(@NotNull @Min(1) @Max(1000000) Long quantity) {}

    public record Line(
            long id,
            long productId,
            String sku,
            String productName,
            long quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal) {}

    public record Order(
            long id,
            long customerId,
            String customerName,
            String status,
            BigDecimal total,
            long version,
            String shippingReference,
            BigDecimal shippingCost,
            Instant createdAt,
            Instant updatedAt,
            List<Line> lines) {}
}
