package com.acme.operations.procurement;

import com.acme.operations.common.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/purchase-orders")
@Validated
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','READONLY')")
public class PurchaseOrderController {
    private final ProcurementService service;

    public PurchaseOrderController(ProcurementService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ProcurementDtos.PurchaseSummary> search(
            @RequestParam(required = false) @Positive Long supplierId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) LocalDate dueBefore,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return service.search(supplierId, status, dueBefore, page, size, sort, direction);
    }

    @GetMapping("/{id}")
    public ProcurementDtos.Purchase get(@PathVariable @Positive long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ResponseEntity<ProcurementDtos.Purchase> create(@Valid @RequestBody ProcurementDtos.PurchaseCreate input) {
        var order = service.create(input);
        return ResponseEntity.created(URI.create("/api/v1/purchase-orders/" + order.id())).body(order);
    }

    @PutMapping("/{id}/lines/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ProcurementDtos.Purchase setLine(@PathVariable @Positive long id,
            @PathVariable @Positive long productId, @Valid @RequestBody ProcurementDtos.SetLine input) {
        return service.setLine(id, productId, input);
    }

    @DeleteMapping("/{id}/lines/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ProcurementDtos.Purchase removeLine(@PathVariable @Positive long id, @PathVariable @Positive long productId) {
        return service.removeLine(id, productId);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ProcurementDtos.Purchase submit(@PathVariable @Positive long id) {
        return service.submit(id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ProcurementDtos.Purchase cancel(@PathVariable @Positive long id) {
        return service.cancel(id);
    }

    @PostMapping("/{id}/receipts")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ResponseEntity<ProcurementDtos.Receipt> receive(@PathVariable @Positive long id,
            @Valid @RequestBody ProcurementDtos.Receive input) {
        var receipt = service.receive(id, input);
        // A replay returns the immutable receipt and the same Location, without receiving stock twice.
        return ResponseEntity.ok().location(URI.create("/api/v1/purchase-orders/" + id + "/receipts/" + receipt.id()))
                .body(receipt);
    }

    @GetMapping("/{id}/receipts")
    public PageResponse<ProcurementDtos.ReceiptSummary> receipts(@PathVariable @Positive long id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.receipts(id, page, size);
    }

    @GetMapping("/{id}/receipts/{receiptId}")
    public ProcurementDtos.Receipt receipt(@PathVariable @Positive long id, @PathVariable @Positive long receiptId) {
        return service.receipt(id, receiptId);
    }
}
