package com.acme.operations.procurement;

import com.acme.operations.common.AuditService;
import com.acme.operations.common.BusinessException;
import com.acme.operations.common.PageResponse;
import com.acme.operations.inventory.InventoryRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','READONLY')")
public class ProcurementService {
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("999999999999.99");
    private final ProcurementRepository repository;
    private final InventoryRepository inventory;
    private final AuditService audit;

    public ProcurementService(ProcurementRepository repository, InventoryRepository inventory, AuditService audit) {
        this.repository = repository;
        this.inventory = inventory;
        this.audit = audit;
    }

    public ProcurementDtos.Supplier supplier(long id) {
        return repository.supplier(id, false);
    }

    public PageResponse<ProcurementDtos.Supplier> suppliers(
            String search, Boolean active, int page, int size, String sort, String direction) {
        return repository.suppliers(search, active, page, size, sort, direction);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ProcurementDtos.Supplier createSupplier(ProcurementDtos.SupplierInput input) {
        long id = repository.createSupplier(input);
        audit.record("SUPPLIER_CREATED", "SUPPLIER", Long.toString(id), "Created supplier");
        return repository.supplier(id, false);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ProcurementDtos.Supplier updateSupplier(long id, ProcurementDtos.SupplierInput input) {
        var supplier = repository.supplier(id, true);
        if (input.version() == null) throw bad("VERSION_REQUIRED", "Provide current resource version");
        if (input.version() != supplier.version()) throw conflict(
                "CONCURRENT_MODIFICATION", "Supplier changed; reload and retry");
        repository.updateSupplier(id, input);
        audit.record("SUPPLIER_UPDATED", "SUPPLIER", Long.toString(id), "Updated supplier");
        return repository.supplier(id, false);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public void deactivateSupplier(long id) {
        var supplier = repository.supplier(id, true);
        if (supplier.active()) {
            repository.deactivateSupplier(id);
            audit.record("SUPPLIER_DEACTIVATED", "SUPPLIER", Long.toString(id), "Deactivated supplier");
        }
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProcurementDtos.Purchase get(long id) {
        return repository.get(id);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<ProcurementDtos.PurchaseSummary> search(
            Long supplierId, String status, LocalDate dueBefore, int page, int size, String sort, String direction) {
        return repository.search(supplierId, status, dueBefore, page, size, sort, direction);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ProcurementDtos.Purchase create(ProcurementDtos.PurchaseCreate input) {
        var supplier = repository.activeSupplier(input.supplierId());
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate expectedOn = input.expectedOn() == null ? today.plusDays(supplier.leadTimeDays()) : input.expectedOn();
        if (expectedOn.isBefore(today) || expectedOn.isAfter(today.plusYears(2))) throw bad(
                "INVALID_EXPECTED_DATE", "Expected arrival must be between today (UTC) and two years from today");
        long id = repository.create(supplier.id(), expectedOn);
        audit.record("PURCHASE_ORDER_CREATED", "PURCHASE_ORDER", Long.toString(id), "Supplier " + supplier.id());
        return repository.get(id);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ProcurementDtos.Purchase setLine(long id, long productId, ProcurementDtos.SetLine input) {
        requireState(repository.header(id, true), Set.of("DRAFT"));
        repository.requireActiveProduct(productId);
        var lines = repository.lines(id);
        boolean exists = lines.stream().anyMatch(line -> line.productId() == productId);
        if (!exists && lines.size() >= 100) throw conflict(
                "PURCHASE_LINE_LIMIT", "A purchase order supports at most 100 distinct products");
        BigDecimal total = lines.stream().filter(line -> line.productId() != productId)
                .map(ProcurementDtos.PurchaseLine::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add)
                .add(input.unitCost().multiply(BigDecimal.valueOf(input.quantity())));
        if (total.compareTo(MAX_AMOUNT) > 0) throw bad(
                "PURCHASE_AMOUNT_TOO_LARGE", "Purchase order amount exceeds supported maximum");
        repository.setLine(id, productId, input);
        audit.record("PURCHASE_LINE_SET", "PURCHASE_ORDER", Long.toString(id),
                "Product " + productId + ", quantity " + input.quantity());
        return repository.get(id);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ProcurementDtos.Purchase removeLine(long id, long productId) {
        requireState(repository.header(id, true), Set.of("DRAFT"));
        repository.removeLine(id, productId);
        audit.record("PURCHASE_LINE_REMOVED", "PURCHASE_ORDER", Long.toString(id), "Product " + productId);
        return repository.get(id);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ProcurementDtos.Purchase submit(long id) {
        var order = repository.header(id, true);
        requireState(order, Set.of("DRAFT"));
        repository.activeSupplier(order.supplierId());
        var lines = repository.lines(id);
        if (lines.isEmpty()) throw conflict("EMPTY_PURCHASE_ORDER", "A purchase order must contain at least one line");
        for (var line : lines) repository.requireActiveProduct(line.productId());
        repository.transition(id, "SUBMITTED");
        audit.record("PURCHASE_ORDER_SUBMITTED", "PURCHASE_ORDER", Long.toString(id), "Submitted to supplier");
        return repository.get(id);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ProcurementDtos.Purchase cancel(long id) {
        requireState(repository.header(id, true), Set.of("DRAFT", "SUBMITTED"));
        repository.transition(id, "CANCELLED");
        audit.record("PURCHASE_ORDER_CANCELLED", "PURCHASE_ORDER", Long.toString(id), "Cancelled before any receipt");
        return repository.get(id);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ProcurementDtos.Receipt receive(long id, ProcurementDtos.Receive input) {
        // The parent row serializes receipts, state transitions and retries for this purchase order.
        var order = repository.header(id, true);
        List<ProcurementDtos.ReceiveLine> receiptLines = input.lines().stream()
                .sorted(Comparator.comparingLong(ProcurementDtos.ReceiveLine::productId)).toList();
        if (receiptLines.stream().map(ProcurementDtos.ReceiveLine::productId).distinct().count() != receiptLines.size())
            throw bad("DUPLICATE_RECEIPT_PRODUCT", "Each product may appear only once in a receipt");
        String reference = input.deliveryReference().trim();
        String payloadHash = fingerprint(reference, receiptLines);
        var previous = repository.previousReceipt(id, input.requestId());
        if (previous.isPresent()) {
            if (!previous.get().payloadHash().equals(payloadHash)) throw conflict(
                    "IDEMPOTENCY_CONFLICT", "Request ID was already used with a different receipt payload");
            return repository.receipt(id, previous.get().id());
        }
        requireState(order, Set.of("SUBMITTED", "PARTIALLY_RECEIVED"));
        Map<Long, ProcurementDtos.PurchaseLine> ordered = repository.lines(id).stream()
                .collect(Collectors.toMap(ProcurementDtos.PurchaseLine::productId, line -> line));
        for (var line : receiptLines) {
            var purchased = ordered.get(line.productId());
            if (purchased == null) throw conflict("PRODUCT_NOT_PURCHASED", "Receipt includes a product outside this order");
            if (line.quantity() > purchased.outstandingQuantity()) throw conflict(
                    "PURCHASE_OVER_RECEIPT", "Receipt exceeds the remaining quantity for product " + line.productId());
        }
        // All inventories are locked in ascending product order, shared with sales and returns.
        Map<Long, Long> nextBalances = new HashMap<>();
        for (var line : receiptLines) {
            var balance = inventory.lock(line.productId());
            try {
                nextBalances.put(line.productId(), Math.addExact(balance.onHand(), line.quantity()));
            } catch (ArithmeticException exception) {
                throw bad("STOCK_AMOUNT_TOO_LARGE", "Stock exceeds supported maximum");
            }
        }
        long receiptId = repository.createReceipt(id, input.requestId(), reference, payloadHash);
        for (var line : receiptLines) {
            inventory.updateOnHand(line.productId(), nextBalances.get(line.productId()));
            inventory.insertMovement(line.productId(), null, "RECEIPT", line.quantity(),
                    "Purchase order " + id + ", receipt " + receiptId);
            repository.receiveLine(receiptId, id, line.productId(), line.quantity());
        }
        repository.updateReceiptStatus(id);
        audit.record("PURCHASE_RECEIVED", "PURCHASE_ORDER", Long.toString(id), "Receipt " + receiptId);
        return repository.receipt(id, receiptId);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProcurementDtos.Receipt receipt(long id, long receiptId) {
        repository.header(id, false);
        return repository.receipt(id, receiptId);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<ProcurementDtos.ReceiptSummary> receipts(long id, int page, int size) {
        repository.header(id, false);
        return repository.receipts(id, page, size);
    }

    private static String fingerprint(String reference, List<ProcurementDtos.ReceiveLine> lines) {
        String canonical = reference.length() + ":" + reference + "|" + lines.stream()
                .map(line -> line.productId() + ":" + line.quantity()).collect(Collectors.joining("|"));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", impossible);
        }
    }

    private static void requireState(ProcurementDtos.PurchaseSummary order, Set<String> allowed) {
        if (!allowed.contains(order.status())) throw conflict(
                "INVALID_PURCHASE_STATE", "Operation is not allowed for purchase order state " + order.status());
    }

    private static BusinessException conflict(String code, String detail) {
        return new BusinessException(HttpStatus.CONFLICT, code, detail);
    }

    private static BusinessException bad(String code, String detail) {
        return new BusinessException(HttpStatus.BAD_REQUEST, code, detail);
    }
}
