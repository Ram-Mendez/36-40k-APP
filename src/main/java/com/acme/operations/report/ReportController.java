package com.acme.operations.report;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.acme.operations.common.PageResponse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

// Spring handles requests to these routes and serializes the response as JSON.
@Tag(name = "Reports")
@RestController
// Each method path is relative to this base path.
@RequestMapping("/api/v1/reports")
// Validate the constraints on method parameters.
@Validated
// ADMIN, OPERATIONS and READONLY can read this resource.
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','READONLY')")
public class ReportController {
    // The controller delegates business operations to this service.
    private final ReportService service;

    // Spring injects the dependencies through the single constructor.
    public ReportController(ReportService service) {
        // Keep the injected dependency for later calls.
        this.service = service;
    }

    // Handle GET requests to this route.
    @Operation(summary = "View sales by customer")
    @GetMapping("/sales")
    // Check sales completed by customer within the specified interval.
    public PageResponse<ReportDtos.CustomerSales> sales(
            // An omitted query parameter leaves this filter unset.
            @RequestParam(required = false) Instant from,
            // An omitted query parameter leaves this filter unset.
            @RequestParam(required = false) Instant to,
            // Read the zero-based page number, using the default if omitted.
            @RequestParam(defaultValue = "0") @Min(0) int page,
            // Validate the requested number of results per page.
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        // Check sales completed by customer within the specified interval.
        return service.sales(from, to, page, size);
    }

    // Handle GET requests to this route.
    @Operation(summary = "View low-stock products and their confirmed order counts")
    @GetMapping("/low-stock")
    // Check products available to meet the indicated threshold.
    public PageResponse<ReportDtos.LowStock> lowStock(
            // Use the default value when this query parameter is omitted.
            @RequestParam(defaultValue = "10") @Min(0) long threshold,
            // Read the zero-based page number, using the default if omitted.
            @RequestParam(defaultValue = "0") @Min(0) int page,
            // Validate the requested number of results per page.
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        // Check products available to meet the indicated threshold.
        return service.lowStock(threshold, page, size);
    }
}
