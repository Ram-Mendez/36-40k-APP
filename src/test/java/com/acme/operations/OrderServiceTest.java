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

// Mockito prepara los dobles sin arrancar Spring ni una base de datos.
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    // Los repositorios y servicios son simulados; permiten observar qué intentó hacer OrderService.
    @Mock OrderRepository repository;
    @Mock InventoryService inventory;
    @Mock AuditService audit;
    // Inyecta los mocks en el servicio que se va a probar.
    @InjectMocks OrderService service;

    // Fabrica una cabecera de ejemplo con el estado indicado y versión 3.
    private OrderRepository.Header header(String state) {
        return new OrderRepository.Header(
                1, 2, "Client", state, BigDecimal.TEN, 3, null, null, Instant.EPOCH, Instant.EPOCH);
    }

    // Repite el caso con tres estados que no son DRAFT.
    @ParameterizedTest
    @ValueSource(strings = {"CONFIRMED", "COMPLETED", "CANCELLED"})
    // Confirmar exige un borrador; aquí debe fallar antes de tocar inventario.
    void confirmationOfNonDraftFailsBeforeStockChanges(String state) {
        // Hace que la consulta de cabecera protegida devuelva el estado del caso actual.
        when(repository.header(1, true)).thenReturn(header(state));
        // Espera el error de negocio de confirmación.
        assertThatThrownBy(() -> service.confirm(1)).isInstanceOf(BusinessException.class);
        // Verifica que no se llamó a inventario, auditoría ni transición de estado.
        verifyNoInteractions(inventory, audit);
        verify(repository, never()).transition(anyLong(), anyString(), any(), any());
    }

    // Un borrador sin líneas no puede reservar productos ni pasar a confirmado.
    @Test
    void emptyOrderCannotTransitionOrReserveStock() {
        // Simula un pedido DRAFT cuya lista de líneas está vacía.
        when(repository.header(1, true)).thenReturn(header("DRAFT"));
        when(repository.lines(1)).thenReturn(List.of());
        // La confirmación debe lanzar una excepción sin hacer cambios posteriores.
        assertThatThrownBy(() -> service.confirm(1)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(inventory, audit);
        verify(repository, never()).transition(anyLong(), anyString(), any(), any());
    }

    // Una cotización basada en una versión antigua no puede consumir el stock.
    @Test
    void staleCarrierQuoteCannotConsumeStock() {
        // Simula versión actual 3; la llamada utiliza versión esperada 2.
        when(repository.header(1, true)).thenReturn(header("CONFIRMED"));
        // Espera el conflicto antes de llamar a inventario o cambiar el estado.
        assertThatThrownBy(
                        () ->
                                service.complete(
                                        1, 2, new ShippingClient.Quote("quote", BigDecimal.ONE)))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(inventory, audit);
        verify(repository, never()).transition(anyLong(), anyString(), any(), any());
    }
}
