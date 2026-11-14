package com.acme.operations.common;

import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Spring registers this class as a service. Its methods implement the business rules.
@Service
public class AuditService {
    // JdbcTemplate executes the SQL and maps the result rows.
    private final JdbcTemplate jdbc;

    // Spring injects the dependencies through the single constructor.
    public AuditService(JdbcTemplate jdbc) {
        // Keep the injected dependency for later calls.
        this.jdbc = jdbc;
    }

    // Spring starts or joins a transaction when the call goes through the proxy. It commits on success and rolls
    // back on unchecked exceptions.
    @Transactional
    // It records an action using the identity of the current request.
    public void record(String action, String entityType, String entityId, String details) {
        // See the context of Spring Security; it does not receive the user's name from the customer.
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        // If there is no authentication use system, for example in a boot process.
        String actor = authentication == null ? "system" : authentication.getName();
        // Internal call: use the already open record transaction, without going through the proxy again.
        recordAs(actor, action, entityType, entityId, details);
    }

    // Spring starts or joins a transaction when the call goes through the proxy. It commits on success and rolls
    // back on unchecked exceptions.
    @Transactional
    // It allows to indicate the actor explicitly, as in login before authenticating the request.
    public void recordAs(
            String actor, String action, String entityType, String entityId, String details) {
        // Save the action, resource, details and requestId in the same transaction as the caller.
        jdbc.update(
                "INSERT INTO audit_event(actor,action,entity_type,entity_id,details,request_id)"
                    + " VALUES (?,?,?,?,?,?)",
                actor,
                action,
                entityType,
                entityId,
                details,
                // The MDC provides the correlation with logs; it can be null out of an HTTP request.
                MDC.get("requestId"));
    }
}
