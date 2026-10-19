package com.acme.operations.common;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {
    private final JdbcTemplate jdbc;

    public AuditController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record AuditView(
            long id,
            String actor,
            String action,
            String entityType,
            String entityId,
            String details,
            String requestId,
            OffsetDateTime createdAt) {}

    @GetMapping
    public PageResponse<AuditView> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(required = false) String requestId) {
        PageRequests.of(page, size, "id", "desc", Set.of("id"));
        StringBuilder filter = new StringBuilder(" WHERE true");
        var args = new ArrayList<Object>();
        if (entityType != null) {
            filter.append(" AND entity_type=?");
            args.add(entityType);
        }
        if (entityId != null) {
            filter.append(" AND entity_id=?");
            args.add(entityId);
        }
        if (requestId != null) {
            filter.append(" AND request_id=?");
            args.add(requestId);
        }
        long total =
                jdbc.queryForObject(
                        "SELECT count(*) FROM audit_event" + filter, Long.class, args.toArray());
        args.add(size);
        args.add((long) page * size);
        var rows =
                jdbc.query(
                        "SELECT * FROM audit_event" + filter + " ORDER BY id DESC LIMIT ? OFFSET ?",
                        (rs, n) ->
                                new AuditView(
                                        rs.getLong("id"),
                                        rs.getString("actor"),
                                        rs.getString("action"),
                                        rs.getString("entity_type"),
                                        rs.getString("entity_id"),
                                        rs.getString("details"),
                                        rs.getString("request_id"),
                                        rs.getObject("created_at", OffsetDateTime.class)),
                        args.toArray());
        return new PageResponse<>(rows, page, size, total, (int) ((total + size - 1) / size));
    }
}
