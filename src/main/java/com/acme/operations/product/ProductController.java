package com.acme.operations.product;

import com.acme.operations.common.PageResponse;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/products")
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','READONLY')")
public class ProductController {
    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ProductDtos.View> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean active) {
        return service.list(page, size, sort, direction, search, active);
    }

    @GetMapping("/{id}")
    public ProductDtos.View get(@PathVariable long id) {
        return service.get(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ResponseEntity<ProductDtos.View> create(@Valid @RequestBody ProductDtos.Input input) {
        var result = service.create(input);
        return ResponseEntity.created(URI.create("/api/v1/products/" + result.id())).body(result);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ProductDtos.View update(
            @PathVariable long id, @Valid @RequestBody ProductDtos.Input input) {
        return service.update(id, input);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    public ResponseEntity<Void> deactivate(@PathVariable long id) {
        service.deactivate(id);
        return ResponseEntity.noContent().build();
    }
}
