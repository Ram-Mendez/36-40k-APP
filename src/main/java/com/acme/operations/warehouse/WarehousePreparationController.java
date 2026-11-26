package com.acme.operations.warehouse;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("dev & !turno-cero")
@Tag(name = "Traslados de almacén")
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
@RequestMapping("/api/v1/warehouse-transfers/{code}/preparation")
class WarehousePreparationController {
    record PackLine(@NotNull @PositiveOrZero @Max(1000) Integer boxes,
                    @NotNull @Positive @Max(1000) Integer unitsPerBox,
                    @NotNull @PositiveOrZero @Max(1000) Integer looseUnits) {}
    record Packing(@NotEmpty @Size(max = 50) List<@NotNull @Valid PackLine> lines) {}
    record PackedUnits(int totalUnits) {}
    record Load(@NotNull @Positive Integer parcels, @NotNull @Positive Integer kilograms) {}
    record Capacity(boolean fits) {}
    record Delivery(@NotNull LocalDate departureDate, @NotNull @Min(0) @Max(30) Integer workingDays) {}
    record Arrival(LocalDate arrivalDate) {}
    record CartPlan(@NotNull @Min(1) @Max(1000) Integer parcels, @NotNull @Min(1) @Max(40) Integer carts) {}
    record CartLoads(List<Integer> parcelsPerCart) {}

    private final TransferPracticeService transfers;
    private final WarehousePreparationService preparation;

    WarehousePreparationController(TransferPracticeService transfers, WarehousePreparationService preparation) {
        this.transfers = transfers;
        this.preparation = preparation;
    }

    @PostMapping("/packed-units")
    public PackedUnits packedUnits(@PathVariable String code, @Valid @RequestBody Packing request) {
        transfers.transfer(code);
        return new PackedUnits(preparation.packedUnits(request.lines()));
    }

    @PostMapping("/capacity")
    public Capacity capacity(@PathVariable String code, @Valid @RequestBody Load request) {
        transfers.transfer(code);
        return new Capacity(preparation.fits(request.parcels(), request.kilograms()));
    }

    @PostMapping("/arrival")
    public Arrival arrival(@PathVariable String code, @Valid @RequestBody Delivery request) {
        transfers.transfer(code);
        return new Arrival(preparation.arrival(request.departureDate(), request.workingDays()));
    }

    @PostMapping("/cart-loads")
    public CartLoads cartLoads(@PathVariable String code, @Valid @RequestBody CartPlan request) {
        transfers.transfer(code);
        return new CartLoads(preparation.cartLoads(request.parcels(), request.carts()));
    }
}
