package com.acme.operations.billing;

import com.acme.operations.common.AuditService;
import com.acme.operations.common.BusinessException;
import com.acme.operations.common.PageRequests;
import com.acme.operations.common.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','READONLY')")
public class BillingService {
    private final BillingRepository repository;
    private final AuditService audit;

    public BillingService(BillingRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    public BillingDtos.Invoice get(long id) { return repository.get(id); }

    public PageResponse<BillingDtos.Invoice> list(Long customerId, String status, int page, int size) {
        page(page, size);
        if (customerId != null && customerId <= 0) throw invalid("Customer id must be positive");
        if (status != null && !Set.of("OPEN", "PARTIALLY_PAID", "SETTLED", "CREDIT_DUE").contains(status))
            throw invalid("Unknown invoice status");
        return repository.list(customerId, status, page, size);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public BillingDtos.Invoice issue(BillingDtos.Issue request) {
        var order = repository.lockOrder(request.orderId());
        var existing = repository.invoiceForOrder(request.orderId());
        if (existing.isPresent()) {
            var invoice = repository.get(existing.get());
            if (!invoice.dueDate().equals(request.dueDate()))
                throw conflict("INVOICE_ALREADY_ISSUED", "The order already has an invoice with different terms");
            return invoice;
        }
        if (!order.status().equals("COMPLETED"))
            throw conflict("ORDER_NOT_COMPLETED", "Only a completed order can be invoiced");
        var today = LocalDate.now(ZoneOffset.UTC);
        if (request.dueDate().isBefore(today) || request.dueDate().isAfter(today.plusDays(90)))
            throw invalid("Due date must be between today and 90 days from today (UTC)");
        long id = repository.issue(order.id(), request.dueDate());
        audit.record("INVOICE_ISSUED", "INVOICE", Long.toString(id), "Order " + order.id());
        return repository.get(id);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public BillingDtos.Entry pay(long id, BillingDtos.Settlement request) {
        return settle(id, "PAYMENT", request);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public BillingDtos.Entry refund(long id, BillingDtos.Settlement request) {
        return settle(id, "REFUND", request);
    }

    private BillingDtos.Entry settle(long id, String kind, BillingDtos.Settlement request) {
        lock(id);
        var previous = repository.byRequest(id, request.requestId());
        if (previous.isPresent()) {
            var entry = previous.get();
            if (!entry.kind().equals(kind) || entry.amount().compareTo(request.amount()) != 0
                    || !entry.reference().equals(request.reference().strip()))
                throw conflict("IDEMPOTENCY_CONFLICT", "Request id was already used with different settlement data");
            // A replay never resurrects a reversed entry.
            return entry;
        }
        var invoice = repository.get(id);
        BigDecimal allowed = kind.equals("PAYMENT") ? invoice.balance() : invoice.balance().negate();
        if (allowed.signum() <= 0 || request.amount().compareTo(allowed) > 0)
            throw conflict(kind.equals("PAYMENT") ? "PAYMENT_EXCEEDS_BALANCE" : "REFUND_EXCEEDS_CREDIT",
                    "Amount exceeds the current " + (kind.equals("PAYMENT") ? "outstanding balance" : "refundable credit"));
        long entryId = repository.post(id, kind, request);
        audit.record("INVOICE_" + kind + "_RECORDED", "INVOICE", Long.toString(id),
                "Entry " + entryId + ", amount " + request.amount().toPlainString());
        return repository.entry(id, entryId);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public BillingDtos.Entry reverse(long id, long entryId, BillingDtos.Reversal request) {
        lock(id);
        var entry = repository.entry(id, entryId);
        String reason = request.reason().strip();
        if (entry.reversedAt() != null) {
            if (!entry.reversalReason().equals(reason))
                throw conflict("REVERSAL_CONFLICT", "Entry was reversed with a different reason");
            return entry;
        }
        if (entry.kind().equals("PAYMENT") && repository.get(id).netPaid().compareTo(entry.amount()) < 0)
            throw conflict("PAYMENT_ALREADY_REFUNDED", "Reverse the associated refunds before reversing this payment");
        repository.reverse(id, entryId, reason);
        audit.record("INVOICE_ENTRY_REVERSED", "INVOICE", Long.toString(id), "Entry " + entryId + ": " + reason);
        return repository.entry(id, entryId);
    }

    public PageResponse<BillingDtos.Entry> entries(long id, int page, int size) {
        page(page, size);
        repository.get(id);
        return repository.entries(id, page, size);
    }

    public BillingDtos.Entry entry(long id, long entryId) {
        repository.get(id);
        return repository.entry(id, entryId);
    }

    public PageResponse<BillingDtos.Credit> credits(long id, int page, int size) {
        page(page, size);
        return repository.credits(repository.get(id).orderId(), page, size);
    }

    public BillingDtos.Receivables receivables(LocalDate asOf, Long customerId) {
        if (customerId != null && customerId <= 0) throw invalid("Customer id must be positive");
        return repository.receivables(asOf == null ? LocalDate.now(ZoneOffset.UTC) : asOf, customerId);
    }

    // All invoice and return mutations share this parent lock before locking their own rows.
    private void lock(long invoiceId) {
        repository.lockOrder(repository.orderId(invoiceId));
        repository.lockInvoice(invoiceId);
    }

    private static void page(int page, int size) {
        PageRequests.of(page, size, "id", "asc", Set.of("id"));
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_BILLING_REQUEST", message);
    }

    private static BusinessException conflict(String code, String message) {
        return new BusinessException(HttpStatus.CONFLICT, code, message);
    }
}
