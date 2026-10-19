package com.acme.operations.order;

import com.acme.operations.common.BusinessException;
import com.acme.operations.shipping.ShippingClient;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class OrderCompletionService {
    private final OrderService orders;
    private final ShippingClient shipping;

    public OrderCompletionService(OrderService orders, ShippingClient shipping) {
        this.orders = orders;
        this.shipping = shipping;
    }

    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public OrderDtos.Order complete(long id) {
        var snapshot = orders.get(id);
        if (!snapshot.status().equals("CONFIRMED"))
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "INVALID_ORDER_STATE",
                    "Only a confirmed order can be completed");
        // Quoting is read-only at the carrier, so transport retries cannot create duplicate
        // shipments.
        // No database transaction or connection is held while waiting for the remote service.
        var quote = shipping.quote(snapshot.id(), snapshot.total());
        return orders.complete(id, snapshot.version(), quote);
    }
}
