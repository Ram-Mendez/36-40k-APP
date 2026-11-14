package com.acme.operations.common;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Set;

// Spring handles requests to these routes and serializes the response as JSON.
@Tag(name = "Audit")
@RestController
// Each method path is relative to this base path.
@RequestMapping("/api/v1/audit")
// Spring Security checks for the ADMIN role before running this operation.
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {
    // JdbcTemplate executes the SQL and maps the result rows.
    private final JdbcTemplate jdbc;

    // Spring injects the dependencies through the single constructor.
    public AuditController(JdbcTemplate jdbc) {
        // Keep the injected dependency for later calls.
        this.jdbc = jdbc;
    }

    // DTO from the history: who did what, on which resource and on which request.
    public record AuditView(
            // Unique ID of this record.
            long id,
            // Identity that executed the audited action.
            String actor,
            // Code of action that was registered.
            String action,
            // Type of resource concerned, e.g. ORDER or USER.
            String entityType,
            // Identifier of the resource concerned, stored as text.
            String entityId,
            // Description of the action to follow the history.
            String details,
            // Identifier that connects this event to the request logs.
            String requestId,
            // Record creation time.
            OffsetDateTime createdAt) {}

    // Handle GET requests to this route.
    @Operation(summary = "List audit events")
    @GetMapping
    // Read one page of results using the supplied filters.
    public PageResponse<AuditView> list(
            // Read the zero-based page number, using the default if omitted.
            @RequestParam(defaultValue = "0") int page,
            // Validate the requested number of results per page.
            @RequestParam(defaultValue = "20") int size,
            // An omitted query parameter leaves this filter unset.
            @RequestParam(required = false) String entityType,
            // An omitted query parameter leaves this filter unset.
            @RequestParam(required = false) String entityId,
            // An omitted query parameter leaves this filter unset.
            @RequestParam(required = false) String requestId) {
        // It reuses the validation of limits; this JDBC query builds its own pagination.
        PageRequests.of(page, size, "id", "desc", Set.of("id"));
        // It starts an always true filter to be able to add optional AND conditions.
        StringBuilder filter = new StringBuilder(" WHERE true");
        // Bind the values to the SQL placeholders.
        var args = new ArrayList<Object>();
        // Filter by type of appeal only if the request so indicates.
        if (entityType != null) {
            filter.append(" AND entity_type=?");
            args.add(entityType);
        }
        // It allows to locate the actions on a particular id.
        if (entityId != null) {
            filter.append(" AND entity_id=?");
            args.add(entityId);
        }
        // It allows to follow all actions of the same request.
        if (requestId != null) {
            filter.append(" AND request_id=?");
            args.add(requestId);
        }
        // It counts coincidences before adding pagation parameters.
        long total =
                jdbc.queryForObject(
                        "SELECT count(*) FROM audit_event" + filter, Long.class, args.toArray());
        // LIMIT receives size and OFSET page * size; the first page is zero.
        args.add(size);
        args.add((long) page * size);
        // Check out the latest events and transform each row into AuditView.
        var rows =
                jdbc.query(
                        "SELECT * FROM audit_event" + filter + " ORDER BY id DESC LIMIT ? OFFSET ?",
                        // The mapper reads columns per name to separate the SQL from the response DTO.
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
        // Returns the history and total number of pages.
        return new PageResponse<>(rows, page, size, total, (int) ((total + size - 1) / size));
    }
}
