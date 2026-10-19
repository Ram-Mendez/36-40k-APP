package com.acme.operations.report;

import com.acme.operations.common.PageResponse;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/reports")
@Validated
@PreAuthorize("hasAnyRole('ADMIN','OPERATIONS','READONLY')")
public class ReportController {
    private final ReportService service;

    public ReportController(ReportService service) {
        this.service = service;
    }

    @GetMapping("/sales")
    public PageResponse<ReportDtos.CustomerSales> sales(
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.sales(from, to, page, size);
    }

    @GetMapping("/low-stock")
    public PageResponse<ReportDtos.LowStock> lowStock(
            @RequestParam(defaultValue = "10") @Min(0) long threshold,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return service.lowStock(threshold, page, size);
    }
}
