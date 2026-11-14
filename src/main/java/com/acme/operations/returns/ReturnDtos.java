package com.acme.operations.returns;

import io.swagger.v3.oas.annotations.media.Schema;

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
            @NotNull @Min(1) @Schema(description = "ID of the catalog product.")
            Long productId,
            @NotNull @Min(1) @Max(1000000) @Schema(description = "Number of units in this line.")
            Long quantity) {}

    public record Create(
            @NotNull @Min(1) @Schema(description = "ID of the customer sales order.")
            Long orderId,
            @NotNull @Schema(description = "Identifies a single operation. Reuse the same UUID when retrying it.")
            UUID requestId,
            @NotBlank @Size(max = 500) String reason,
            @NotEmpty @Size(max = 100) List<@NotNull @Valid RequestedLine> lines) {}

    public record Decision(
            @NotNull @Min(0) @Schema(description = "Current record version used to detect concurrent changes.")
            Long version,
            @NotBlank @Size(max = 500) String reason) {}

    public record Disposition(
            @NotNull @Min(1) @Schema(description = "ID of the catalog product.")
            Long productId,
            @NotNull @Min(0) @Max(1000000) @Schema(description = "Returned units suitable for resale.")
            Long restockQuantity,
            @NotNull @Min(0) @Max(1000000) @Schema(description = "Returned units that cannot be restocked.")
            Long damagedQuantity) {}

    public record Receive(
            @NotNull @Schema(description = "Identifies a single operation. Reuse the same UUID when retrying it.")
            UUID requestId,
            @NotNull @Min(0) @Schema(description = "Current record version used to detect concurrent changes.")
            Long version,
            @NotEmpty @Size(max = 100) List<@NotNull @Valid Disposition> lines) {}

    public record Line(
            @Schema(description = "ID of the catalog product.")
            long productId,
            String sku,
            @Schema(description = "Number of units in this line.")
            long quantity,
            @Schema(description = "Selling price in EUR per unit.")
            BigDecimal unitPrice,
            @Schema(description = "Value of the returned units at their original selling price.")
            BigDecimal creditAmount,
            @Schema(description = "Returned units suitable for resale.")
            Long restockQuantity,
            @Schema(description = "Returned units that cannot be restocked.")
            Long damagedQuantity) {}

    public record Summary(
            @Schema(description = "ID of this return. Use it to approve, reject or receive the return.")
            long id,
            @Schema(description = "ID of the customer sales order.")
            long orderId,
            String status,
            String reason,
            @Schema(description = "Value of the returned units at their original selling price.")
            BigDecimal creditAmount,
            @Schema(description = "Current record version used to detect concurrent changes.")
            long version,
            Instant createdAt,
            Instant reviewedAt,
            @Schema(description = "Time the warehouse recorded the returned goods.")
            Instant receivedAt) {}

    public record View(
            @Schema(description = "ID of this return. Use it to approve, reject or receive the return.")
            long id,
            @Schema(description = "ID of the customer sales order.")
            long orderId,
            String status,
            String reason,
            @Schema(description = "Reason recorded when the request was approved or rejected.")
            String reviewReason,
            @Schema(description = "Value of the returned units at their original selling price.")
            BigDecimal creditAmount,
            @Schema(description = "Current record version used to detect concurrent changes.")
            long version,
            @Schema(description = "UUID used to open this return request.")
            UUID createRequestId,
            @Schema(description = "UUID used to record the arrival of the returned units.")
            UUID receiptRequestId,
            Instant createdAt,
            Instant updatedAt,
            Instant reviewedAt,
            @Schema(description = "Time the warehouse recorded the returned goods.")
            Instant receivedAt,
            List<Line> lines) {}
}
