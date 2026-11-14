package com.acme.operations.order;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.acme.operations.common.PageResponse;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

// Spring handles requests to these routes and serializes the response as JSON.
@Tag(name = "Sales orders")
@RestController
// Each method path is relative to this base path.
@RequestMapping("/api/v1/orders")
// Validate the constraints on method parameters.
@Validated
// Only ADMIN and OPERATIONS can perform this operation.
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS')")
public class OrderController {
    // The controller delegates business operations to this service.
    private final OrderService service;
    // Coordinates the HTTP call to shipping and then transactional termination.
    private final OrderCompletionService completion;

    // Spring injects the dependencies through the single constructor.
    public OrderController(OrderService service, OrderCompletionService completion) {
        // Keep the injected dependency for later calls.
        this.service = service;
        this.completion = completion;
    }

    // Handle GET requests to this route.
    @Operation(summary = "List customer orders")
    @GetMapping
    // ADMIN, OPERATIONS and READONLY can read this resource.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','READONLY')")
    // Search for orders with filters and order; return content and page metadata.
    public PageResponse<OrderDtos.Order> search(
            // An omitted query parameter leaves this filter unset.
            @RequestParam(required = false) Long customerId,
            // An omitted query parameter leaves this filter unset.
            @Parameter(description = "Filter by status.", schema = @Schema(allowableValues = {
                    "DRAFT", "CONFIRMED", "COMPLETED", "CANCELLED"}))
            @RequestParam(required = false) String status,
            // Read the zero-based page number, using the default if omitted.
            @RequestParam(defaultValue = "0") @Min(0) int page,
            // Validate the requested number of results per page.
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            // Use the default value when this query parameter is omitted.
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        // Search for orders with filters and order; return content and page metadata.
        return service.search(customerId, status, page, size, sort);
    }

    // Handle GET requests to this route.
    @Operation(summary = "View an order and its lines")
    @GetMapping("/{id}")
    // Look up the resource by ID. The service reports an error if it is missing.
    @PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','READONLY')")
    public OrderDtos.Order get(@PathVariable long id) {
        // Look up the resource by ID. The service reports an error if it is missing.
        return service.get(id);
    }

    // Handle POST requests and delegate the operation to the service.
    @Operation(summary = "Create a draft sales order")
    @PostMapping
    // Spring reads and validates the JSON. The service creates the record and returns its generated fields.
    public ResponseEntity<OrderDtos.Order> create(@Valid @RequestBody OrderDtos.Create request) {
        // The service creates the record and returns its generated fields.
        var result = service.create(request);
        // Return HTTP 201 with the resource URL in Location and its representation in the JSON body.
        return ResponseEntity.created(URI.create("/api/v1/orders/" + result.id())).body(result);
    }

    // Handle PUT requests to this route.
    @Operation(summary = "Change the customer on a draft order")
    @PutMapping("/{id}")
    // The service validates the changes before saving them.
    public OrderDtos.Order update(
            // Spring reads the ID from the URL and any request body from JSON.
            @PathVariable long id, @Valid @RequestBody OrderDtos.Create request) {
        // The service validates the changes before saving them.
        return service.update(id, request);
    }

    // Handle PUT requests to this route.
    @Operation(summary = "Set the quantity ordered for a product")
    @PutMapping("/{id}/lines/{productId}")
    // Set the amount of a product on order; rules are checked in service.
    public OrderDtos.Order setLine(
            // Spring reads the ID from the URL and any request body from JSON.
            @PathVariable long id,
            // Spring reads the ID from the URL and any request body from JSON.
            @PathVariable long productId,
            // Spring converts the JSON to the DTO and validates its restrictions before running the method.
            @Valid @RequestBody OrderDtos.SetLine request) {
        // Set the amount of a product on order; rules are checked in service.
        return service.setLine(id, productId, request);
    }

    // Handle a DELETE request to remove an item or deactivate the resource.
    @Operation(summary = "Remove a product from the order")
    @DeleteMapping("/{id}/lines/{productId}")
    // Remove that product from the order and recalculate its total in the service. The id takes it Spring from the
    // route.
    public OrderDtos.Order removeLine(@PathVariable long id, @PathVariable long productId) {
        // Remove that product from the order and emphasize its total in the service.
        return service.removeLine(id, productId);
    }

    // Handle POST requests and delegate the operation to the service.
    @Operation(summary = "Confirm the order and reserve stock",
            description = "Reserve the requested units for this customer order.")
    @PostMapping("/{id}/confirm")
    // Confirm the order and book your products within the service transaction. The id takes Spring from the route.
    public OrderDtos.Order confirm(@PathVariable long id) {
        // Confirm the order and book your products within the service transaction.
        return service.confirm(id);
    }

    // Handle POST requests and delegate the operation to the service.
    @Operation(summary = "Cancel the order and release its reservations")
    @PostMapping("/{id}/cancel")
    // Cancel the order and release reservations when appropriate. The id takes Spring from the route.
    public OrderDtos.Order cancel(@PathVariable long id) {
        // Cancel the order and release reservations when appropriate.
        return service.cancel(id);
    }

    // Handle POST requests and delegate the operation to the service.
    @Operation(summary = "Complete the sale and record the shipment",
            description = "Obtain a shipping quote and record the outbound stock movement.")
    @PostMapping("/{id}/complete")
    // First you get shipping and then complete the order with your status and version controls. The id takes
    // Spring from the route.
    public OrderDtos.Order complete(@PathVariable long id) {
        // First you get shipping and then complete the order with your status and version controls.
        return completion.complete(id);
    }
}
