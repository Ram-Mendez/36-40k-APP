package com.acme.operations.inventory;

import com.acme.operations.common.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inventory")
@Validated
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','READONLY')")
public class InventoryController {
    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<InventoryDtos.Balance> balances(
            @RequestParam(required = false) Long productId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.balances(productId, page, size);
    }

    @PostMapping("/{productId}/adjustments")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public void adjust(
            @PathVariable long productId, @Valid @RequestBody InventoryDtos.Adjustment request) {
        service.adjust(productId, request);
    }

    @GetMapping("/movements")
    public PageResponse<InventoryDtos.Movement> movements(
            @RequestParam(required = false) Long productId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.movements(productId, page, size);
    }

    @GetMapping("/reservations")
    public PageResponse<InventoryDtos.Reservation> reservations(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.reservations(page, size);
    }
}
