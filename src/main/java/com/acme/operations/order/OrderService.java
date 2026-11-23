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

// Spring registers this class as a service. Its methods implement the business rules.
@Service
public class OrderService {
    // The repository handles persistence for this service.
    private final OrderRepository repository;
    // It manages reserves and balances within the order transaction.
    private final InventoryService inventory;
    // Use the shared audit service to record this operation.
    private final AuditService audit;

    // Spring injects the dependencies through the single constructor.
    public OrderService(
            OrderRepository repository, InventoryService inventory, AuditService audit) {
        // Keep the injected dependency for later calls.
        this.repository = repository;
        // Keep the injected dependency for later calls.
        this.inventory = inventory;
        // Keep the injected dependency for later calls.
        this.audit = audit;
    }

    // Spring opens a REPEATABLE_READ reading: header and lines see a consistent snapshot.
    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    // Recover the DTO with general data and order lines.
    public OrderDtos.Order get(long id) {
        // The repository runs the query and maps the result.
        return repository.get(id);
    }

    // It maintains a consistent vision between the count, the headers and the lines of this page.
    @Transactional(
            readOnly = true,
            isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public PageResponse<OrderDtos.Order> search(
            Long customerId, String status, int page, int size, String sort) {
        // The repository runs the query and maps the result.
        return repository.search(customerId, status, page, size, sort);
    }

    // Spring starts or joins a transaction when the call goes through the proxy. It commits on success and rolls
    // back on unchecked exceptions.
    @Transactional
    // Only ADMIN and OPERATIONS can perform this operation.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Creating an order requires a client; lines are added in later operations.
    public OrderDtos.Order create(OrderDtos.Create request) {
        // Reject a non-existing or inactive customer before inserting the order.
        repository.requireActiveCustomer(request.customerId());
        // Insert the customer_id and return the generated order ID.
        long id = repository.create(request.customerId());
        // Record the creation in the audit log in the same transaction.
        audit.record(
                "ORDER_CREATED", "ORDER", Long.toString(id), "Customer " + request.customerId());
        // Refer to the order created to return your DTO with initial values and lines.
        return repository.get(id);
    }

    // Spring starts or joins a transaction when the call goes through the proxy. It commits on success and rolls
    // back on unchecked exceptions.
    @Transactional
    // Only ADMIN and OPERATIONS can perform this operation.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public OrderDtos.Order update(long id, OrderDtos.Create request) {
        // Block the header and require DRAFT before changing the customer.
        requireState(repository.header(id, true), "DRAFT");
        // Check that the new client exists and is still active.
        repository.requireActiveCustomer(request.customerId());
        // Save the relationship with the customer; increase version and update date.
        repository.customer(id, request.customerId());
        // Write the audit event in the same transaction as the change. Both roll back together.
        audit.record(
                "ORDER_UPDATED", "ORDER", Long.toString(id), "Customer " + request.customerId());
        // The repository runs the query and maps the result.
        return repository.get(id);
    }

    // Spring starts or joins a transaction when the call goes through the proxy. It commits on success and rolls
    // back on unchecked exceptions.
    @Transactional
    // Only ADMIN and OPERATIONS can perform this operation.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public OrderDtos.Order setLine(long id, long productId, OrderDtos.SetLine request) {
        // It blocks the order; only the erasers allow to edit lines.
        requireState(repository.header(id, true), "DRAFT");
        // Check that the product is active and gets its current price.
        BigDecimal currentPrice = repository.activePrice(productId);
        // Read the current lines to calculate the order limits.
        var lines = repository.lines(id);
        // Use findFirst to look up the existing line for this product.
        var existing = lines.stream().filter(line -> line.productId() == productId).findFirst();
        // The limit affects different products, not the amount of units in a line.
        if (existing.isEmpty() && lines.size() >= 100)
            // 409 prevents the addition of a new line when the maximum is reached.
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "ORDER_LINE_LIMIT",
                    "An order supports at most 100 distinct products");
        // If the line exists it retains its agreed price; if it is new it uses the one in the catalogue.
        BigDecimal price = existing.map(OrderDtos.Line::unitPrice).orElse(currentPrice);
        // Calculates the total by replacing the affected line, without adding it twice.
        BigDecimal newTotal =
                lines.stream()
                        .filter(line -> line.productId() != productId)
                        .map(OrderDtos.Line::lineTotal)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .add(price.multiply(BigDecimal.valueOf(request.quantity())));
        // Compare BigDecimal to avoid exceeding the amount that the application supports.
        if (newTotal.compareTo(new BigDecimal("999999999999.99")) > 0)
            // 400 reports that the new amount would produce an excessive amount.
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "ORDER_AMOUNT_TOO_LARGE",
                    "Order amount exceeds supported maximum");
        // Insert or update the line and recalculate the total in the database.
        repository.setLine(id, productId, request.quantity(), currentPrice);
        // Write the audit event in the same transaction as the change. Both roll back together.
        audit.record(
                "ORDER_LINE_SET",
                "ORDER",
                Long.toString(id),
                "Product " + productId + ", quantity " + request.quantity());
        // The repository runs the query and maps the result.
        return repository.get(id);
    }

    // Spring starts or joins a transaction when the call goes through the proxy. It commits on success and rolls
    // back on unchecked exceptions.j
    @Transactional
    // Only ADMIN and OPERATIONS can perform this operation.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public OrderDtos.Order removeLine(long id, long productId) {
        // Lock the order and check that it can still be edited.
        requireState(repository.header(id, true), "DRAFT");
        // Delete the line and recalculate the total; if there is no spear 404.
        repository.removeLine(id, productId);
        // Write the audit event in the same transaction as the change. Both roll back together.
        audit.record("ORDER_LINE_REMOVED", "ORDER", Long.toString(id), "Product " + productId);
        // The repository runs the query and maps the result.
        return repository.get(id);
    }

    // Spring starts or joins a transaction when the call goes through the proxy. It commits on success and rolls
    // back on unchecked exceptions.
    @Transactional
    // Only ADMIN and OPERATIONS can perform this operation.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Confirm the draft and book all its lines as a single transaction.
    public OrderDtos.Order confirm(long id) {
        // FOR UPDATE prevents another operation from changing this order while it is confirmed.
        var order = repository.header(id, true);
        // An order already confirmed, completed or cancelled cannot be confirmed again.
        requireState(order, "DRAFT");
        // Check the customer again in case the account was deactivated after the order was created.
        repository.requireActiveCustomer(order.customerId());
        // Get the product-ordered lines to block inventory in a stable order.
        var lines = repository.lines(id);
        // You can't confirm an order without products.
        if (lines.isEmpty())
            throw new BusinessException(
                    HttpStatus.CONFLICT, "EMPTY_ORDER", "An order must contain at least one line");
        // Process each line in order of productId to reduce blocks in order to the contrary.
        for (var line : lines) {
            // Check availability of the product in a catalog without changing its agreed price.
            repository.activePrice(line.productId());
            // It binds to this transaction using MANDATORY; if a line fails, all are undone.
            inventory.reserve(id, line.productId(), line.quantity());
        }
        // Just change to CONFIRMED after booking all the lines.
        repository.transition(id, "CONFIRMED", null, null);
        // Write the audit event in the same transaction as the change. Both roll back together.
        audit.record(
                "ORDER_CONFIRMED",
                "ORDER",
                Long.toString(id),
                "Reserved " + lines.size() + " product(s)");
        // The repository runs the query and maps the result.
        return repository.get(id);
    }

    // Spring starts or joins a transaction when the call goes through the proxy. It commits on success and rolls
    // back on unchecked exceptions.
    @Transactional
    // Only ADMIN and OPERATIONS can perform this operation.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Cancel a draft or a confirmed one; do not delete the request from the history.
    public OrderDtos.Order cancel(long id) {
        // Lock the order before checking its status so completion cannot run concurrently.
        var order = repository.header(id, true);
        // The completed or cancelled orders do not support this transition.
        if (!List.of("DRAFT", "CONFIRMED").contains(order.status()))
            // It returns a conflict of state without continuing with reservations.
            throw transitionError(order.status(), "CANCELLED");
        // Only the confirmed have reservations to release.
        if (order.status().equals("CONFIRMED"))
            // Check the order products in the stable order of the repository.
            for (var line : repository.lines(id))
                // False frees reserved without subtracting physical stocks, because nothing has been sent.
                inventory.release(id, line.productId(), line.quantity(), false);
        // Save CANCELLED after releasing the corresponding reserves.
        repository.transition(id, "CANCELLED", null, null);
        // Write the audit event in the same transaction as the change. Both roll back together.
        audit.record("ORDER_CANCELLED", "ORDER", Long.toString(id), "Reservations released");
        // The repository runs the query and maps the result.
        return repository.get(id);
    }

    // Spring starts or joins a transaction when the call goes through the proxy. It commits on success and rolls
    // back on unchecked exceptions.
    @Transactional
    // Only ADMIN and OPERATIONS can perform this operation.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Receive the read version before shipping to detect changes during the HTTP call.
    public OrderDtos.Order complete(long id, long expectedVersion, ShippingClient.Quote quote) {
        // Open the protected section by reading the header with FOR UPDATE.
        var order = repository.header(id, true);
        // The previous photo could already be out of date.
        requireState(order, "CONFIRMED");
        // Compare the current version with which it was used to request the quote.
        if (order.version() != expectedVersion)
            // 409 avoids applying a quote on an order that changed while expected.
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "ORDER_CHANGED",
                    "Order changed while shipping was being quoted; reload and retry");
        // Consume the reservations of each line within this same transaction.
        for (var line : repository.lines(id))
            // true discounts both reserved and physical, because now the order comes out.
            inventory.release(id, line.productId(), line.quantity(), true);
        // Save COMPLETED and shipping data only after consuming all lines.
        repository.transition(id, "COMPLETED", quote.reference(), quote.cost());
        // Write the audit event in the same transaction as the change. Both roll back together.
        audit.record(
                "ORDER_COMPLETED",
                "ORDER",
                Long.toString(id),
                "Shipping quote " + quote.reference());
        // Returns the updated order; Spring confirms when you leave correctly by your proxy.
        return repository.get(id);
    }

    // Reusable rule that compares the current status to the required.
    public static void requireState(OrderRepository.Header order, String expected) {
        // If it does not match, stop the operation before it continues to modify data.
        if (!order.status().equals(expected)) throw transitionError(order.status(), expected);
    }

    // It builds the error; the caller decides to launch it and the handler builds the HTTP response.
    private static BusinessException transitionError(String state, String target) {
        return new BusinessException(
                HttpStatus.CONFLICT,
                "INVALID_ORDER_STATE",
                "Operation requires " + target + "; current state is " + state);
    }
}
