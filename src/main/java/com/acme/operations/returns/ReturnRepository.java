package com.acme.operations.returns;

import com.acme.operations.common.BusinessException;
import com.acme.operations.common.PageResponse;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ReturnRepository {
    private final JdbcTemplate jdbc;

    public ReturnRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Header(
            long id, long orderId, String status, String reason, String reviewReason,
            BigDecimal creditAmount, long version, UUID createRequestId,
            String createFingerprint, UUID receiptRequestId, String receiptFingerprint,
            Instant createdAt, Instant updatedAt, Instant reviewedAt, Instant receivedAt) {}

    public long orderId(long id) {
        var rows = jdbc.queryForList(
                "SELECT order_id FROM sales_return WHERE id=?", Long.class, id);
        if (rows.isEmpty()) throw missing();
        return rows.getFirst();
    }

    public Header header(long id, boolean lock) {
        var rows = jdbc.query(
                "SELECT * FROM sales_return WHERE id=?" + (lock ? " FOR UPDATE" : ""),
                (rs, row) -> header(rs), id);
        if (rows.isEmpty()) throw missing();
        return rows.getFirst();
    }

    public Optional<Header> byCreationRequest(long orderId, UUID requestId) {
        return jdbc.query(
                        "SELECT * FROM sales_return WHERE order_id=? AND create_request_id=?",
                        (rs, row) -> header(rs), orderId, requestId)
                .stream().findFirst();
    }

    public long allocatedQuantity(long orderId, long productId) {
        return jdbc.queryForObject(
                "SELECT coalesce(sum(l.quantity),0) FROM sales_return r"
                        + " JOIN sales_return_line l ON l.return_id=r.id"
                        + " WHERE r.order_id=? AND l.product_id=?",
                Long.class, orderId, productId);
    }

    public long create(long orderId, UUID requestId, String fingerprint,
                       String reason, BigDecimal creditAmount) {
        return jdbc.queryForObject(
                "INSERT INTO sales_return(order_id,create_request_id,create_fingerprint,reason,credit_amount)"
                        + " VALUES (?,?,?,?,?) RETURNING id",
                Long.class, orderId, requestId, fingerprint, reason, creditAmount);
    }

    public void addLine(long returnId, long productId, long quantity, BigDecimal unitPrice) {
        jdbc.update(
                "INSERT INTO sales_return_line(return_id,product_id,quantity,unit_price) VALUES (?,?,?,?)",
                returnId, productId, quantity, unitPrice);
    }

    public void decide(long id, String status, String reason) {
        jdbc.update(
                "UPDATE sales_return SET status=?,review_reason=?,reviewed_at=now(),"
                        + "version=version+1,updated_at=now() WHERE id=?",
                status, reason, id);
    }

    public void disposition(long returnId, long productId, long restock, long damaged) {
        jdbc.update(
                "UPDATE sales_return_line SET restock_quantity=?,damaged_quantity=?"
                        + " WHERE return_id=? AND product_id=?",
                restock, damaged, returnId, productId);
    }

    public void receive(long id, UUID requestId, String fingerprint) {
        jdbc.update(
                "UPDATE sales_return SET status='RECEIVED',receipt_request_id=?,"
                        + "receipt_fingerprint=?,received_at=now(),updated_at=now(),version=version+1 WHERE id=?",
                requestId, fingerprint, id);
    }

    public List<ReturnDtos.Line> lines(long returnId) {
        return jdbc.query(
                "SELECT l.*,p.sku FROM sales_return_line l JOIN product p ON p.id=l.product_id"
                        + " WHERE l.return_id=? ORDER BY l.product_id",
                (rs, row) -> new ReturnDtos.Line(
                        rs.getLong("product_id"), rs.getString("sku"), rs.getLong("quantity"),
                        rs.getBigDecimal("unit_price"),
                        rs.getBigDecimal("unit_price").multiply(BigDecimal.valueOf(rs.getLong("quantity"))),
                        rs.getObject("restock_quantity", Long.class),
                        rs.getObject("damaged_quantity", Long.class)),
                returnId);
    }

    public ReturnDtos.View get(long id) {
        Header h = header(id, false);
        return new ReturnDtos.View(
                h.id(), h.orderId(), h.status(), h.reason(), h.reviewReason(), h.creditAmount(),
                h.version(), h.createRequestId(), h.receiptRequestId(), h.createdAt(), h.updatedAt(),
                h.reviewedAt(), h.receivedAt(), lines(id));
    }

    public PageResponse<ReturnDtos.Summary> search(
            Long orderId, String status, int page, int size, String orderBy) {
        var args = new ArrayList<Object>();
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (orderId != null) {
            where.append(" AND order_id=?");
            args.add(orderId);
        }
        if (status != null) {
            where.append(" AND status=?");
            args.add(status);
        }
        long count = jdbc.queryForObject(
                "SELECT count(*) FROM sales_return" + where, Long.class, args.toArray());
        args.add(size);
        args.add((long) page * size);
        var rows = jdbc.query(
                "SELECT * FROM sales_return" + where + " ORDER BY " + orderBy + " LIMIT ? OFFSET ?",
                (rs, row) -> new ReturnDtos.Summary(
                        rs.getLong("id"), rs.getLong("order_id"), rs.getString("status"),
                        rs.getString("reason"), rs.getBigDecimal("credit_amount"),
                        rs.getLong("version"), instant(rs, "created_at"),
                        instant(rs, "reviewed_at"), instant(rs, "received_at")),
                args.toArray());
        return new PageResponse<>(rows, page, size, count, (int) ((count + size - 1) / size));
    }

    private static Header header(ResultSet rs) throws SQLException {
        return new Header(
                rs.getLong("id"), rs.getLong("order_id"), rs.getString("status"),
                rs.getString("reason"), rs.getString("review_reason"), rs.getBigDecimal("credit_amount"),
                rs.getLong("version"), rs.getObject("create_request_id", UUID.class),
                rs.getString("create_fingerprint"), rs.getObject("receipt_request_id", UUID.class),
                rs.getString("receipt_fingerprint"), instant(rs, "created_at"),
                instant(rs, "updated_at"), instant(rs, "reviewed_at"), instant(rs, "received_at"));
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static BusinessException missing() {
        return new BusinessException(HttpStatus.NOT_FOUND, "RETURN_NOT_FOUND", "Return not found");
    }
}
