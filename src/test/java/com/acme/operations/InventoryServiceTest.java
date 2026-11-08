package com.acme.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.acme.operations.common.AuditService;
import com.acme.operations.common.BusinessException;
import com.acme.operations.inventory.InventoryDtos;
import com.acme.operations.inventory.InventoryRepository;
import com.acme.operations.inventory.InventoryService;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

// JUnit activa Mockito para crear los dobles e inyectarlos antes de cada prueba.
@ExtendWith(MockitoExtension.class)
// Prueba la lógica de inventario aislada: no usa PostgreSQL ni verifica el commit real.
class InventoryServiceTest {
    // Repositorio simulado: las consultas y escrituras se controlan y verifican desde la prueba.
    @Mock InventoryRepository repository;
    // Auditoría simulada para comprobar si el servicio intenta registrar alguna acción.
    @Mock AuditService audit;
    // Mockito construye InventoryService usando los mocks anteriores.
    @InjectMocks InventoryService service;

    // Ejecuta el mismo caso para cada cantidad fuera del rango permitido, incluido cero.
    @ParameterizedTest
    @ValueSource(longs = {0, 1000000001L, -1000000001L, Long.MAX_VALUE, Long.MIN_VALUE})
    // Comprueba que una cantidad inválida se rechace antes de acceder a dependencias.
    void rejectsUnsupportedAdjustmentsBeforeAnyWrite(long quantity) {
        // Espera BusinessException al intentar el ajuste; aquí no se comprueba el código HTTP.
        assertThatThrownBy(() -> service.adjust(1, new InventoryDtos.Adjustment(quantity, "Count")))
                .isInstanceOf(BusinessException.class);
        // Verifica que ni el repositorio ni la auditoría recibieron llamadas.
        verifyNoInteractions(repository, audit);
    }

    // Prueba que un ajuste no deje menos unidades físicas que las ya reservadas.
    @Test
    void adjustmentCannotConsumeReservedStock() {
        // Simula un producto existente con 10 físicas y 8 reservadas.
        when(repository.productExists(4)).thenReturn(true);
        when(repository.lock(4)).thenReturn(new InventoryRepository.LockedBalance(10, 8));
        // Restar 5 dejaría 5 físicas para 8 reservadas, por lo que debe fallar.
        assertThatThrownBy(() -> service.adjust(4, new InventoryDtos.Adjustment(-5L, "Count")))
                .isInstanceOf(BusinessException.class);
        // Comprueba que el servicio no intentó guardar saldo, movimiento ni auditoría.
        verify(repository, never()).updateOnHand(anyLong(), anyLong());
        verify(repository, never())
                .insertMovement(anyLong(), any(), anyString(), anyLong(), anyString());
        verifyNoInteractions(audit);
    }

    // Comprueba la secuencia de un ajuste válido y los datos que se registran.
    @Test
    void successfulAdjustmentRecordsBusinessContextAfterWrite() {
        // El saldo inicial simulado es 10 físicas y 8 reservadas.
        when(repository.productExists(4)).thenReturn(true);
        when(repository.lock(4)).thenReturn(new InventoryRepository.LockedBalance(10, 8));
        // Añade 5 unidades; el nuevo saldo físico esperado es 15.
        service.adjust(4, new InventoryDtos.Adjustment(5L, "Receipt"));
        // inOrder permite exigir el orden de llamadas a ambos mocks.
        var sequence = inOrder(repository, audit);
        // Primero actualiza el saldo; después registra el movimiento y finalmente la auditoría.
        sequence.verify(repository).updateOnHand(4, 15);
        sequence.verify(repository).insertMovement(4, null, "ADJUSTMENT", 5, "Receipt");
        sequence.verify(audit).record("STOCK_ADJUSTED", "PRODUCT", "4", "5: Receipt");
    }

    // Dos unidades disponibles no alcanzan para reservar tres.
    @Test
    void insufficientAvailableStockRejectsReservationBeforeWrites() {
        // Simula 10 físicas y 8 reservadas sin consultar una base real.
        when(repository.lock(4)).thenReturn(new InventoryRepository.LockedBalance(10, 8));
        // Espera la excepción de stock insuficiente al solicitar tres unidades.
        assertThatThrownBy(() -> service.reserve(1, 4, 3))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient");
        // Comprueba que no se intentó insertar una reserva ni un movimiento.
        verify(repository, never()).insertReservation(anyLong(), anyLong(), anyLong());
        verify(repository, never())
                .insertMovement(anyLong(), any(), anyString(), anyLong(), anyString());
    }

    // Si hay stock disponible, la reserva y su movimiento usan la cantidad del pedido.
    @Test
    void sufficientAvailableStockCreatesReservationAndMovement() {
        // Simula 10 unidades físicas y 2 reservadas: quedan 8 disponibles.
        when(repository.lock(4)).thenReturn(new InventoryRepository.LockedBalance(10, 2));

        // El pedido 1 solicita reservar 3 unidades del producto 4.
        service.reserve(1, 4, 3);

        // Comprueba la reserva y el movimiento que el servicio pide guardar.
        verify(repository).insertReservation(1, 4, 3);
        verify(repository).insertMovement(4, 1L, "RESERVE", 3, "Order confirmed");
    }

    // Reservar cero unidades debe rechazarse antes de consultar o modificar el stock.
    @Test
    void zeroQuantityCannotBeReserved() {
        // Ejecuta la reserva inválida y comprueba el código y estado de la excepción.
        assertThatThrownBy(() -> service.reserve(1, 4, 0))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("INVALID_QUANTITY");
                    assertThat(exception.getStatus()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
                });

        // La validación ocurre antes de llamar al repositorio o a la auditoría.
        verifyNoInteractions(repository, audit);
    }

    // Una liberación debe corresponder a una reserva existente para ese pedido y cantidad.
    @Test
    void missingReservationCannotReleaseStock() {
        // Simula saldo reservado, pero el borrado devuelve cero filas: no coincide la reserva.
        when(repository.lock(4)).thenReturn(new InventoryRepository.LockedBalance(10, 8));
        when(repository.deleteReservation(1, 4, 3)).thenReturn(0);
        // Espera el rechazo y comprueba que no se ajusten saldos ni se añada movimiento.
        assertThatThrownBy(() -> service.release(1, 4, 3, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Reservation");
        verify(repository, never()).decreaseReservedAndOnHand(anyLong(), anyLong(), anyLong());
        verify(repository, never())
                .insertMovement(anyLong(), any(), anyString(), anyLong(), anyString());
    }

    // Al cancelar, libera la reserva sin descontar productos físicos.
    @Test
    void cancellationReleasesReservationWithoutDebitingOnHand() {
        // Simula saldo válido y que se borró exactamente una reserva.
        when(repository.lock(4)).thenReturn(new InventoryRepository.LockedBalance(10, 8));
        when(repository.deleteReservation(1, 4, 3)).thenReturn(1);
        // false representa cancelación: no hay salida física del almacén.
        service.release(1, 4, 3, false);
        // Comprueba el orden: bloqueo, borrado, resta de reservado y movimiento RELEASE.
        var sequence = inOrder(repository);
        sequence.verify(repository).lock(4);
        sequence.verify(repository).deleteReservation(1, 4, 3);
        // El último cero indica que on_hand no debe disminuir.
        sequence.verify(repository).decreaseReservedAndOnHand(4, 3, 0);
        // El movimiento guarda -3 porque libera tres unidades reservadas.
        sequence.verify(repository).insertMovement(4, 1L, "RELEASE", -3, "Order cancelled");
    }
}
