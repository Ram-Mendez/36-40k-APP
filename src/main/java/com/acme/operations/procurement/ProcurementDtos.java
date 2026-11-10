package com.acme.operations.procurement;

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
            @NotNull @Min(0) @Max(365) Integer leadTimeDays,
            @PositiveOrZero Long version) {}

    public record Supplier(
            long id, String name, String taxId, String email, int leadTimeDays,
            boolean active, long version) {}

    // Without an explicit date, the supplier's current lead time determines the planned arrival.
    public record PurchaseCreate(
            @NotNull @Positive Long supplierId, LocalDate expectedOn) {}

    public record SetLine(
            @NotNull @Min(1) @Max(1000000) Long quantity,
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal unitCost) {}

    public record PurchaseLine(
            long productId, String sku, String productName, long quantity,
            long receivedQuantity, long outstandingQuantity, BigDecimal unitCost,
            BigDecimal lineTotal) {}

    public record PurchaseSummary(
            long id, long supplierId, String supplierName, String status,
            LocalDate expectedOn, BigDecimal total, long version, Instant createdAt,
            Instant updatedAt) {}

    public record Purchase(
            long id, long supplierId, String supplierName, String status,
            LocalDate expectedOn, BigDecimal total, long version, Instant createdAt,
            Instant updatedAt, List<PurchaseLine> lines) {}

    public record ReceiveLine(
            @NotNull @Positive Long productId,
            @NotNull @Min(1) @Max(1000000) Long quantity) {}

    public record Receive(
            @NotNull UUID requestId,
            @NotBlank @Size(max = 120) String deliveryReference,
            @NotEmpty @Size(max = 100) List<@NotNull @Valid ReceiveLine> lines) {}

    public record ReceiptLine(long productId, long quantity) {}

    public record ReceiptSummary(
            long id, long purchaseOrderId, UUID requestId, String deliveryReference,
            Instant receivedAt) {}

    public record Receipt(
            long id, long purchaseOrderId, UUID requestId, String deliveryReference,
            Instant receivedAt, List<ReceiptLine> lines) {}
}
