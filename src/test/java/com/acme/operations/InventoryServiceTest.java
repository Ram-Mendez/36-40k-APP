package com.acme.operations;

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

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {
    @Mock InventoryRepository repository;
    @Mock AuditService audit;
    @InjectMocks InventoryService service;

    @ParameterizedTest
    @ValueSource(longs = {0, 1000000001L, -1000000001L, Long.MAX_VALUE, Long.MIN_VALUE})
    void rejectsUnsupportedAdjustmentsBeforeAnyWrite(long quantity) {
        assertThatThrownBy(() -> service.adjust(1, new InventoryDtos.Adjustment(quantity, "Count")))
                .isInstanceOf(BusinessException.class);
        verifyNoInteractions(repository, audit);
    }

    @Test
    void adjustmentCannotConsumeReservedStock() {
        when(repository.productExists(4)).thenReturn(true);
        when(repository.lock(4)).thenReturn(new InventoryRepository.LockedBalance(10, 8));
        assertThatThrownBy(() -> service.adjust(4, new InventoryDtos.Adjustment(-5L, "Count")))
                .isInstanceOf(BusinessException.class);
        verify(repository, never()).updateOnHand(anyLong(), anyLong());
        verify(repository, never())
                .insertMovement(anyLong(), any(), anyString(), anyLong(), anyString());
        verifyNoInteractions(audit);
    }

    @Test
    void successfulAdjustmentRecordsBusinessContextAfterWrite() {
        when(repository.productExists(4)).thenReturn(true);
        when(repository.lock(4)).thenReturn(new InventoryRepository.LockedBalance(10, 8));
        service.adjust(4, new InventoryDtos.Adjustment(5L, "Receipt"));
        var sequence = inOrder(repository, audit);
        sequence.verify(repository).updateOnHand(4, 15);
        sequence.verify(repository).insertMovement(4, null, "ADJUSTMENT", 5, "Receipt");
        sequence.verify(audit).record("STOCK_ADJUSTED", "PRODUCT", "4", "5: Receipt");
    }

    @Test
    void insufficientAvailableStockRejectsReservationBeforeWrites() {
        when(repository.lock(4)).thenReturn(new InventoryRepository.LockedBalance(10, 8));
        assertThatThrownBy(() -> service.reserve(1, 4, 3))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient");
        verify(repository, never()).insertReservation(anyLong(), anyLong(), anyLong());
        verify(repository, never())
                .insertMovement(anyLong(), any(), anyString(), anyLong(), anyString());
    }

    @Test
    void missingReservationCannotReleaseStock() {
        when(repository.lock(4)).thenReturn(new InventoryRepository.LockedBalance(10, 8));
        when(repository.deleteReservation(1, 4, 3)).thenReturn(0);
        assertThatThrownBy(() -> service.release(1, 4, 3, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Reservation");
        verify(repository, never()).decreaseReservedAndOnHand(anyLong(), anyLong(), anyLong());
        verify(repository, never())
                .insertMovement(anyLong(), any(), anyString(), anyLong(), anyString());
    }

    @Test
    void cancellationReleasesReservationWithoutDebitingOnHand() {
        when(repository.lock(4)).thenReturn(new InventoryRepository.LockedBalance(10, 8));
        when(repository.deleteReservation(1, 4, 3)).thenReturn(1);
        service.release(1, 4, 3, false);
        var sequence = inOrder(repository);
        sequence.verify(repository).lock(4);
        sequence.verify(repository).deleteReservation(1, 4, 3);
        sequence.verify(repository).decreaseReservedAndOnHand(4, 3, 0);
        sequence.verify(repository).insertMovement(4, 1L, "RELEASE", -3, "Order cancelled");
    }
}
