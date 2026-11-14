package com.acme.operations.billing;

import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class BillingDtos {
    private BillingDtos() {}

    public record Issue(
            @NotNull @Positive @Schema(description = "ID of the customer sales order.")
            Long orderId,
            @NotNull @Schema(description = "Invoice due date in YYYY-MM-DD format.")
            LocalDate dueDate) {}

    public record Settlement(
            @NotNull @Schema(description = "Identifies a single operation. Reuse the same UUID when retrying it.")
            UUID requestId,
            @NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) @Schema(description = "Amount in EUR.")
            BigDecimal amount,
            @NotBlank @Size(max = 120) String reference) {}

    public record Reversal(@NotBlank @Size(max = 500) String reason) {}

    public record Invoice(
            long id,
            String number,
            @Schema(description = "ID of the customer sales order.")
            long orderId,
            @Schema(description = "ID of the customer placing the order.")
            long customerId,
            String customerName,
            String customerTaxId,
            String customerAddress,
            String currency,
            BigDecimal productTotal,
            BigDecimal shippingTotal,
            BigDecimal total,
            @Schema(description = "Invoice credit from returns received by the warehouse.")
            BigDecimal credited,
            @Schema(description = "Payments received minus refunds paid to the customer.")
            BigDecimal netPaid,
            @Schema(description = "Invoice total minus credits and net payments. A positive balance is due from the customer. A negative balance is due to the customer.")
            BigDecimal balance,
            String status,
            @Schema(description = "Invoice due date in YYYY-MM-DD format.")
            LocalDate dueDate,
            Instant issuedAt) {}

    public record Entry(
            long id,
            @Schema(description = "ID of the invoice containing this entry.")
            long invoiceId,
            @Schema(description = "Identifies a single operation. Reuse the same UUID when retrying it.")
            UUID requestId,
            @Schema(description = "Entry type: PAYMENT or REFUND.")
            String kind,
            @Schema(description = "Amount in EUR.")
            BigDecimal amount,
            String reference,
            Instant createdAt,
            @Schema(description = "Time the entry was reversed. Null while the entry is effective.")
            Instant reversedAt,
            String reversalReason) {}

    public record Credit(
            @Schema(description = "ID of the received return that generated this credit.")
            long returnId,
            @Schema(description = "Amount in EUR.")
            BigDecimal amount,
            Instant receivedAt) {}

    public record AgingBucket(
            String name,
            long invoiceCount,
            @Schema(description = "Amount in EUR.")
            BigDecimal amount) {}

    public record Receivables(
            @Schema(description = "Date used to decide which invoices are overdue.")
            LocalDate asOf,
            @Schema(description = "CURRENT_BALANCE uses the payments and credits recorded to date.")
            String balanceBasis,
            @Schema(description = "Amount customers still owe, including invoices that are not yet overdue.")
            BigDecimal outstanding,
            @Schema(description = "Outstanding amount past its due date.")
            BigDecimal overdue,
            @Schema(description = "Amount owed to customers with a credit balance.")
            BigDecimal refundsDue,
            List<AgingBucket> buckets) {}
}
