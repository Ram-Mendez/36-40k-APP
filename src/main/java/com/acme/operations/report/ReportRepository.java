package com.acme.operations.report;

import com.acme.operations.common.PageResponse;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// Spring registers this repository. Its methods execute SQL and map the results.
@Repository
public class ReportRepository {
    // JdbcTemplate executes the SQL and maps the result rows.
    private final JdbcTemplate jdbc;

    // Spring injects the dependencies through the single constructor.
    public ReportRepository(JdbcTemplate jdbc) {
        // Keep the injected dependency for later calls.
        this.jdbc = jdbc;
    }

    // Aggregate sales for each customer.
    public PageResponse<ReportDtos.CustomerSales> sales(
            Instant from, Instant to, int page, int size) {
        // Select the states that are taken into account for the sales query.
        StringBuilder filter = new StringBuilder(" WHERE o.status IN ('COMPLETED')");
        // The parameters are accumulated in the order of the filter?.
        List<Object> args = new ArrayList<>();
        // The initial limit is included by the "h= and is converted to timestamp SQL."
        if (from != null) {
            filter.append(" AND o.updated_at>=?");
            args.add(Timestamp.from(from));
        }
        // The final limit is excluded by means of the following, avoiding counting two adjoining dates.
        if (to != null) {
            filter.append(" AND o.updated_at<?");
            args.add(Timestamp.from(to));
        }
        // It counts different customers, as each row of the report represents a customer.
        long total =
                jdbc.queryForObject(
                        "SELECT count(DISTINCT o.customer_id) FROM sales_order o" + filter,
                        Long.class,
                        args.toArray());
        // Add size and displacement after possible date parameters.
        args.add(size);
        args.add((long) page * size);
        // Group by customer, sum sales and shipping totals, then sort by sales and ID.
        var rows =
                jdbc.query(
                        "SELECT"
                            + " c.id,c.name,count(o.id),sum(o.total),sum(coalesce(o.shipping_cost,0))"
                            + " FROM customer c JOIN sales_order o ON o.customer_id=c.id"
                                + filter
                                + " GROUP BY c.id,c.name ORDER BY sum(o.total) DESC,c.id LIMIT ?"
                                + " OFFSET ?",
                        // Convert SQL aggregates into a sales DTO per customer.
                        (rs, n) ->
                                new ReportDtos.CustomerSales(
                                        rs.getLong(1),
                                        rs.getString(2),
                                        rs.getLong(3),
                                        rs.getBigDecimal(4),
                                        rs.getBigDecimal(5)),
                        args.toArray());
        // Returns the aggregates and the page totals.
        return new PageResponse<>(rows, page, size, total, (int) ((total + size - 1) / size));
    }

    // Search for active products with few units available for new reservations.
    public PageResponse<ReportDtos.LowStock> lowStock(long threshold, int page, int size) {
        // LEFT JOIN includes products without an inventory row. COALESCE gives those products a zero balance.
        String from =
                " FROM product p LEFT JOIN inventory_balance i ON i.product_id=p.id WHERE"
                    + " p.active=true AND coalesce(i.on_hand,0)-coalesce(i.reserved,0)<=?";
        // The same filter counts products available below or equal to the threshold.
        long total = jdbc.queryForObject("SELECT count(*)" + from, Long.class, threshold);
        // Read each product’s balance and count its reservations.
        var rows =
                jdbc.query(
                        "SELECT"
                            + " p.id,p.sku,p.name,coalesce(i.on_hand,0),coalesce(i.reserved,0),(SELECT"
                            + " count(DISTINCT r.order_id) FROM stock_reservation r WHERE r.product_id=p.id)"
                                + from
                                + " ORDER BY coalesce(i.on_hand,0)-coalesce(i.reserved,0),p.id"
                                + " LIMIT ? OFFSET ?",
                        // Build the DTO including available = on_hand - reserved.
                        (rs, n) ->
                                new ReportDtos.LowStock(
                                        rs.getLong(1),
                                        rs.getString(2),
                                        rs.getString(3),
                                        rs.getLong(4),
                                        rs.getLong(5),
                                        rs.getLong(4) - rs.getLong(5),
                                        // This count comes from stock_reservation; it is not a sum of units.
                                        rs.getLong(6)),
                        // They receive threshold, size and page * size, in that order.
                        threshold,
                        size,
                        (long) page * size);
        // Deliver the page without changing any balance.
        return new PageResponse<>(rows, page, size, total, (int) ((total + size - 1) / size));
    }
}
