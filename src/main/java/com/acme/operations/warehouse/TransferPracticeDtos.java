package com.acme.operations.warehouse;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

final class TransferPracticeDtos {
    private TransferPracticeDtos() {}

    record Transfer(String code, String externalReference, long revision, String status) {}
    record Inspection(@NotNull Boolean passed) {}
    record Attempt(long id, String result) {}
    record Event(String transferCode, String eventType) {}

    record Damage(@NotBlank @Size(max = 80) String sku, @Size(max = 80) String reasonCode,
                  @NotNull @Positive Integer units) {}
    record DamageBalance(String sku, String reasonCode, long units) {}

    record Booking(@NotNull Instant startsAt, @NotNull Instant endsAt) {}
    record Slot(long id, String dock, Instant startsAt, Instant endsAt) {}

    record Job(int position, String status) {}

    record Weight(@NotNull @DecimalMin("0.001") @Digits(integer = 9, fraction = 3)
                  BigDecimal kilograms) {}

    record LotCount(String lot, int expectedUnits, Integer countedUnits) {}

    record ReceiptCondition(@NotNull Boolean damaged, @NotNull Boolean refrigerated) {}
    record Route(String destination) {}
}
