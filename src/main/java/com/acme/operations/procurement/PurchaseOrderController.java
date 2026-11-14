package com.acme.operations.procurement;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.acme.operations.common.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;

@Tag(name = "Purchases")
@RestController
@RequestMapping("/api/v1/purchase-orders")
@Validated
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','READONLY')")
public class PurchaseOrderController {
    private final ProcurementService service;

    public PurchaseOrderController(ProcurementService service) {
        this.service = service;
    }

    @Operation(summary = "List purchase orders")
    @GetMapping
    public PageResponse<ProcurementDtos.PurchaseSummary> search(
            @RequestParam(required = false) @Positive Long supplierId,
            @Parameter(description = "Filter by status.", schema = @Schema(allowableValues = {
                    "DRAFT", "SUBMITTED", "PARTIALLY_RECEIVED", "RECEIVED", "CANCELLED"}))
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate dueBefore,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return service.search(supplierId, status, dueBefore, page, size, sort, direction);
    }

    @Operation(summary = "View a purchase order and its lines")
    @GetMapping("/{id}")
    public ProcurementDtos.Purchase get(@PathVariable @Positive long id) {
        return service.get(id);
    }

    @Operation(summary = "Create a draft purchase order")
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ResponseEntity<ProcurementDtos.Purchase> create(@Valid @RequestBody ProcurementDtos.PurchaseCreate input) {
        var order = service.create(input);
        return ResponseEntity.created(URI.create("/api/v1/purchase-orders/" + order.id())).body(order);
    }

    @Operation(summary = "Set the quantity and unit cost for a product")
    @PutMapping("/{id}/lines/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ProcurementDtos.Purchase setLine(@PathVariable @Positive long id,
            @PathVariable @Positive long productId, @Valid @RequestBody ProcurementDtos.SetLine input) {
        return service.setLine(id, productId, input);
    }

    @Operation(summary = "Remove a product from a purchase order")
    @DeleteMapping("/{id}/lines/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ProcurementDtos.Purchase removeLine(@PathVariable @Positive long id, @PathVariable @Positive long productId) {
        return service.removeLine(id, productId);
    }

    @Operation(summary = "Submit the purchase order to the supplier",
            description = "Stock increases when the warehouse records a delivery.")
    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ProcurementDtos.Purchase submit(@PathVariable @Positive long id) {
        return service.submit(id);
    }

    @Operation(summary = "Cancel a purchase order",
            description = "Draft and submitted orders can be cancelled. A partial receipt prevents cancellation.")
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ProcurementDtos.Purchase cancel(@PathVariable @Positive long id) {
        return service.cancel(id);
    }

    @Operation(summary = "Receive goods against a purchase order",
            description = "Record the units delivered and the supplier delivery reference. Reuse the original request ID when retrying the same receipt.")
    @PostMapping("/{id}/receipts")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ResponseEntity<ProcurementDtos.Receipt> receive(@PathVariable @Positive long id,
            @Valid @RequestBody ProcurementDtos.Receive input) {
        var receipt = service.receive(id, input);
        // A replay returns the immutable receipt and the same Location, without receiving stock twice.
        return ResponseEntity.ok().location(URI.create("/api/v1/purchase-orders/" + id + "/receipts/" + receipt.id()))
                .body(receipt);
    }

    @Operation(summary = "List deliveries received for a purchase order")
    @GetMapping("/{id}/receipts")
    public PageResponse<ProcurementDtos.ReceiptSummary> receipts(@PathVariable @Positive long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.receipts(id, page, size);
    }

    @Operation(summary = "View a delivery receipt")
    @GetMapping("/{id}/receipts/{receiptId}")
    public ProcurementDtos.Receipt receipt(@PathVariable @Positive long id, @PathVariable @Positive long receiptId) {
        return service.receipt(id, receiptId);
    }
}
