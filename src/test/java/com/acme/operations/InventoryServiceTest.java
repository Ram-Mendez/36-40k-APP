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

// JUnit activates Mockito to create the doubles and inject them before each test.
@ExtendWith(MockitoExtension.class)
// Test the isolated inventory logic: do not use PostgreSQL or check the actual commit.
class InventoryServiceTest {
    // Simulated repository: the queries and scriptures are controlled and verified from the test.
    @Mock InventoryRepository repository;
    // Simulated audit to check if the service is trying to record any action.
    @Mock AuditService audit;
    // Mockito builds InventoryService using the above mocks.
    @InjectMocks InventoryService service;

    // Run the same case for each quantity outside the permitted range, including zero.
    @ParameterizedTest
    @ValueSource(longs = {0, 1000000001L, -1000000001L, Long.MAX_VALUE, Long.MIN_VALUE})
    // It shows that an invalid amount is rejected before accessing dependencies.
    void rejectsUnsupportedAdjustmentsBeforeAnyWrite(long quantity) {
        // Wait for BusinessException when trying to adjust; here the HTTP code is not checked.
        assertThatThrownBy(() -> service.adjust(1, new InventoryDtos.Adjustment(quantity, "Count")))
                .isInstanceOf(BusinessException.class);
        // He verifies that neither the repository nor the audit received calls.
        verifyNoInteractions(repository, audit);
    }

    // Proof that an adjustment does not leave less physical units than those already reserved.
    @Test
    void adjustmentCannotConsumeReservedStock() {
        // It simulates an existing product with 10 physical and 8 reserved.
        when(repository.productExists(4)).thenReturn(true);
        when(repository.lock(4)).thenReturn(new InventoryRepository.LockedBalance(10, 8));
        // Subtract 5 would leave 5 physicists for 8 reserved, so you must fail.
        assertThatThrownBy(() -> service.adjust(4, new InventoryDtos.Adjustment(-5L, "Count")))
                .isInstanceOf(BusinessException.class);
        // It shows that the service did not attempt to save balance, movement or audit.
        verify(repository, never()).updateOnHand(anyLong(), anyLong());
        verify(repository, never())
                .insertMovement(anyLong(), any(), anyString(), anyLong(), anyString());
        verifyNoInteractions(audit);
    }

    // Check the sequence of a valid adjustment and the data that are recorded.
    @Test
    void successfulAdjustmentRecordsBusinessContextAfterWrite() {
        // The simulated starting balance is 10 physical and 8 reserved.
        when(repository.productExists(4)).thenReturn(true);
        when(repository.lock(4)).thenReturn(new InventoryRepository.LockedBalance(10, 8));
        // Add 5 units; the new expected physical balance is 15.
        service.adjust(4, new InventoryDtos.Adjustment(5L, "Receipt"));
        // inOrder allows you to demand the call order from both mocks.
        var sequence = inOrder(repository, audit);
        // First, it updates the balance; then it records the movement and finally the audit.
        sequence.verify(repository).updateOnHand(4, 15);
        sequence.verify(repository).insertMovement(4, null, "ADJUSTMENT", 5, "Receipt");
        sequence.verify(audit).record("STOCK_ADJUSTED", "PRODUCT", "4", "5: Receipt");
    }

    // Two available units are not enough to book three.
    @Test
    void insufficientAvailableStockRejectsReservationBeforeWrites() {
        // It simulates 10 physical and 8 reserved without consulting a real base.
        when(repository.lock(4)).thenReturn(new InventoryRepository.LockedBalance(10, 8));
        // He expects the exception of insufficient stock when applying for three units.
        assertThatThrownBy(() -> service.reserve(1, 4, 3))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient");
        // It shows that no attempt was made to insert a reservation or movement.
        verify(repository, never()).insertReservation(anyLong(), anyLong(), anyLong());
        verify(repository, never())
                .insertMovement(anyLong(), any(), anyString(), anyLong(), anyString());
    }

    // If stock is available, the reserve and its movement use the amount of the order.
    @Test
    void sufficientAvailableStockCreatesReservationAndMovement() {
        // It simulates 10 physical units and 2 reserved units: 8 are available.
        when(repository.lock(4)).thenReturn(new InventoryRepository.LockedBalance(10, 2));

        // Order 1 requests to book 3 units of product 4.
        service.reserve(1, 4, 3);

        // Check the reservation and movement the service asks to save.
        verify(repository).insertReservation(1, 4, 3);
        verify(repository).insertMovement(4, 1L, "RESERVE", 3, "Order confirmed");
    }

    // Reserve zero units must be rejected before consulting or modifying stock.
    @Test
    void zeroQuantityCannotBeReserved() {
        // Run the invalid reservation and check the code and state of the exception.
        assertThatThrownBy(() -> service.reserve(1, 4, 0))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("INVALID_QUANTITY");
                    assertThat(exception.getStatus()).isEqualTo(org.springframework.http.HttpStatus.BAD_REQUEST);
                });

        // Validation occurs before calling the repository or audit.
        verifyNoInteractions(repository, audit);
    }

    // A release must correspond to an existing reservation for that order and quantity.
    @Test
    void missingReservationCannotReleaseStock() {
        // It simulates reserved balance, but the erasure returns zero rows: the reservation does not match.
        when(repository.lock(4)).thenReturn(new InventoryRepository.LockedBalance(10, 8));
        when(repository.deleteReservation(1, 4, 3)).thenReturn(0);
        // Wait for the rejection and check that no balances are adjusted and no movement added.
        assertThatThrownBy(() -> service.release(1, 4, 3, false))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Reservation");
        verify(repository, never()).decreaseReservedAndOnHand(anyLong(), anyLong(), anyLong());
        verify(repository, never())
                .insertMovement(anyLong(), any(), anyString(), anyLong(), anyString());
    }

    // When you cancel, you release the reservation without discounting physical products.
    @Test
    void cancellationReleasesReservationWithoutDebitingOnHand() {
        // It simulates a valid balance and that a reservation was exactly deleted.
        when(repository.lock(4)).thenReturn(new InventoryRepository.LockedBalance(10, 8));
        when(repository.deleteReservation(1, 4, 3)).thenReturn(1);
        // false represents cancellation: no physical exit from the warehouse.
        service.release(1, 4, 3, false);
        // Check the sequence: lock the row, delete the reservation, reduce reserved stock and record RELEASE.
        var sequence = inOrder(repository);
        sequence.verify(repository).lock(4);
        sequence.verify(repository).deleteReservation(1, 4, 3);
        // The last zero indicates that on_hand should not decrease.
        sequence.verify(repository).decreaseReservedAndOnHand(4, 3, 0);
        // The movement keeps -3 because it releases three reserved units.
        sequence.verify(repository).insertMovement(4, 1L, "RELEASE", -3, "Order cancelled");
    }
}
