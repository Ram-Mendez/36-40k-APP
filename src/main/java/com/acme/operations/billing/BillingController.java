package com.acme.operations.billing;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.acme.operations.common.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;

@Tag(name = "Billing")
@RestController
@RequestMapping("/api/v1/billing")
@Validated
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','READONLY')")
public class BillingController {
    private final BillingService service;

    public BillingController(BillingService service) { this.service = service; }

    @Operation(summary = "Issue an invoice for a completed order")
    @PostMapping("/invoices")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ResponseEntity<BillingDtos.Invoice> issue(@Valid @RequestBody BillingDtos.Issue request) {
        var invoice = service.issue(request);
        return ResponseEntity.created(URI.create("/api/v1/billing/invoices/" + invoice.id())).body(invoice);
    }

    @Operation(summary = "List invoices")
    @GetMapping("/invoices")
    public PageResponse<BillingDtos.Invoice> list(
            @RequestParam(required = false) Long customerId,
            @Parameter(description = "Filter by status.", schema = @Schema(allowableValues = {
                    "OPEN", "PARTIALLY_PAID", "SETTLED", "CREDIT_DUE"}))
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.list(customerId, status, page, size);
    }

    @Operation(summary = "View an invoice and its outstanding balance")
    @GetMapping("/invoices/{id}")
    public BillingDtos.Invoice get(@PathVariable @Positive long id) { return service.get(id); }

    @Operation(summary = "Record a payment",
            description = "Use a new request ID for each payment. When retrying the same payment, reuse its request ID and original details.")
    @PostMapping("/invoices/{id}/payments")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public BillingDtos.Entry pay(@PathVariable @Positive long id, @Valid @RequestBody BillingDtos.Settlement request) {
        return service.pay(id, request);
    }

    @Operation(summary = "Record a refund")
    @PostMapping("/invoices/{id}/refunds")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public BillingDtos.Entry refund(@PathVariable @Positive long id, @Valid @RequestBody BillingDtos.Settlement request) {
        return service.refund(id, request);
    }

    @Operation(summary = "List payments and refunds for an invoice")
    @GetMapping("/invoices/{id}/entries")
    public PageResponse<BillingDtos.Entry> entries(@PathVariable @Positive long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.entries(id, page, size);
    }

    @Operation(summary = "View an invoice entry")
    @GetMapping("/invoices/{id}/entries/{entryId}")
    public BillingDtos.Entry entry(@PathVariable @Positive long id, @PathVariable @Positive long entryId) {
        return service.entry(id, entryId);
    }

    @Operation(summary = "Reverse an entry and keep its history")
    @PostMapping("/invoices/{id}/entries/{entryId}/reverse")
    @PreAuthorize("hasRole('ADMIN')")
    public BillingDtos.Entry reverse(@PathVariable @Positive long id, @PathVariable @Positive long entryId,
            @Valid @RequestBody BillingDtos.Reversal request) {
        return service.reverse(id, entryId, request);
    }

    @Operation(summary = "List credits for received returns")
    @GetMapping("/invoices/{id}/credits")
    public PageResponse<BillingDtos.Credit> credits(@PathVariable @Positive long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.credits(id, page, size);
    }

    @Operation(summary = "List outstanding invoices by due date")
    @GetMapping("/receivables")
    public BillingDtos.Receivables receivables(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @RequestParam(required = false) Long customerId) {
        return service.receivables(asOf, customerId);
    }
}
