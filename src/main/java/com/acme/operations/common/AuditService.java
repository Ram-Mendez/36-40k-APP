package com.acme.operations.common;

import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;

    public AuditService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void record(String action, String entityType, String entityId, String details) {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        String actor = authentication == null ? "system" : authentication.getName();
        recordAs(actor, action, entityType, entityId, details);
    }

    @Transactional
    public void recordAs(
            String actor, String action, String entityType, String entityId, String details) {
        jdbc.update(
                "INSERT INTO audit_event(actor,action,entity_type,entity_id,details,request_id)"
                    + " VALUES (?,?,?,?,?,?)",
                actor,
                action,
                entityType,
                entityId,
                details,
                MDC.get("requestId"));
    }
}
