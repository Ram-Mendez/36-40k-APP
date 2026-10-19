package com.acme.operations.order;

import com.acme.operations.common.AuditService;
import com.acme.operations.common.BusinessException;
import com.acme.operations.common.PageResponse;
import com.acme.operations.inventory.InventoryService;
import com.acme.operations.shipping.ShippingClient;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class OrderService {
    private final OrderRepository repository;
    private final InventoryService inventory;
    private final AuditService audit;

    public OrderService(
            OrderRepository repository, InventoryService inventory, AuditService audit) {
        this.repository = repository;
        this.inventory = inventory;
        this.audit = audit;
    }

    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public OrderDtos.Order get(long id) {
        return repository.get(id);
    }

    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PageResponse<OrderDtos.Order> search(
            Long customerId, String status, int page, int size, String sort) {
        return repository.search(customerId, status, page, size, sort);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public OrderDtos.Order create(OrderDtos.Create request) {
        repository.requireActiveCustomer(request.customerId());
        long id = repository.create(request.customerId());
        audit.record(
                "ORDER_CREATED", "ORDER", Long.toString(id), "Customer " + request.customerId());
        return repository.get(id);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public OrderDtos.Order update(long id, OrderDtos.Create request) {
        requireState(repository.header(id, true), "DRAFT");
        repository.requireActiveCustomer(request.customerId());
        repository.customer(id, request.customerId());
        audit.record(
                "ORDER_UPDATED", "ORDER", Long.toString(id), "Customer " + request.customerId());
        return repository.get(id);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public OrderDtos.Order setLine(long id, long productId, OrderDtos.SetLine request) {
        requireState(repository.header(id, true), "DRAFT");
        BigDecimal currentPrice = repository.activePrice(productId);
        var lines = repository.lines(id);
        var existing = lines.stream().filter(line -> line.productId() == productId).findFirst();
        if (existing.isEmpty() && lines.size() >= 100)
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "ORDER_LINE_LIMIT",
                    "An order supports at most 100 distinct products");
        BigDecimal price = existing.map(OrderDtos.Line::unitPrice).orElse(currentPrice);
        BigDecimal newTotal =
                lines.stream()
                        .filter(line -> line.productId() != productId)
                        .map(OrderDtos.Line::lineTotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .add(price.multiply(BigDecimal.valueOf(request.quantity())));
        if (newTotal.compareTo(new BigDecimal("999999999999.99")) > 0)
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "ORDER_AMOUNT_TOO_LARGE",
                    "Order amount exceeds supported maximum");
        repository.setLine(id, productId, request.quantity(), currentPrice);
        audit.record(
                "ORDER_LINE_SET",
                "ORDER",
                Long.toString(id),
                "Product " + productId + ", quantity " + request.quantity());
        return repository.get(id);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public OrderDtos.Order removeLine(long id, long productId) {
        requireState(repository.header(id, true), "DRAFT");
        repository.removeLine(id, productId);
        audit.record("ORDER_LINE_REMOVED", "ORDER", Long.toString(id), "Product " + productId);
        return repository.get(id);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public OrderDtos.Order confirm(long id) {
        var order = repository.header(id, true);
        requireState(order, "DRAFT");
        repository.requireActiveCustomer(order.customerId());
        var lines = repository.lines(id);
        if (lines.isEmpty())
            throw new BusinessException(
                    HttpStatus.CONFLICT, "EMPTY_ORDER", "An order must contain at least one line");
        // Every operation locks products in ascending ID order to prevent opposite-order deadlocks.
        for (var line : lines) {
            repository.activePrice(line.productId());
            inventory.reserve(id, line.productId(), line.quantity());
        }
        repository.transition(id, "CONFIRMED", null, null);
        audit.record(
                "ORDER_CONFIRMED",
                "ORDER",
                Long.toString(id),
                "Reserved " + lines.size() + " product(s)");
        return repository.get(id);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public OrderDtos.Order cancel(long id) {
        var order = repository.header(id, true);
        if (!List.of("DRAFT", "CONFIRMED").contains(order.status()))
            throw transitionError(order.status(), "CANCELLED");
        if (order.status().equals("CONFIRMED"))
            for (var line : repository.lines(id))
                inventory.release(id, line.productId(), line.quantity(), false);
        repository.transition(id, "CANCELLED", null, null);
        audit.record("ORDER_CANCELLED", "ORDER", Long.toString(id), "Reservations released");
        return repository.get(id);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public OrderDtos.Order complete(long id, long expectedVersion, ShippingClient.Quote quote) {
        var order = repository.header(id, true);
        requireState(order, "CONFIRMED");
        if (order.version() != expectedVersion)
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "ORDER_CHANGED",
                    "Order changed while shipping was being quoted; reload and retry");
        for (var line : repository.lines(id))
            inventory.release(id, line.productId(), line.quantity(), true);
        repository.transition(id, "COMPLETED", quote.reference(), quote.cost());
        audit.record(
                "ORDER_COMPLETED",
                "ORDER",
                Long.toString(id),
                "Shipping quote " + quote.reference());
        return repository.get(id);
    }

    public static void requireState(OrderRepository.Header order, String expected) {
        if (!order.status().equals(expected)) throw transitionError(order.status(), expected);
    }

    private static BusinessException transitionError(String state, String target) {
        return new BusinessException(
                HttpStatus.CONFLICT,
                "INVALID_ORDER_STATE",
                "Operation requires " + target + "; current state is " + state);
    }
}
