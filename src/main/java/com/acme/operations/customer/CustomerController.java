package com.acme.operations.customer;

import com.acme.operations.common.PageResponse;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/customers")
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','READONLY')")
public class CustomerController {
    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<CustomerDtos.View> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active) {
        return service.list(page, size, sort, direction, search, active);
    }

    @GetMapping("/{id}")
    public CustomerDtos.View get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ResponseEntity<CustomerDtos.View> create(@Valid @RequestBody CustomerDtos.Input input) {
        var result = service.create(input);
        return ResponseEntity.created(URI.create("/api/v1/customers/" + result.id())).body(result);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public CustomerDtos.View update(
            @PathVariable long id, @Valid @RequestBody CustomerDtos.Input input) {
        return service.update(id, input);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ResponseEntity<Void> deactivate(@PathVariable long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
