package com.acme.operations.order;

import com.acme.operations.common.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/orders")
@Validated
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
public class OrderController {
    private final OrderService service;
    private final OrderCompletionService completion;

    public OrderController(OrderService service, OrderCompletionService completion) {
        this.service = service;
        this.completion = completion;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','READONLY')")
    public PageResponse<OrderDtos.Order> search(
            @RequestParam(required = false) Long customerId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        return service.search(customerId, status, page, size, sort);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','READONLY')")
    public OrderDtos.Order get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    public ResponseEntity<OrderDtos.Order> create(@Valid @RequestBody OrderDtos.Create request) {
        var result = service.create(request);
        return ResponseEntity.created(URI.create("/api/v1/orders/" + result.id())).body(result);
    }

    @PutMapping("/{id}")
    public OrderDtos.Order update(
            @PathVariable long id, @Valid @RequestBody OrderDtos.Create request) {
        return service.update(id, request);
    }

    @PutMapping("/{id}/lines/{productId}")
    public OrderDtos.Order setLine(
            @PathVariable long id,
            @PathVariable long productId,
            @Valid @RequestBody OrderDtos.SetLine request) {
        return service.setLine(id, productId, request);
    }

    @DeleteMapping("/{id}/lines/{productId}")
    public OrderDtos.Order removeLine(@PathVariable long id, @PathVariable long productId) {
        return service.removeLine(id, productId);
    }

    @PostMapping("/{id}/confirm")
    public OrderDtos.Order confirm(@PathVariable long id) {
        return service.confirm(id);
    }

    @PostMapping("/{id}/cancel")
    public OrderDtos.Order cancel(@PathVariable long id) {
        return service.cancel(id);
    }

    @PostMapping("/{id}/complete")
    public OrderDtos.Order complete(@PathVariable long id) {
        return completion.complete(id);
    }
}
