package com.acme.operations.returns;

import com.acme.operations.common.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/returns")
@Validated
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
public class ReturnController {
    private final ReturnService service;

    public ReturnController(ReturnService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','READONLY')")
    public PageResponse<ReturnDtos.Summary> search(
            @RequestParam(required = false) Long orderId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return service.search(orderId, status, page, size, sort);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','READONLY')")
    public ReturnDtos.View get(@PathVariable @Min(1) long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<ReturnDtos.View> create(@Valid @RequestBody ReturnDtos.Create request) {
        var result = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/returns/" + result.id())).body(result);
    }

    @PostMapping("/{id}/approve")
    public ReturnDtos.View approve(@PathVariable @Min(1) long id,
                                  @Valid @RequestBody ReturnDtos.Decision request) {
        return service.approve(id, request);
    }

    @PostMapping("/{id}/reject")
    public ReturnDtos.View reject(@PathVariable @Min(1) long id,
                                 @Valid @RequestBody ReturnDtos.Decision request) {
        return service.reject(id, request);
    }

    @PostMapping("/{id}/receive")
    public ReturnDtos.View receive(@PathVariable @Min(1) long id,
                                  @Valid @RequestBody ReturnDtos.Receive request) {
        return service.receive(id, request);
    }
}
