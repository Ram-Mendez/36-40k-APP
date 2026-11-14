package com.acme.operations.report;

import com.acme.operations.common.BusinessException;
import com.acme.operations.common.PageResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

// Spring registers this class as a service. Its methods implement the business rules.
@Service
// In the readings, REPEATABLE_READ keeps the count and rows of the report consistent.
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ReportService {
    // The repository handles persistence for this service.
    private final ReportRepository repository;

    // Spring injects the dependencies through the single constructor.
    public ReportService(ReportRepository repository) {
        // Keep the injected dependency for later calls.
        this.repository = repository;
    }

    // Valid the interval before consulting sales completed by customer.
    public PageResponse<ReportDtos.CustomerSales> sales(
            Instant from, Instant to, int page, int size) {
        // If both dates arrive, from must be strictly prior to.
        if (from != null && to != null && !from.isBefore(to))
            // It rejects with 400 an empty or inverted interval.
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "from must be earlier than to");
        // Delegate the SQL calculation and return your results page.
        return repository.sales(from, to, page, size);
    }

    // List products available below or equal to the threshold.
    public PageResponse<ReportDtos.LowStock> lowStock(long threshold, int page, int size) {
        // The repository calculates less reserved physical and applies the pagation.
        return repository.lowStock(threshold, page, size);
    }
}
