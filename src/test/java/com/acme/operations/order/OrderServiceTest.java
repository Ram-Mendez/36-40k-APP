package com.acme.operations.order;

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

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock OrderRepository orders;
    @Mock InventoryService inventory;
    @Mock AuditService audit;
    OrderService service;

    @BeforeEach
    void setup() {
        service = new OrderService(orders, inventory, audit);
    }

    private OrderRepository.Header header(String status) {
        return new OrderRepository.Header(
                1, 1, "Acme", status, BigDecimal.TEN, 2, null, null, Instant.now(), Instant.now());
    }

    @Test
    void emptyOrderCannotReserveOrTransition() {
        when(orders.header(1, true)).thenReturn(header("DRAFT"));
        when(orders.lines(1)).thenReturn(List.of());
        assertThatThrownBy(() -> service.confirm(1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least one line");
        verifyNoInteractions(inventory, audit);
        verify(orders, never()).transition(anyLong(), anyString(), any(), any());
    }

    @Test
    void completedOrderCannotBeCancelledOrReleaseStock() {
        when(orders.header(1, true)).thenReturn(header("COMPLETED"));
        assertThatThrownBy(() -> service.cancel(1)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(inventory, audit);
        verify(orders, never()).transition(anyLong(), anyString(), any(), any());
    }

    @Test
    void staleQuoteCannotDebitStock() {
        when(orders.header(1, true)).thenReturn(header("CONFIRMED"));
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

    @Test
    void draftCannotBeCompleted() {
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
