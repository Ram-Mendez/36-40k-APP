package com.acme.operations.procurement;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ProcurementDtos {
    private ProcurementDtos() {}

    public record SupplierInput(
            @NotBlank @Size(max = 200) String name,
            @NotBlank @Pattern(regexp = "[A-Za-z0-9-]{5,32}") String taxId,
            @NotBlank @Email @Size(max = 254) String email,
            @NotNull @Min(0) @Max(365) @Schema(description = "Typical number of days the supplier needs to deliver.")
            Integer leadTimeDays,
            @PositiveOrZero @Schema(description = "Current record version used to detect concurrent changes.")
            Long version) {}

    public record Supplier(
            long id,
            String name,
            String taxId,
            String email,
            @Schema(description = "Typical number of days the supplier needs to deliver.")
            int leadTimeDays,
            boolean active,
            @Schema(description = "Current record version used to detect concurrent changes.")
            long version) {}

    // Without an explicit date, the supplier's current lead time determines the planned arrival.
    public record PurchaseCreate(
            @NotNull @Positive @Schema(description = "ID of the supplier providing the goods.")
            Long supplierId,
            @Schema(description = "Expected delivery date in YYYY-MM-DD format.")
            LocalDate expectedOn) {}

    public record SetLine(
            @NotNull @Min(1) @Max(1000000) @Schema(description = "Number of units in this line.")
            Long quantity,
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) @Schema(description = "Purchase price agreed with the supplier, in EUR per unit.")
            BigDecimal unitCost) {}

    public record PurchaseLine(
            @Schema(description = "ID of the catalog product.")
            long productId,
            String sku,
            String productName,
            @Schema(description = "Number of units in this line.")
            long quantity,
            @Schema(description = "Units already received from the supplier.")
            long receivedQuantity,
            @Schema(description = "Units still due from the supplier.")
            long outstandingQuantity,
            @Schema(description = "Purchase price agreed with the supplier, in EUR per unit.")
            BigDecimal unitCost,
            BigDecimal lineTotal) {}

    public record PurchaseSummary(
            long id,
            @Schema(description = "ID of the supplier providing the goods.")
            long supplierId,
            String supplierName,
            String status,
            @Schema(description = "Expected delivery date in YYYY-MM-DD format.")
            LocalDate expectedOn,
            BigDecimal total,
            @Schema(description = "Current record version used to detect concurrent changes.")
            long version,
            Instant createdAt,
            Instant updatedAt) {}

    public record Purchase(
            long id,
            @Schema(description = "ID of the supplier providing the goods.")
            long supplierId,
            String supplierName,
            String status,
            @Schema(description = "Expected delivery date in YYYY-MM-DD format.")
            LocalDate expectedOn,
            BigDecimal total,
            @Schema(description = "Current record version used to detect concurrent changes.")
            long version,
            Instant createdAt,
            Instant updatedAt,
            List<PurchaseLine> lines) {}

    public record ReceiveLine(
            @NotNull @Positive @Schema(description = "ID of the catalog product.")
            Long productId,
            @NotNull @Min(1) @Max(1000000) @Schema(description = "Number of units in this line.")
            Long quantity) {}

    public record Receive(
            @NotNull @Schema(description = "Identifies a single operation. Reuse the same UUID when retrying it.")
            UUID requestId,
            @NotBlank @Size(max = 120) @Schema(description = "Delivery note reference provided by the supplier.")
            String deliveryReference,
            @NotEmpty @Size(max = 100) List<@NotNull @Valid ReceiveLine> lines) {}

    public record ReceiptLine(
            @Schema(description = "ID of the catalog product.")
            long productId,
            @Schema(description = "Number of units in this line.")
            long quantity) {}

    public record ReceiptSummary(
            long id,
            @Schema(description = "ID of the purchase order for this delivery.")
            long purchaseOrderId,
            @Schema(description = "Identifies a single operation. Reuse the same UUID when retrying it.")
            UUID requestId,
            @Schema(description = "Delivery note reference provided by the supplier.")
            String deliveryReference,
            @Schema(description = "Time the warehouse recorded the delivery.")
            Instant receivedAt) {}

    public record Receipt(
            long id,
            @Schema(description = "ID of the purchase order for this delivery.")
            long purchaseOrderId,
            @Schema(description = "Identifies a single operation. Reuse the same UUID when retrying it.")
            UUID requestId,
            @Schema(description = "Delivery note reference provided by the supplier.")
            String deliveryReference,
            @Schema(description = "Time the warehouse recorded the delivery.")
            Instant receivedAt,
            List<ReceiptLine> lines) {}
}
