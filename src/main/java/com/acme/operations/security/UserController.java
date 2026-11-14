package com.acme.operations.security;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.acme.operations.common.PageResponse;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

// Spring handles requests to these routes and serializes the response as JSON.
@Tag(name = "Users")
@RestController
// Each method path is relative to this base path.
@RequestMapping("/api/v1/users")
public class UserController {
    // The controller delegates business operations to this service.
    private final UserService service;

    // Spring injects the dependencies through the single constructor.
    public UserController(UserService service) {
        // Keep the injected dependency for later calls.
        this.service = service;
    }

    // Handle GET requests to this route.
    @Operation(summary = "List users")
    @GetMapping
    // Read one page of results using the supplied filters.
    public PageResponse<UserDtos.View> list(
            // Read the zero-based page number, using the default if omitted.
            @RequestParam(defaultValue = "0") int page,
            // Validate the requested number of results per page.
            @RequestParam(defaultValue = "20") int size,
            // An omitted query parameter leaves this filter unset.
            @RequestParam(required = false) String query,
            // An omitted query parameter leaves this filter unset.
            @RequestParam(required = false) Boolean active) {
        // Read one page of results using the supplied filters.
        return service.list(page, size, query, active);
    }

    // Handle POST requests and delegate the operation to the service.
    @Operation(summary = "Create a user")
    @PostMapping
    // If it ends correctly, Spring responds 201 indicating that the resource was created.
    @ResponseStatus(HttpStatus.CREATED)
    // Spring reads and validates the JSON. The service creates the record and returns its generated fields.
    public UserDtos.View create(@Valid @RequestBody UserDtos.Create request) {
        // The service creates the record and returns its generated fields.
        return service.create(request);
    }

    // Handle PUT requests to this route.
    @Operation(summary = "Update user access")
    @PutMapping("/{id}")
    // The service validates the changes before saving them.
    public UserDtos.View update(
            // Spring reads the ID from the URL and any request body from JSON.
            @PathVariable long id, @Valid @RequestBody UserDtos.Update request) {
        // The service validates the changes before saving them.
        return service.update(id, request);
    }

    // Handle a DELETE request to remove an item or deactivate the resource.
    @Operation(summary = "Deactivate a user")
    @DeleteMapping("/{id}")
    // Return HTTP 204 with an empty body on success.
    @ResponseStatus(HttpStatus.NO_CONTENT)
    // Deactivate the resource and preserve its history.
    public void deactivate(@PathVariable long id) {
        // Deactivate the resource and preserve its history.
        service.deactivate(id);
    }
}
