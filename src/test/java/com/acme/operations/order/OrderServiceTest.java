package com.acme.operations.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.acme.operations.common.AuditService;
import com.acme.operations.common.BusinessException;
import com.acme.operations.inventory.InventoryService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

// Mockito creates simulated collaborators without lifting Spring or PostgreSQL.
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    // The repository, inventory and audit are double-controlled by the test.
    @Mock OrderRepository orders;
    @Mock InventoryService inventory;
    @Mock AuditService audit;
    OrderService service;

    // JUnit runs this method before each case to create a clean service.
    @BeforeEach
    void setup() {
        // It manually builds OrderService with mocks; it does not go through a Spring proxy.
        service = new OrderService(orders, inventory, audit);
    }

    // Create an example header with configurable status and version 2.
    private OrderRepository.Header header(String status) {
        return new OrderRepository.Header(
                1, 1, "Acme", status, BigDecimal.TEN, 2, null, null, Instant.now(), Instant.now());
    }

    // An order without lines should not reserve units or change status.
    @Test
    void emptyOrderCannotReserveOrTransition() {
        // It simulates the reading of a draft and an empty list of lines.
        when(orders.header(1, true)).thenReturn(header("DRAFT"));
        when(orders.lines(1)).thenReturn(List.of());
        // It requires an exception whose message indicates that at least one line is missing.
        assertThatThrownBy(() -> service.confirm(1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least one line");
        // He verifies that no inventory, audit or transition was called.
        verifyNoInteractions(inventory, audit);
        verify(orders, never()).transition(anyLong(), anyString(), any(), any());
    }

    // A completed order cannot be cancelled or released.
    @Test
    void completedOrderCannotBeCancelledOrReleaseStock() {
        // He's pretending that the header is already in COMPLETED.
        when(orders.header(1, true)).thenReturn(header("COMPLETED"));
        // Check the rejection and do not call the units that change data.
        assertThatThrownBy(() -> service.cancel(1)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(inventory, audit);
        verify(orders, never()).transition(anyLong(), anyString(), any(), any());
    }

    // Cancel a confirmed order releases your reservation and changes the status to CANCELLED.
    @Test
    void cancellationOfConfirmedOrderReleasesReservedUnits() {
        // Simulates a confirmed order with 3 reserved units of product 4.
        var line = new OrderDtos.Line(6, 4, "SKU-4", "Producto", 3, BigDecimal.TEN, BigDecimal.valueOf(30));
        var cancelled = new OrderDtos.Order(
                1, 1, "Acme", "CANCELLED", BigDecimal.valueOf(30), 3,
                null, null, Instant.EPOCH, Instant.EPOCH, List.of(line));
        when(orders.header(1, true)).thenReturn(header("CONFIRMED"));
        when(orders.lines(1)).thenReturn(List.of(line));
        when(orders.get(1)).thenReturn(cancelled);

        // Run the cancellation and check that you return the requested order after the change.
        assertThat(service.cancel(1)).isSameAs(cancelled);

        // Fake indicates that the reserve is released without sending the units.
        verify(inventory).release(1, 4, 3, false);
        verify(orders).transition(1, "CANCELLED", null, null);
    }

    // A different version of the one used to quote invalidates the completion.
    @Test
    void staleQuoteCannotDebitStock() {
        // The current simulated version is 2 and the caller presents the old version 1.
        when(orders.header(1, true)).thenReturn(header("CONFIRMED"));
        // He's waiting for the conflict without trying to discounted inventory.
        assertThatThrownBy(
                        () ->
                                service.complete(
                                        1,
                                        1,
                                        new com.acme.operations.shipping.ShippingClient.Quote(
                                                "Q-1", BigDecimal.ONE)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("changed");
        verifyNoInteractions(inventory, audit);
    }

    // An order in DRAFT cannot be completed even if a quote is provided.
    @Test
    void draftCannotBeCompleted() {
        // It prepares the invalid state and requires the operation to fail before it touches stock.
        when(orders.header(1, true)).thenReturn(header("DRAFT"));
        assertThatThrownBy(
                        () ->
                                service.complete(
                                        1,
                                        2,
                                        new com.acme.operations.shipping.ShippingClient.Quote(
                                                "Q-1", BigDecimal.ONE)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(inventory, audit);
    }
}
