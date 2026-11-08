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

// Mockito crea los colaboradores simulados sin levantar Spring ni PostgreSQL.
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {
    // El repositorio, inventario y auditoría son dobles controlados por la prueba.
    @Mock OrderRepository orders;
    @Mock InventoryService inventory;
    @Mock AuditService audit;
    OrderService service;

    // JUnit ejecuta este método antes de cada caso para crear un servicio limpio.
    @BeforeEach
    void setup() {
        // Construye manualmente OrderService con los mocks; no pasa por un proxy de Spring.
        service = new OrderService(orders, inventory, audit);
    }

    // Crea una cabecera de ejemplo con estado configurable y versión 2.
    private OrderRepository.Header header(String status) {
        return new OrderRepository.Header(
                1, 1, "Acme", status, BigDecimal.TEN, 2, null, null, Instant.now(), Instant.now());
    }

    // Un pedido sin líneas no debe reservar unidades ni cambiar de estado.
    @Test
    void emptyOrderCannotReserveOrTransition() {
        // Simula la lectura de un borrador y una lista vacía de líneas.
        when(orders.header(1, true)).thenReturn(header("DRAFT"));
        when(orders.lines(1)).thenReturn(List.of());
        // Exige una excepción cuyo mensaje indique que falta al menos una línea.
        assertThatThrownBy(() -> service.confirm(1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("at least one line");
        // Verifica que no se llamó a inventario, auditoría ni transición.
        verifyNoInteractions(inventory, audit);
        verify(orders, never()).transition(anyLong(), anyString(), any(), any());
    }

    // Un pedido completado no puede cancelarse ni liberar stock.
    @Test
    void completedOrderCannotBeCancelledOrReleaseStock() {
        // Simula que la cabecera ya está en COMPLETED.
        when(orders.header(1, true)).thenReturn(header("COMPLETED"));
        // Comprueba el rechazo y que no se llamen las dependencias que cambian datos.
        assertThatThrownBy(() -> service.cancel(1)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(inventory, audit);
        verify(orders, never()).transition(anyLong(), anyString(), any(), any());
    }

    // Cancelar un pedido confirmado libera su reserva y cambia el estado a CANCELLED.
    @Test
    void cancellationOfConfirmedOrderReleasesReservedUnits() {
        // Simula un pedido confirmado con 3 unidades reservadas del producto 4.
        var line = new OrderDtos.Line(6, 4, "SKU-4", "Producto", 3, BigDecimal.TEN, BigDecimal.valueOf(30));
        var cancelled = new OrderDtos.Order(
                1, 1, "Acme", "CANCELLED", BigDecimal.valueOf(30), 3,
                null, null, Instant.EPOCH, Instant.EPOCH, List.of(line));
        when(orders.header(1, true)).thenReturn(header("CONFIRMED"));
        when(orders.lines(1)).thenReturn(List.of(line));
        when(orders.get(1)).thenReturn(cancelled);

        // Ejecuta la cancelación y comprueba que devuelve el pedido consultado después del cambio.
        assertThat(service.cancel(1)).isSameAs(cancelled);

        // false indica que se libera la reserva sin enviar las unidades.
        verify(inventory).release(1, 4, 3, false);
        verify(orders).transition(1, "CANCELLED", null, null);
    }

    // Una versión distinta de la usada para cotizar invalida la finalización.
    @Test
    void staleQuoteCannotDebitStock() {
        // La versión actual simulada es 2 y el llamador presenta la versión antigua 1.
        when(orders.header(1, true)).thenReturn(header("CONFIRMED"));
        // Espera el conflicto sin intentar descontar inventario.
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

    // Un pedido en DRAFT no puede completarse aunque se aporte una cotización.
    @Test
    void draftCannotBeCompleted() {
        // Prepara el estado inválido y exige que la operación falle antes de tocar stock.
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
