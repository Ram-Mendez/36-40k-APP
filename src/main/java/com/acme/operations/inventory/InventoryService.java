package com.acme.operations.inventory;

import com.acme.operations.common.AuditService;
import com.acme.operations.common.BusinessException;
import com.acme.operations.common.PageResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// Spring registers this class as a service. Its methods implement the business rules.
@Service
public class InventoryService {
    // The repository handles persistence for this service.
    private final InventoryRepository repository;
    // Use the shared audit service to record this operation.
    private final AuditService audit;

    // Spring injects the dependencies through the single constructor.
    public InventoryService(InventoryRepository repository, AuditService audit) {
        // Keep the injected dependency for later calls.
        this.repository = repository;
        // Keep the injected dependency for later calls.
        this.audit = audit;
    }

    // Spring starts or joins a transaction when the call goes through the proxy. It commits on success and rolls
    // back on unchecked exceptions.
    @Transactional
    // Only ADMIN and OPERATIONS can perform this operation.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // It adjusts physical stocks and records the motive within the same operation.
    public void adjust(long productId, InventoryDtos.Adjustment request) {
        // Reject zero and amounts outside the range admitted before touching the inventory.
        if (request.quantity() == 0
                || request.quantity() > 1000000000L
                || request.quantity() < -1000000000L)
            // The handler will return 400 with INVALID_QUANTITY; the amount sent is not valid.
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_QUANTITY",
                    "Adjustment must be nonzero and between -1000000000 and 1000000000");
        // Check that the product exists before creating or locking its inventory row.
        if (!repository.productExists(productId))
            // The absence of the product is reported as 404, not as insufficient stock.
            throw new BusinessException(
                    HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found");
        // Read and lock the product row until this transaction is finished.
        var balance = repository.lock(productId);
        // It will keep the new physical total, not the amount available or the increase isolated.
        long onHand;
        // Catch addition overflow and report why the quantity is invalid.
        try {
            // Add the adjustment to the current balance and launch ArithmeticException if the result does not fit
            // in long.
            onHand = Math.addExact(balance.onHand(), request.quantity());
        // You turn that overflow into a business exception with HTTP 400.
        } catch (ArithmeticException exception) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "INVALID_QUANTITY", "Quantity exceeds supported range");
        }
        // It prevents fewer stocks than those committed; it also prevents a negative physical balance.
        if (onHand < balance.reserved())
            // 409 explains that the adjustment is in line with the current reserves.
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "INSUFFICIENT_AVAILABLE_STOCK",
                    "Adjustment would consume reserved stock or make stock negative");
        // Save the new physical total and increase the balance version.
        repository.updateOnHand(productId, onHand);
        // Add the ADJUSTMENT movement; orderId is null because it does not come from an order.
        repository.insertMovement(
                productId, null, "ADJUSTMENT",request.quantity(), request.reason());
        // Record who adjusted the stock; balance, movement and audit share transaction.
        audit.record(
                "STOCK_ADJUSTED",
                "PRODUCT",
                Long.toString(productId),
                request.quantity() + ": " + request.reason());
    }

    // Spring requires an already open transaction; it joins and fails if it does not exist.
    @Transactional(propagation = Propagation.MANDATORY)
    // Only ADMIN and OPERATIONS can perform this operation.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Book units for an order; the boxes are still in the warehouse.
    public void reserve(long orderId, long productId, long quantity) {
        // Reject zero or negative amounts before blocking data.
        requirePositiveQuantity(quantity);
        // Lock the inventory row so concurrent orders cannot reserve the same units.
        var balance = repository.lock(productId);
        // Available = less reserved physicist; compare that result to what was requested.
        if (balance.onHand() - balance.reserved() < quantity)
            // If it does not reach, the exception causes rollback from the operation that called for reserve.
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "INSUFFICIENT_STOCK",
                    "Insufficient available stock for product " + productId);
        // It increases the reserve and creates the reservation linked to the order in the same transaction.
        repository.insertReservation(orderId, productId, quantity);
        // Save the evidence of the reservation; a failure here also undoes the previous change.
        repository.insertMovement(productId, orderId, "RESERVE", quantity, "Order confirmed");
    }

    // Spring requires an already open transaction; it joins and fails if it does not exist.
    @Transactional(propagation = Propagation.MANDATORY)
    // Only ADMIN and OPERATIONS can perform this operation.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Free a reservation; ship distinguishes a cancellation from a physical output.
    public void release(long orderId, long productId, long quantity, boolean ship) {
        // The amount must be positive even if the final movement is recorded with a negative sign.
        requirePositiveQuantity(quantity);
        // Keep the balance protected while checking and releasing the reservation.
        var balance = repository.lock(productId);
        // Check the reserved stock and remove the reservation matching this order and quantity.
        if (balance.reserved() < quantity
                || repository.deleteReservation(orderId, productId, quantity) != 1)
            // If the reservation does not match, the transaction is aborted instead of leaving a partial change.
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "RESERVATION_MISMATCH",
                    "Reservation does not match order");
        // Always subtract reserved; only subtract on_hand when ship is true and the units come out.
        repository.decreaseReservedAndOnHand(productId, quantity, ship ? quantity : 0);
        // You register SHIP when you send or RELEASE when you cancel; -quantity reflects the release.
        repository.insertMovement(
                productId,
                orderId,
                ship ? "SHIP" : "RELEASE",
                -quantity,
                ship ? "Order completed" : "Order cancelled");
    }

    // Centralize the same rule to book and release.
    private static void requirePositiveQuantity(long quantity) {
        // Zero or a negative number does not represent a valid amount for these operations.
        if (quantity <= 0)
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_QUANTITY",
                    "Stock reservation quantity must be positive");
    }

    // See the summary by product: physical, reserved and available.
    public PageResponse<InventoryDtos.Balance> balances(Long productId, int page, int size) {
        // The repository runs the query and maps the result.
        return repository.balances(productId, page, size);
    }

    // Check the change history, not the current balance.
    public PageResponse<InventoryDtos.Movement> movements(Long productId, int page, int size) {
        // The repository runs the query and maps the result.
        return repository.movements(productId, page, size);
    }

    // Check the reservations associated with orders, do not make a new reservation.
    public PageResponse<InventoryDtos.Reservation> reservations(int page, int size) {
        // The repository runs the query and maps the result.
        return repository.reservations(page, size);
    }
}
