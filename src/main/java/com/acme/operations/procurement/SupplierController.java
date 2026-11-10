package com.acme.operations.procurement;

import com.acme.operations.common.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/suppliers")
@Validated
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','READONLY')")
public class SupplierController {
    private final ProcurementService service;

    public SupplierController(ProcurementService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ProcurementDtos.Supplier> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        return service.suppliers(search, active, page, size, sort, direction);
    }

    @GetMapping("/{id}")
    public ProcurementDtos.Supplier get(@PathVariable @Positive long id) {
        return service.supplier(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ResponseEntity<ProcurementDtos.Supplier> create(@Valid @RequestBody ProcurementDtos.SupplierInput input) {
        var supplier = service.createSupplier(input);
        return ResponseEntity.created(URI.create("/api/v1/suppliers/" + supplier.id())).body(supplier);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ProcurementDtos.Supplier update(
            @PathVariable @Positive long id, @Valid @RequestBody ProcurementDtos.SupplierInput input) {
        return service.updateSupplier(id, input);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ResponseEntity<Void> deactivate(@PathVariable @Positive long id) {
        service.deactivateSupplier(id);
        return ResponseEntity.noContent().build();
    }
}
