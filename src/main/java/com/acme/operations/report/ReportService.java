package com.acme.operations.report;

import com.acme.operations.common.BusinessException;
import com.acme.operations.common.PageResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class ReportService {
    private final ReportRepository repository;

    public ReportService(ReportRepository repository) {
        this.repository = repository;
    }

    public PageResponse<ReportDtos.CustomerSales> sales(
            Instant from, Instant to, int page, int size) {
        if (from != null && to != null && !from.isBefore(to))
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "from must be earlier than to");
        return repository.sales(from, to, page, size);
    }

    public PageResponse<ReportDtos.LowStock> lowStock(long threshold, int page, int size) {
        return repository.lowStock(threshold, page, size);
    }
}
