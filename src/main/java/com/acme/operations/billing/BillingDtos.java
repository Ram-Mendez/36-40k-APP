package com.acme.operations.billing;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class BillingDtos {
    private BillingDtos() {}

    public record Issue(@NotNull @Positive Long orderId, @NotNull LocalDate dueDate) {}

    public record Settlement(
            @NotNull UUID requestId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
            @NotBlank @Size(max = 120) String reference) {}

    public record Reversal(@NotBlank @Size(max = 500) String reason) {}

    public record Invoice(
            long id,
            String number,
            long orderId,
            long customerId,
            String customerName,
            String customerTaxId,
            String customerAddress,
            String currency,
            BigDecimal productTotal,
            BigDecimal shippingTotal,
            BigDecimal total,
            BigDecimal credited,
            BigDecimal netPaid,
            BigDecimal balance,
            String status,
            LocalDate dueDate,
            Instant issuedAt) {}

    public record Entry(
            long id,
            long invoiceId,
            UUID requestId,
            String kind,
            BigDecimal amount,
            String reference,
            Instant createdAt,
            Instant reversedAt,
            String reversalReason) {}

    public record Credit(long returnId, BigDecimal amount, Instant receivedAt) {}

    public record AgingBucket(String name, long invoiceCount, BigDecimal amount) {}

    public record Receivables(
            LocalDate asOf,
            String balanceBasis,
            BigDecimal outstanding,
            BigDecimal overdue,
            BigDecimal refundsDue,
            List<AgingBucket> buckets) {}
}
