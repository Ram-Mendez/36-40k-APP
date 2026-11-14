package com.acme.operations.inventory;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.acme.operations.common.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

// Spring handles requests to these routes and serializes the response as JSON.
@Tag(name = "Inventory")
@RestController
// Each method path is relative to this base path.
@RequestMapping("/api/v1/inventory")
// Validate the constraints on method parameters.
@Validated
// ADMIN, OPERATIONS and READONLY can read this resource.
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','READONLY')")
public class InventoryController {
    // The controller delegates business operations to this service.
    private final InventoryService service;

    // Spring injects the dependencies through the single constructor.
    public InventoryController(InventoryService service) {
        // Keep the injected dependency for later calls.
        this.service = service;
    }

    // Handle GET requests to this route.
    @Operation(summary = "View stock balances")
    @GetMapping
    // Return the physical, reserved and available stock for each product.
    public PageResponse<InventoryDtos.Balance> balances(
            // An omitted query parameter leaves this filter unset.
            @RequestParam(required = false) Long productId,
            // Read the zero-based page number, using the default if omitted.
            @RequestParam(defaultValue = "0") @Min(0) int page,
            // Validate the requested number of results per page.
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        // Return the physical, reserved and available stock for each product.
        return service.balances(productId, page, size);
    }

    // Handle POST requests and delegate the operation to the service.
    @Operation(summary = "Adjust stock for a product")
    @PostMapping("/{productId}/adjustments")
    // Return HTTP 204 with an empty body on success.
    @ResponseStatus(HttpStatus.NO_CONTENT)
    // Only ADMIN and OPERATIONS can perform this operation.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Save the stock adjustment, movement and audit event in one transaction.
    public void adjust(
            // Spring reads the ID from the URL and any request body from JSON.
            @PathVariable long productId, @Valid @RequestBody InventoryDtos.Adjustment request) {
        // Save the stock adjustment, movement and audit event in one transaction.
        service.adjust(productId, request);
    }

    // Handle GET requests to this route.
    @Operation(summary = "List stock movements")
    @GetMapping("/movements")
    // Returns the movement history; does not calculate a new reserve.
    public PageResponse<InventoryDtos.Movement> movements(
            // An omitted query parameter leaves this filter unset.
            @RequestParam(required = false) Long productId,
            // Read the zero-based page number, using the default if omitted.
            @RequestParam(defaultValue = "0") @Min(0) int page,
            // Validate the requested number of results per page.
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        // Returns the movement history; does not calculate a new reserve.
        return service.movements(productId, page, size);
    }

    // Handle GET requests to this route.
    @Operation(summary = "List current stock reservations")
    @GetMapping("/reservations")
    // List existing reservations per order and product; this GET does not reserve units.
    public PageResponse<InventoryDtos.Reservation> reservations(
            // Read the zero-based page number, using the default if omitted.
            @RequestParam(defaultValue = "0") @Min(0) int page,
            // Validate the requested number of results per page.
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        // List existing reservations per order and product; this GET does not reserve units.
        return service.reservations(page, size);
    }
}
