package com.acme.operations;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.acme.operations.common.AuditService;
import com.acme.operations.common.BusinessException;
import com.acme.operations.inventory.InventoryService;
import com.acme.operations.order.OrderRepository;
import com.acme.operations.order.OrderService;
import com.acme.operations.shipping.ShippingClient;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    @Mock OrderRepository repository;
    @Mock InventoryService inventory;
    @Mock AuditService audit;
    @InjectMocks OrderService service;

    private OrderRepository.Header header(String state) {
        return new OrderRepository.Header(
                1, 2, "Client", state, BigDecimal.TEN, 3, null, null, Instant.EPOCH, Instant.EPOCH);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONFIRMED", "COMPLETED", "CANCELLED"})
    void confirmationOfNonDraftFailsBeforeStockChanges(String state) {
        when(repository.header(1, true)).thenReturn(header(state));
        assertThatThrownBy(() -> service.confirm(1)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(inventory, audit);
        verify(repository, never()).transition(anyLong(), anyString(), any(), any());
    }

    @Test
    void emptyOrderCannotTransitionOrReserveStock() {
        when(repository.header(1, true)).thenReturn(header("DRAFT"));
        when(repository.lines(1)).thenReturn(List.of());
        assertThatThrownBy(() -> service.confirm(1)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(inventory, audit);
        verify(repository, never()).transition(anyLong(), anyString(), any(), any());
    }

    @Test
    void staleCarrierQuoteCannotConsumeStock() {
        when(repository.header(1, true)).thenReturn(header("CONFIRMED"));
        assertThatThrownBy(
                        () ->
                                service.complete(
                                        1, 2, new ShippingClient.Quote("quote", BigDecimal.ONE)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(inventory, audit);
        verify(repository, never()).transition(anyLong(), anyString(), any(), any());
    }
}
