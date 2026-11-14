package com.acme.operations.customer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.acme.operations.common.PageResponse;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

// Spring handles requests to these routes and serializes the response as JSON.
@Tag(name = "Customers")
@RestController
// Each method path is relative to this base path.
@RequestMapping("/api/v1/customers")
// ADMIN, OPERATIONS and READONLY can read this resource.
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','READONLY')")
public class CustomerController {
    // The controller delegates business operations to this service.
    private final CustomerService service;

    // Spring injects the dependencies through the single constructor.
    public CustomerController(CustomerService service) {
        // Keep the injected dependency for later calls.
        this.service = service;
    }

    // Handle GET requests to this route.
    @Operation(summary = "List customers")
    @GetMapping
    // Read one page of results using the supplied filters.
    public PageResponse<CustomerDtos.View> list(
            // Read the zero-based page number, using the default if omitted.
            @RequestParam(defaultValue = "0") int page,
            // Validate the requested number of results per page.
            @RequestParam(defaultValue = "20") int size,
            // Use the default value when this query parameter is omitted.
            @RequestParam(defaultValue = "name") String sort,
            // Use the default value when this query parameter is omitted.
            @RequestParam(defaultValue = "asc") String direction,
            // An omitted query parameter leaves this filter unset.
            @RequestParam(required = false) String search,
            // An omitted query parameter leaves this filter unset.
            @RequestParam(required = false) Boolean active) {
        // Read one page of results using the supplied filters.
        return service.list(page, size, sort, direction, search, active);
    }

    // Handle GET requests to this route.
    @Operation(summary = "View a customer")
    @GetMapping("/{id}")
    // Look up the resource by ID. The service reports an error if it is missing.
    public CustomerDtos.View get(@PathVariable long id) {
        // Look up the resource by ID. The service reports an error if it is missing.
        return service.get(id);
    }

    // Handle POST requests and delegate the operation to the service.
    @Operation(summary = "Create a customer")
    @PostMapping
    // Only ADMIN and OPERATIONS can perform this operation.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Spring reads and validates the JSON. The service creates the record and returns its generated fields.
    public ResponseEntity<CustomerDtos.View> create(@Valid @RequestBody CustomerDtos.Input input) {
        // The service creates the record and returns its generated fields.
        var result = service.create(input);
        // Return HTTP 201 with the resource URL in Location and its representation in the JSON body.
        return ResponseEntity.created(URI.create("/api/v1/customers/" + result.id())).body(result);
    }

    // Handle PUT requests to this route.
    @Operation(summary = "Update a customer")
    @PutMapping("/{id}")
    // Only ADMIN and OPERATIONS can perform this operation.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // The service validates the changes before saving them.
    public CustomerDtos.View update(
            // Spring reads the ID from the URL and any request body from JSON.
            @PathVariable long id, @Valid @RequestBody CustomerDtos.Input input) {
        // The service validates the changes before saving them.
        return service.update(id, input);
    }

    // Handle a DELETE request to remove an item or deactivate the resource.
    @Operation(summary = "Deactivate a customer")
    @DeleteMapping("/{id}")
    // Only ADMIN and OPERATIONS can perform this operation.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
    // Deactivate the resource and preserve its history.
    public ResponseEntity<Void> deactivate(@PathVariable long id) {
        // Deactivate the resource and preserve its history.
        service.deactivate(id);
        // Return HTTP 204 with an empty body on success.
        return ResponseEntity.noContent().build();
    }
}
