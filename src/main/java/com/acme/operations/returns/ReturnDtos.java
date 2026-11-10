package com.acme.operations.returns;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ReturnDtos {
    private ReturnDtos() {}

    public record RequestedLine(
            @NotNull @Min(1) Long productId,
            @NotNull @Min(1) @Max(1000000) Long quantity) {}

    public record Create(
            @NotNull @Min(1) Long orderId,
            @NotNull UUID requestId,
            @NotBlank @Size(max = 500) String reason,
            @NotEmpty @Size(max = 100) List<@NotNull @Valid RequestedLine> lines) {}

    public record Decision(
            @NotNull @Min(0) Long version,
            @NotBlank @Size(max = 500) String reason) {}

    public record Disposition(
            @NotNull @Min(1) Long productId,
            @NotNull @Min(0) @Max(1000000) Long restockQuantity,
            @NotNull @Min(0) @Max(1000000) Long damagedQuantity) {}

    public record Receive(
            @NotNull UUID requestId,
            @NotNull @Min(0) Long version,
            @NotEmpty @Size(max = 100) List<@NotNull @Valid Disposition> lines) {}

    public record Line(
            long productId,
            String sku,
            long quantity,
            BigDecimal unitPrice,
            BigDecimal creditAmount,
            Long restockQuantity,
            Long damagedQuantity) {}

    public record Summary(
            long id,
            long orderId,
            String status,
            String reason,
            BigDecimal creditAmount,
            long version,
            Instant createdAt,
            Instant reviewedAt,
            Instant receivedAt) {}

    public record View(
            long id,
            long orderId,
            String status,
            String reason,
            String reviewReason,
            BigDecimal creditAmount,
            long version,
            UUID createRequestId,
            UUID receiptRequestId,
            Instant createdAt,
            Instant updatedAt,
            Instant reviewedAt,
            Instant receivedAt,
            List<Line> lines) {}
}
