package com.acme.operations.order;

import com.acme.operations.common.BusinessException;
import com.acme.operations.common.PageResponse;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Repository
public class OrderRepository {
    private final JdbcTemplate jdbc;

    public OrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Header(
            long id,
            long customerId,
            String customerName,
            String status,
            BigDecimal total,
            long version,
            String shippingReference,
            BigDecimal shippingCost,
            Instant createdAt,
            Instant updatedAt) {}

    public Header header(long id, boolean lock) {
        var result =
                jdbc.query(
                        "SELECT o.*,c.name AS customer_name FROM sales_order o JOIN customer c ON"
                            + " c.id=o.customer_id WHERE o.id=?"
                                + (lock ? " FOR UPDATE OF o" : ""),
                        (rs, n) ->
                                new Header(
                                        rs.getLong("id"),
                                        rs.getLong("customer_id"),
                                        rs.getString("customer_name"),
                                        rs.getString("status"),
                                        rs.getBigDecimal("total"),
                                        rs.getLong("version"),
                                        rs.getString("shipping_reference"),
                                        rs.getBigDecimal("shipping_cost"),
                                        rs.getTimestamp("created_at").toInstant(),
                                        rs.getTimestamp("updated_at").toInstant()),
                        id);
        if (result.isEmpty())
            throw new BusinessException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found");
        return result.getFirst();
    }

    public List<OrderDtos.Line> lines(long id) {
        return jdbc.query(
                "SELECT l.*,p.sku,p.name FROM order_line l JOIN product p ON p.id=l.product_id"
                    + " WHERE l.order_id=? ORDER BY l.product_id",
                (rs, n) ->
                        new OrderDtos.Line(
                                rs.getLong("id"),
                                rs.getLong("product_id"),
                                rs.getString("sku"),
                                rs.getString("name"),
                                rs.getLong("quantity"),
                                rs.getBigDecimal("unit_price"),
                                rs.getBigDecimal("unit_price")
                                        .multiply(BigDecimal.valueOf(rs.getLong("quantity")))),
                id);
    }

    public OrderDtos.Order get(long id) {
        return view(header(id, false), lines(id));
    }

    private OrderDtos.Order view(Header h, List<OrderDtos.Line> lines) {
        return new OrderDtos.Order(
                h.id(),
                h.customerId(),
                h.customerName(),
                h.status(),
                h.total(),
                h.version(),
                h.shippingReference(),
                h.shippingCost(),
                h.createdAt(),
                h.updatedAt(),
                lines);
    }

    public void requireActiveCustomer(long id) {
        var matches =
                jdbc.queryForList(
                        "SELECT active FROM customer WHERE id=? FOR SHARE", Boolean.class, id);
        if (matches.isEmpty())
            throw new BusinessException(
                    HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", "Customer not found");
        if (!matches.getFirst())
            throw new BusinessException(
                    HttpStatus.CONFLICT, "CUSTOMER_INACTIVE", "Customer is inactive");
    }

    public BigDecimal activePrice(long productId) {
        var matches =
                jdbc.queryForList(
                        "SELECT unit_price FROM product WHERE id=? AND active=true FOR SHARE",
                        BigDecimal.class,
                        productId);
        if (matches.isEmpty())
            throw new BusinessException(
                    HttpStatus.CONFLICT, "PRODUCT_UNAVAILABLE", "Product is missing or inactive");
        return matches.getFirst();
    }

    public long create(long customerId) {
        return jdbc.queryForObject(
                "INSERT INTO sales_order(customer_id) VALUES (?) RETURNING id",
                Long.class,
                customerId);
    }

    public void customer(long id, long customerId) {
        jdbc.update(
                "UPDATE sales_order SET customer_id=?,version=version+1,updated_at=now() WHERE"
                    + " id=?",
                customerId,
                id);
    }

    public void setLine(long id, long productId, long quantity, BigDecimal currentPrice) {
        // Existing lines retain their agreed price; only newly added products use the current
        // catalogue price.
        jdbc.update(
                "INSERT INTO order_line(order_id,product_id,quantity,unit_price) VALUES (?,?,?,?)"
                    + " ON CONFLICT(order_id,product_id) DO UPDATE SET quantity=excluded.quantity",
                id,
                productId,
                quantity,
                currentPrice);
        recalculate(id);
    }

    public void removeLine(long id, long productId) {
        if (jdbc.update("DELETE FROM order_line WHERE order_id=? AND product_id=?", id, productId)
                == 0)
            throw new BusinessException(
                    HttpStatus.NOT_FOUND, "ORDER_LINE_NOT_FOUND", "Order line not found");
        recalculate(id);
    }

    private void recalculate(long id) {
        BigDecimal amount =
                jdbc.queryForObject(
                        "SELECT coalesce(sum(quantity*unit_price),0) FROM order_line WHERE"
                            + " order_id=?",
                        BigDecimal.class,
                        id);
        jdbc.update(
                "UPDATE sales_order SET total=?,version=version+1,updated_at=now() WHERE id=?",
                amount,
                id);
    }

    public void transition(long id, String status, String reference, BigDecimal cost) {
        jdbc.update(
                "UPDATE sales_order SET"
                    + " status=?,shipping_reference=?,shipping_cost=?,version=version+1,updated_at=now()"
                    + " WHERE id=?",
                status,
                reference,
                cost,
                id);
    }

    public PageResponse<OrderDtos.Order> search(
            Long customerId, String status, int page, int size, String sort) {
        String orderBy =
                switch (sort) {
                    case "createdAt,asc" -> "o.created_at ASC,o.id ASC";
                    case "total,asc" -> "o.total ASC,o.id ASC";
                    case "total,desc" -> "o.total DESC,o.id DESC";
                    case "createdAt,desc" -> "o.created_at DESC,o.id DESC";
                    default ->
                            throw new BusinessException(
                                    HttpStatus.BAD_REQUEST,
                                    "INVALID_SORT",
                                    "Allowed sorts: createdAt,asc; createdAt,desc; total,asc;"
                                        + " total,desc");
                };
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        List<Object> args = new ArrayList<>();
        if (customerId != null) {
            where.append(" AND o.customer_id=?");
            args.add(customerId);
        }
        if (status != null) {
            if (!List.of("DRAFT", "CONFIRMED", "COMPLETED", "CANCELLED").contains(status))
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Unknown order status");
            where.append(" AND o.status=?");
            args.add(status);
        }
        long count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM sales_order o" + where, Long.class, args.toArray());
        args.add(size);
        args.add((long) page * size);
        var headers =
                jdbc.query(
                        "SELECT o.*,c.name AS customer_name FROM sales_order o JOIN customer c ON"
                            + " c.id=o.customer_id"
                                + where
                                + " ORDER BY "
                                + orderBy
                                + " LIMIT ? OFFSET ?",
                        (rs, n) ->
                                new Header(
                                        rs.getLong("id"),
                                        rs.getLong("customer_id"),
                                        rs.getString("customer_name"),
                                        rs.getString("status"),
                                        rs.getBigDecimal("total"),
                                        rs.getLong("version"),
                                        rs.getString("shipping_reference"),
                                        rs.getBigDecimal("shipping_cost"),
                                        rs.getTimestamp("created_at").toInstant(),
                                        rs.getTimestamp("updated_at").toInstant()),
                        args.toArray());
        if (headers.isEmpty())
            return new PageResponse<>(
                    List.of(), page, size, count, (int) ((count + size - 1) / size));
        String placeholders = String.join(",", java.util.Collections.nCopies(headers.size(), "?"));
        record OrderLine(long orderId, OrderDtos.Line line) {}
        var lineRows =
                jdbc.query(
                        "SELECT l.*,p.sku,p.name FROM order_line l JOIN product p ON"
                            + " p.id=l.product_id WHERE l.order_id IN ("
                                + placeholders
                                + ") ORDER BY l.order_id,l.product_id",
                        (rs, n) -> {
                            BigDecimal price = rs.getBigDecimal("unit_price");
                            long quantity = rs.getLong("quantity");
                            var line =
                                    new OrderDtos.Line(
                                            rs.getLong("id"),
                                            rs.getLong("product_id"),
                                            rs.getString("sku"),
                                            rs.getString("name"),
                                            quantity,
                                            price,
                                            price.multiply(BigDecimal.valueOf(quantity)));
                            return new OrderLine(rs.getLong("order_id"), line);
                        },
                        headers.stream().map(Header::id).toArray());
        Map<Long, List<OrderDtos.Line>> grouped =
                lineRows.stream()
                        .collect(
                                Collectors.groupingBy(
                                        OrderLine::orderId,
                                        Collectors.mapping(OrderLine::line, Collectors.toList())));
        return new PageResponse<>(
                headers.stream()
                        .map(h -> view(h, grouped.getOrDefault(h.id(), List.of())))
                        .toList(),
                page,
                size,
                count,
                (int) ((count + size - 1) / size));
    }
}
