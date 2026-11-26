package com.acme.operations.warehouse;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("dev & !turno-cero")
class InspectionHistoryService {
    private final JdbcTemplate jdbc;

    InspectionHistoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRejectedInspection(String code) {
        jdbc.update("""
                INSERT INTO transfer_practice.inspection_attempt(transfer_code, result)
                VALUES (?, 'REJECTED')
                """, code);
    }
}