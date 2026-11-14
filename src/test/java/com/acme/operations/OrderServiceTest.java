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

// Mockito prepares the doubles without starting Spring or a database.
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    // The repositories and services are simulated; they allow you to observe what OrderService tried to do.
    @Mock OrderRepository repository;
    @Mock InventoryService inventory;
    @Mock AuditService audit;
    // Inject the mocks into the service to be tested.
    @InjectMocks OrderService service;

    // It makes an example header with the indicated status and version 3.
    private OrderRepository.Header header(String state) {
        return new OrderRepository.Header(
                1, 2, "Client", state, BigDecimal.TEN, 3, null, null, Instant.EPOCH, Instant.EPOCH);
    }

    // Repeat the case with three states that are not DRAFT.
    @ParameterizedTest
    @ValueSource(strings = {"CONFIRMED", "COMPLETED", "CANCELLED"})
    // Confirm requires a draft; here you must fail before you touch inventory.
    void confirmationOfNonDraftFailsBeforeStockChanges(String state) {
        // It causes the protected header consultation to return the current case status.
        when(repository.header(1, true)).thenReturn(header(state));
        // Wait for the confirmation business error.
        assertThatThrownBy(() -> service.confirm(1)).isInstanceOf(BusinessException.class);
        // He verifies that no inventory, audit or state transition was called.
        verifyNoInteractions(inventory, audit);
        verify(repository, never()).transition(anyLong(), anyString(), any(), any());
    }

    // A draft without lines cannot reserve products or move to confirmed.
    @Test
    void emptyOrderCannotTransitionOrReserveStock() {
        // It simulates a DRAFT order whose list of lines is empty.
        when(repository.header(1, true)).thenReturn(header("DRAFT"));
        when(repository.lines(1)).thenReturn(List.of());
        // Confirmation must throw an exception before any further changes are made.
        assertThatThrownBy(() -> service.confirm(1)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(inventory, audit);
        verify(repository, never()).transition(anyLong(), anyString(), any(), any());
    }

    // A quote based on an old version cannot consume the stock.
    @Test
    void staleCarrierQuoteCannotConsumeStock() {
        // It simulates current version 3; the call uses expected version 2.
        when(repository.header(1, true)).thenReturn(header("CONFIRMED"));
        // Wait for the conflict before you call inventory or change the state.
        assertThatThrownBy(
                        () ->
                                service.complete(
                                        1, 2, new ShippingClient.Quote("quote", BigDecimal.ONE)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(inventory, audit);
        verify(repository, never()).transition(anyLong(), anyString(), any(), any());
    }
}
