package com.acme.operations.report;

import com.acme.operations.common.PageResponse;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Repository
public class ReportRepository {
    private final JdbcTemplate jdbc;

    public ReportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public PageResponse<ReportDtos.CustomerSales> sales(
            Instant from, Instant to, int page, int size) {
        StringBuilder filter = new StringBuilder(" WHERE o.status='COMPLETED'");
        List<Object> args = new ArrayList<>();
        if (from != null) {
            filter.append(" AND o.updated_at>=?");
            args.add(Timestamp.from(from));
        }
        if (to != null) {
            filter.append(" AND o.updated_at<?");
            args.add(Timestamp.from(to));
        }
        long total =
                jdbc.queryForObject(
                        "SELECT count(DISTINCT o.customer_id) FROM sales_order o" + filter,
                        Long.class,
                        args.toArray());
        args.add(size);
        args.add((long) page * size);
        var rows =
                jdbc.query(
                        "SELECT"
                            + " c.id,c.name,count(o.id),sum(o.total),sum(coalesce(o.shipping_cost,0))"
                            + " FROM customer c JOIN sales_order o ON o.customer_id=c.id"
                                + filter
                                + " GROUP BY c.id,c.name ORDER BY sum(o.total) DESC,c.id LIMIT ?"
                                + " OFFSET ?",
                        (rs, n) ->
                                new ReportDtos.CustomerSales(
                                        rs.getLong(1),
                                        rs.getString(2),
                                        rs.getLong(3),
                                        rs.getBigDecimal(4),
                                        rs.getBigDecimal(5)),
                        args.toArray());
        return new PageResponse<>(rows, page, size, total, (int) ((total + size - 1) / size));
    }

    public PageResponse<ReportDtos.LowStock> lowStock(long threshold, int page, int size) {
        String from =
                " FROM product p LEFT JOIN inventory_balance i ON i.product_id=p.id WHERE"
                    + " p.active=true AND coalesce(i.on_hand,0)-coalesce(i.reserved,0)<=?";
        long total = jdbc.queryForObject("SELECT count(*)" + from, Long.class, threshold);
        var rows =
                jdbc.query(
                        "SELECT"
                            + " p.id,p.sku,p.name,coalesce(i.on_hand,0),coalesce(i.reserved,0),(SELECT"
                            + " count(*) FROM stock_reservation r WHERE r.product_id=p.id)"
                                + from
                                + " ORDER BY coalesce(i.on_hand,0)-coalesce(i.reserved,0),p.id"
                                + " LIMIT ? OFFSET ?",
                        (rs, n) ->
                                new ReportDtos.LowStock(
                                        rs.getLong(1),
                                        rs.getString(2),
                                        rs.getString(3),
                                        rs.getLong(4),
                                        rs.getLong(5),
                                        rs.getLong(4) - rs.getLong(5),
                                        rs.getLong(6)),
                        threshold,
                        size,
                        (long) page * size);
        return new PageResponse<>(rows, page, size, total, (int) ((total + size - 1) / size));
    }
}
