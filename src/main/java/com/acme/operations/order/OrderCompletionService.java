package com.acme.operations.order;

import com.acme.operations.common.BusinessException;
import com.acme.operations.shipping.ShippingClient;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

// Spring registers this class as a service. Its methods implement the business rules.
@Service
// It coordinates the consultation of the carrier and the completion; these are two separate steps.
public class OrderCompletionService {
    // Service that reads the order and runs your changes within transactions.
    private final OrderService orders;
    // HTTP client who gets the carrier's quote.
    private final ShippingClient shipping;

    // Spring injects both services by creating this object by its only builder.
    public OrderCompletionService(OrderService orders, ShippingClient shipping) {
        // Keep the reference to the order service to use it later.
        this.orders = orders;
        // Keep the HTTP client; here no request is yet sent.
        this.shipping = shipping;
    }

    // Only ADMIN and OPERATIONS can perform this operation.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Returns the full order DTO; does not convert the customer's id into an order.
    public OrderDtos.Order complete(long id) {
        // You get a photo of the order. The reading transaction ends when you return to the normal flow.
        var snapshot = orders.get(id);
        // It only allows to continue if that photo shows a confirmed order.
        if (!snapshot.status().equals("CONFIRMED"))
            // It disrupts the flow with a business error; ApiExceptionHandler transforms it into HTTP.
            throw new BusinessException(
                    // 409 indicates conflict with the state of the order; code and message explain the cause.
                    HttpStatus.CONFLICT,
                    "INVALID_ORDER_STATE",
                    "Only a confirmed order can be completed");
        // The quote is read only in the carrier: order again does not create a shipment. Shipping consultation
        // without opening a transaction here; if it fails, the order is not completed.
        var quote = shipping.quote(snapshot.id(), snapshot.total());
        // The other service opens the transaction, blocks and rechecks status and version before saving.
        return orders.complete(id, snapshot.version(), quote);
    }
// End of service: the transactions are managed by the OrderService methods called above.
}
