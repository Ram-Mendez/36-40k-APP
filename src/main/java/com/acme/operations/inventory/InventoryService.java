package com.acme.operations.inventory;

import com.acme.operations.common.AuditService;
import com.acme.operations.common.BusinessException;
import com.acme.operations.common.PageResponse;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {
    private final InventoryRepository repository;
    private final AuditService audit;

    public InventoryService(InventoryRepository repository, AuditService audit) {
        this.repository = repository;
        this.audit = audit;
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public void adjust(long productId, InventoryDtos.Adjustment request) {
        if (request.quantity() == 0
                || request.quantity() > 1000000000L
                || request.quantity() < -1000000000L)
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_QUANTITY",
                    "Adjustment must be nonzero and between -1000000000 and 1000000000");
        if (!repository.productExists(productId))
            throw new BusinessException(
                    HttpStatus.NOT_FOUND, "PRODUCT_NOT_FOUND", "Product not found");
        var balance = repository.lock(productId);
        long onHand;
        try {
            onHand = Math.addExact(balance.onHand(), request.quantity());
        } catch (ArithmeticException exception) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "INVALID_QUANTITY", "Quantity exceeds supported range");
        }
        if (onHand < balance.reserved())
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "INSUFFICIENT_AVAILABLE_STOCK",
                    "Adjustment would consume reserved stock or make stock negative");
        repository.updateOnHand(productId, onHand);
        repository.insertMovement(
                productId, null, "ADJUSTMENT", request.quantity(), request.reason());
        audit.record(
                "STOCK_ADJUSTED",
                "PRODUCT",
                Long.toString(productId),
                request.quantity() + ": " + request.reason());
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public void reserve(long orderId, long productId, long quantity) {
        requirePositiveQuantity(quantity);
        var balance = repository.lock(productId);
        if (balance.onHand() - balance.reserved() < quantity)
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "INSUFFICIENT_STOCK",
                    "Insufficient available stock for product " + productId);
        repository.insertReservation(orderId, productId, quantity);
        repository.insertMovement(productId, orderId, "RESERVE", quantity, "Order confirmed");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public void release(long orderId, long productId, long quantity, boolean ship) {
        requirePositiveQuantity(quantity);
        var balance = repository.lock(productId);
        if (balance.reserved() < quantity
                || repository.deleteReservation(orderId, productId, quantity) != 1)
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "RESERVATION_MISMATCH",
                    "Reservation does not match order");
        repository.decreaseReservedAndOnHand(productId, quantity, ship ? quantity : 0);
        repository.insertMovement(
                productId,
                orderId,
                ship ? "SHIP" : "RELEASE",
                -quantity,
                ship ? "Order completed" : "Order cancelled");
    }

    private static void requirePositiveQuantity(long quantity) {
        if (quantity <= 0)
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_QUANTITY",
                    "Stock reservation quantity must be positive");
    }

    public PageResponse<InventoryDtos.Balance> balances(Long productId, int page, int size) {
        return repository.balances(productId, page, size);
    }

    public PageResponse<InventoryDtos.Movement> movements(Long productId, int page, int size) {
        return repository.movements(productId, page, size);
    }

    public PageResponse<InventoryDtos.Reservation> reservations(int page, int size) {
        return repository.reservations(page, size);
    }
}
