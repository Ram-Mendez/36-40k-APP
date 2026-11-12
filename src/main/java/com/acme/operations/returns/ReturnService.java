package com.acme.operations.returns;

import com.acme.operations.common.AuditService;
import com.acme.operations.common.BusinessException;
import com.acme.operations.common.PageResponse;
import com.acme.operations.inventory.InventoryRepository;
import com.acme.operations.order.OrderDtos;
import com.acme.operations.order.OrderRepository;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','READONLY')")
public class ReturnService {
    private static final Set<String> STATES = Set.of("REQUESTED", "APPROVED", "REJECTED", "RECEIVED");
    private final ReturnRepository repository;
    private final OrderRepository orders;
    private final InventoryRepository inventory;
    private final AuditService audit;

    public ReturnService(ReturnRepository repository, OrderRepository orders,
                         InventoryRepository inventory, AuditService audit) {
        this.repository = repository;
        this.orders = orders;
        this.inventory = inventory;
        this.audit = audit;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ReturnDtos.View get(long id) {
        return repository.get(id);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<ReturnDtos.Summary> search(
            Long orderId, String status, int page, int size, String sort) {
        if (page < 0 || page > 1000000 || size < 1 || size > 100)
            throw invalid("INVALID_PAGE", "Page must be 0..1000000 and size 1..100");
        if (status != null && !STATES.contains(status))
            throw invalid("INVALID_RETURN_STATUS", "Unknown return status");
        if (orderId != null && orderId <= 0)
            throw invalid("INVALID_ORDER_ID", "Order id must be positive");
        String orderBy = switch (sort) {
            case "createdAt,desc" -> "created_at DESC,id DESC";
            case "createdAt,asc" -> "created_at ASC,id ASC";
            case "creditAmount,asc" -> "credit_amount ASC,id ASC";
            case "creditAmount,desc" -> "credit_amount DESC,id DESC";
            default -> throw invalid("INVALID_SORT", "Allowed sorts: createdAt or creditAmount, asc or desc");
        };
        return repository.search(orderId, status, page, size, orderBy);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ReturnDtos.View create(ReturnDtos.Create request) {
        // El pedido es el bloqueo común a solicitudes de devolución y operaciones de cobro.
        var order = orders.header(request.orderId(), true);
        var requested = sortedRequested(request.lines());
        String reason = request.reason().trim();
        String fingerprint = creationFingerprint(reason, requested);
        var previous = repository.byCreationRequest(order.id(), request.requestId());
        if (previous.isPresent()) {
            requireSamePayload(previous.get().createFingerprint(), fingerprint);
            return repository.get(previous.get().id());
        }
        if (!order.status().equals("COMPLETED"))
            throw conflict("ORDER_NOT_COMPLETED", "Only completed orders can be returned");
        Map<Long, OrderDtos.Line> sold = orders.lines(order.id()).stream()
                .collect(Collectors.toMap(OrderDtos.Line::productId, line -> line));
        BigDecimal credit = BigDecimal.ZERO;
        for (var line : requested) {
            var original = sold.get(line.productId());
            if (original == null)
                throw conflict("PRODUCT_NOT_IN_ORDER", "A returned product does not belong to the order");
            long allocated = repository.allocatedQuantity(order.id(), line.productId());
            if (line.quantity() > original.quantity() - allocated)
                throw conflict("RETURN_QUANTITY_EXCEEDED", "Requested units exceed the remaining returnable quantity");
            credit = credit.add(original.unitPrice().multiply(BigDecimal.valueOf(line.quantity())));
        }
        long id = repository.create(order.id(), request.requestId(), fingerprint, reason, credit);
        for (var line : requested)
            repository.addLine(id, line.productId(), line.quantity(), sold.get(line.productId()).unitPrice());
        audit.record("RETURN_REQUESTED", "RETURN", Long.toString(id), "Order " + order.id());
        return repository.get(id);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ReturnDtos.View approve(long id, ReturnDtos.Decision request) {
        return decide(id, request, "APPROVED");
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ReturnDtos.View reject(long id, ReturnDtos.Decision request) {
        return decide(id, request, "REJECTED");
    }

    private ReturnDtos.View decide(long id, ReturnDtos.Decision request, String target) {
        var header = lockReturn(id);
        if (target.equals("REJECTED")) requireVersion(header, request.version());
        requireState(header, "REQUESTED");
        repository.decide(id, target, request.reason().trim());
        audit.record("RETURN_" + target, "RETURN", Long.toString(id), request.reason().trim());
        return repository.get(id);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ReturnDtos.View receive(long id, ReturnDtos.Receive request) {
        var header = lockReturn(id);
        List<ReturnDtos.Disposition> dispositions = sortedDispositions(request.lines());
        String fingerprint = receiptFingerprint(request.version(), dispositions);
        if (request.requestId().equals(header.receiptRequestId())) {
            requireSamePayload(header.receiptFingerprint(), fingerprint);
            return repository.get(id);
        }
        requireVersion(header, request.version());
        requireState(header, "APPROVED");
        List<ReturnDtos.Line> lines = repository.lines(id);
        if (lines.size() != dispositions.size())
            throw invalid("RETURN_LINES_MISMATCH", "Supply a disposition for every return line exactly once");
        // Valida todas las líneas antes de modificar saldos; el rollback cubre también errores de escritura.
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            var disposition = dispositions.get(i);
            if (line.productId() != disposition.productId()
                    || disposition.restockQuantity() + disposition.damagedQuantity() != line.quantity())
                throw invalid("RETURN_LINES_MISMATCH", "Disposition quantities must match each requested line");
        }
        for (var disposition : dispositions) {
            if (disposition.restockQuantity() > 0) {
                var balance = inventory.lock(disposition.productId());
                long onHand;
                try {
                    onHand = Math.addExact(balance.onHand(), disposition.restockQuantity());
                } catch (ArithmeticException exception) {
                    throw conflict("STOCK_CAPACITY_EXCEEDED", "Returned stock exceeds the supported quantity");
                }
                inventory.updateOnHand(disposition.productId(), onHand);
                inventory.insertMovement(disposition.productId(), header.orderId(), "RETURN",
                        disposition.restockQuantity(), "Return " + id + " accepted into stock");
            }
            repository.disposition(id, disposition.productId(),
                    disposition.restockQuantity(), disposition.damagedQuantity());
        }
        // El abono incluye unidades aceptadas dañadas; el transporte original no se devuelve.
        repository.receive(id, request.requestId(), fingerprint);
        audit.record("RETURN_RECEIVED", "RETURN", Long.toString(id),
                "Order " + header.orderId() + "; credit " + header.creditAmount());
        return repository.get(id);
    }

    private ReturnRepository.Header lockReturn(long id) {
        long orderId = repository.orderId(id);
        orders.header(orderId, true);
        return repository.header(id, true);
    }

    private static List<ReturnDtos.RequestedLine> sortedRequested(List<ReturnDtos.RequestedLine> lines) {
        var sorted = lines.stream().sorted(Comparator.comparing(ReturnDtos.RequestedLine::productId)).toList();
        if (sorted.stream().map(ReturnDtos.RequestedLine::productId).distinct().count() != sorted.size())
            throw invalid("DUPLICATE_RETURN_LINE", "Each product may occur only once in a return request");
        return sorted;
    }

    private static List<ReturnDtos.Disposition> sortedDispositions(List<ReturnDtos.Disposition> lines) {
        var sorted = lines.stream().sorted(Comparator.comparing(ReturnDtos.Disposition::productId)).toList();
        if (sorted.stream().map(ReturnDtos.Disposition::productId).distinct().count() != sorted.size())
            throw invalid("DUPLICATE_RETURN_LINE", "Each product may occur only once in a receipt");
        return sorted;
    }

    private static String creationFingerprint(String reason, List<ReturnDtos.RequestedLine> lines) {
        var canonical = new StringBuilder().append(reason.length()).append(':').append(reason);
        lines.forEach(line -> canonical.append('|').append(line.productId()).append(':').append(line.quantity()));
        return digest(canonical.toString());
    }

    private static String receiptFingerprint(long version, List<ReturnDtos.Disposition> lines) {
        var canonical = new StringBuilder().append(version);
        lines.forEach(line -> canonical.append('|').append(line.productId()).append(':')
                .append(line.restockQuantity()).append(':').append(line.damagedQuantity()));
        return digest(canonical.toString());
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void requireSamePayload(String previous, String current) {
        if (!current.equals(previous))
            throw conflict("IDEMPOTENCY_CONFLICT", "The request id was already used with different data");
    }

    private static void requireVersion(ReturnRepository.Header header, long expected) {
        if (header.version() != expected)
            throw conflict("CONCURRENT_MODIFICATION", "Return changed; reload and retry");
    }

    private static void requireState(ReturnRepository.Header header, String required) {
        if (!header.status().equals(required))
            throw conflict("INVALID_RETURN_STATE", "Operation requires " + required + "; current state is " + header.status());
    }

    private static BusinessException conflict(String code, String detail) {
        return new BusinessException(HttpStatus.CONFLICT, code, detail);
    }

    private static BusinessException invalid(String code, String detail) {
        return new BusinessException(HttpStatus.BAD_REQUEST, code, detail);
    }
}
