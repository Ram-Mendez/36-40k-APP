package com.acme.operations.replenishment;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.acme.operations.common.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Replenishment")
@RestController
@RequestMapping("/api/v1/replenishment")
@Validated
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','READONLY')")
public class ReplenishmentController {
    private final ReplenishmentService service;

    public ReplenishmentController(ReplenishmentService service) {
        this.service = service;
    }

    @Operation(summary = "View the stock thresholds and preferred supplier for a product")
    @GetMapping("/policies/{productId}")
    public ReplenishmentDtos.Policy get(@PathVariable @Positive long productId) {
        return service.get(productId);
    }

    @Operation(summary = "Set the minimum stock, target stock and preferred supplier")
    @PutMapping("/policies/{productId}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ReplenishmentDtos.Policy upsert(
            @PathVariable @Positive long productId, @Valid @RequestBody ReplenishmentDtos.PolicyInput input) {
        return service.upsert(productId,input);
    }

    @Operation(summary = "List replenishment policies")
    @GetMapping("/policies")
    public PageResponse<ReplenishmentDtos.Policy> policies(
            @RequestParam(required = false) @Positive Long productId,
            @RequestParam(required = false) @Positive Long preferredSupplierId,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) @Size(max = 200) String search,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.policies(productId,preferredSupplierId,active,search,page,size);
    }

    @Operation(summary = "List suggested quantities to purchase",
            description = "Compare available stock plus outstanding purchases with the minimum stock level for each product.")
    @GetMapping("/proposals")
    public PageResponse<ReplenishmentDtos.Proposal> proposals(
            @RequestParam(required = false) @Positive Long productId,
            @RequestParam(required = false) @Positive Long preferredSupplierId,
            @RequestParam(required = false) @Size(max = 200) String search,
            @RequestParam(defaultValue = "0") @Min(0) @Max(1000000) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.proposals(productId,preferredSupplierId,search,page,size);
    }
}
