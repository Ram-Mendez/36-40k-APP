package com.acme.operations.returns;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.acme.operations.common.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@Tag(name = "Returns")
@RestController
@RequestMapping("/api/v1/returns")
@Validated
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
public class ReturnController {
    private final ReturnService service;

    public ReturnController(ReturnService service) {
        this.service = service;
    }

    @Operation(summary = "List return requests")
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','READONLY')")
    public PageResponse<ReturnDtos.Summary> search(
            @RequestParam(required = false) Long orderId,
            @Parameter(description = "Filter by status.", schema = @Schema(allowableValues = {
                    "REQUESTED", "APPROVED", "REJECTED", "RECEIVED"}))
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return service.search(orderId, status, page, size, sort);
    }

    @Operation(summary = "View a return request")
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','READONLY')")
    public ReturnDtos.View get(@PathVariable @Min(1) long id) {
        return service.get(id);
    }

    @Operation(summary = "Request a return for items sold",
            description = "Customer service requests a return against a completed sales order. The requested units are reserved for review.")
    @PostMapping
    public ResponseEntity<ReturnDtos.View> create(@Valid @RequestBody ReturnDtos.Create request) {
        var result = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/returns/" + result.id())).body(result);
    }

    @Operation(summary = "Approve a return request")
    @PostMapping("/{id}/approve")
    public ReturnDtos.View approve(@PathVariable @Min(1) long id,
                                  @Valid @RequestBody ReturnDtos.Decision request) {
        return service.approve(id, request);
    }

    @Operation(summary = "Reject a return request",
            description = "Release the units in this request so the customer can request another return.")
    @PostMapping("/{id}/reject")
    public ReturnDtos.View reject(@PathVariable @Min(1) long id,
                                 @Valid @RequestBody ReturnDtos.Decision request) {
        return service.reject(id, request);
    }

    @Operation(summary = "Receive and inspect returned items",
            description = "Record the units that can be restocked and the units that were damaged.")
    @PostMapping("/{id}/receive")
    public ReturnDtos.View receive(@PathVariable @Min(1) long id,
                                  @Valid @RequestBody ReturnDtos.Receive request) {
        return service.receive(id, request);
    }
}
