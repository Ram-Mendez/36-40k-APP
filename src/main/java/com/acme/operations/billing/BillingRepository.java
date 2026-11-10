package com.acme.operations.billing;

import com.acme.operations.common.BusinessException;
import com.acme.operations.common.PageResponse;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class BillingRepository {
    private final JdbcTemplate jdbc;

    // Aggregate each one-to-many relationship before joining, preserving one row per invoice.
    private static final String BALANCES = """
            WITH credits AS (
                SELECT order_id, sum(credit_amount) AS credited
                FROM sales_return WHERE status='RECEIVED' GROUP BY order_id
            ), payments AS (
                SELECT invoice_id,
                    sum(CASE WHEN kind='PAYMENT' THEN amount ELSE -amount END) AS paid
                FROM invoice_entry WHERE reversed_at IS NULL GROUP BY invoice_id
            ), balances AS (
                SELECT i.*, i.product_total+i.shipping_total AS total,
                    coalesce(c.credited,0) AS credited, coalesce(p.paid,0) AS paid,
                    i.product_total+i.shipping_total-coalesce(c.credited,0)-coalesce(p.paid,0) AS balance
                FROM invoice i
                LEFT JOIN credits c ON c.order_id=i.order_id
                LEFT JOIN payments p ON p.invoice_id=i.id
            ), ledger AS (
                SELECT b.*, CASE WHEN balance < 0 THEN 'CREDIT_DUE'
                    WHEN balance = 0 THEN 'SETTLED'
                    WHEN paid > 0 OR credited > 0 THEN 'PARTIALLY_PAID'
                    ELSE 'OPEN' END AS settlement_status
                FROM balances b
            )
            """;

    private static final RowMapper<BillingDtos.Invoice> INVOICE = (rs, row) ->
            new BillingDtos.Invoice(
                    rs.getLong("id"), "INV-" + rs.getLong("id"), rs.getLong("order_id"),
                    rs.getLong("customer_id"), rs.getString("customer_name"),
                    rs.getString("customer_tax_id"), rs.getString("customer_address"),
                    rs.getString("currency"), rs.getBigDecimal("product_total"),
                    rs.getBigDecimal("shipping_total"), rs.getBigDecimal("total"),
                    rs.getBigDecimal("credited"), rs.getBigDecimal("paid"), rs.getBigDecimal("balance"),
                    rs.getString("settlement_status"), rs.getObject("due_date", LocalDate.class),
                    rs.getTimestamp("issued_at").toInstant());

    private static final RowMapper<BillingDtos.Entry> ENTRY = (rs, row) ->
            new BillingDtos.Entry(rs.getLong("id"), rs.getLong("invoice_id"),
                    rs.getObject("request_id", UUID.class), rs.getString("kind"),
                    rs.getBigDecimal("amount"), rs.getString("reference"),
                    rs.getTimestamp("created_at").toInstant(), nullableInstant(rs, "reversed_at"),
                    rs.getString("reversal_reason"));

    public BillingRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record OrderSnapshot(long id, String status) {}

    public OrderSnapshot lockOrder(long orderId) {
        return jdbc.query("SELECT id,status FROM sales_order WHERE id=? FOR UPDATE",
                        (rs, n) -> new OrderSnapshot(rs.getLong(1), rs.getString(2)), orderId)
                .stream().findFirst().orElseThrow(() -> missing("ORDER_NOT_FOUND", "Order not found"));
    }

    public long orderId(long invoiceId) {
        return jdbc.query("SELECT order_id FROM invoice WHERE id=?", (rs, n) -> rs.getLong(1), invoiceId)
                .stream().findFirst().orElseThrow(() -> missing("INVOICE_NOT_FOUND", "Invoice not found"));
    }

    public void lockInvoice(long invoiceId) {
        jdbc.queryForObject("SELECT id FROM invoice WHERE id=? FOR UPDATE", Long.class, invoiceId);
    }

    public Optional<Long> invoiceForOrder(long orderId) {
        return jdbc.query("SELECT id FROM invoice WHERE order_id=?", (rs, n) -> rs.getLong(1), orderId)
                .stream().findFirst();
    }

    public long issue(long orderId, LocalDate dueDate) {
        return jdbc.queryForObject("""
                INSERT INTO invoice(order_id,customer_id,customer_name,customer_tax_id,
                    customer_address,product_total,shipping_total,due_date)
                SELECT o.id,o.customer_id,c.name,c.tax_id,c.address,o.total,
                    coalesce(o.shipping_cost,0),? FROM sales_order o JOIN customer c ON c.id=o.customer_id
                WHERE o.id=? RETURNING id
                """, Long.class, dueDate, orderId);
    }

    public BillingDtos.Invoice get(long id) {
        return jdbc.query(BALANCES + " SELECT * FROM ledger WHERE id=?", INVOICE, id)
                .stream().findFirst().orElseThrow(() -> missing("INVOICE_NOT_FOUND", "Invoice not found"));
    }

    public PageResponse<BillingDtos.Invoice> list(
            Long customerId, String status, int page, int size) {
        var args = new ArrayList<Object>();
        String where = " WHERE 1=1";
        if (customerId != null) { where += " AND customer_id=?"; args.add(customerId); }
        if (status != null) { where += " AND settlement_status=?"; args.add(status); }
        long count = jdbc.queryForObject(BALANCES + " SELECT count(*) FROM ledger" + where,
                Long.class, args.toArray());
        args.add(size);
        args.add((long) page * size);
        var rows = jdbc.query(BALANCES + " SELECT * FROM ledger" + where
                + " ORDER BY due_date,id LIMIT ? OFFSET ?", INVOICE, args.toArray());
        return new PageResponse<>(rows, page, size, count, (int) ((count + size - 1) / size));
    }

    public Optional<BillingDtos.Entry> byRequest(long invoiceId, UUID requestId) {
        return jdbc.query("SELECT * FROM invoice_entry WHERE invoice_id=? AND request_id=?",
                ENTRY, invoiceId, requestId).stream().findFirst();
    }

    public BillingDtos.Entry entry(long invoiceId, long entryId) {
        return jdbc.query("SELECT * FROM invoice_entry WHERE invoice_id=? AND id=?",
                ENTRY, invoiceId, entryId).stream().findFirst()
                .orElseThrow(() -> missing("ENTRY_NOT_FOUND", "Entry not found for this invoice"));
    }

    public long post(long invoiceId, String kind, BillingDtos.Settlement request) {
        return jdbc.queryForObject("""
                INSERT INTO invoice_entry(invoice_id,request_id,kind,amount,reference)
                VALUES (?,?,?,?,?) RETURNING id
                """, Long.class, invoiceId, request.requestId(), kind, request.amount(), request.reference().strip());
    }

    public void reverse(long invoiceId, long entryId, String reason) {
        jdbc.update("UPDATE invoice_entry SET reversed_at=now(),reversal_reason=? WHERE invoice_id=? AND id=?",
                reason, invoiceId, entryId);
    }

    public PageResponse<BillingDtos.Entry> entries(long invoiceId, int page, int size) {
        long count = jdbc.queryForObject("SELECT count(*) FROM invoice_entry WHERE invoice_id=?",
                Long.class, invoiceId);
        var rows = jdbc.query("SELECT * FROM invoice_entry WHERE invoice_id=? ORDER BY id DESC LIMIT ? OFFSET ?",
                ENTRY, invoiceId, size, (long) page * size);
        return new PageResponse<>(rows, page, size, count, (int) ((count + size - 1) / size));
    }

    public PageResponse<BillingDtos.Credit> credits(long orderId, int page, int size) {
        long count = jdbc.queryForObject("SELECT count(*) FROM sales_return WHERE order_id=? AND status='RECEIVED'",
                Long.class, orderId);
        var rows = jdbc.query("SELECT id,credit_amount,received_at FROM sales_return"
                + " WHERE order_id=? AND status='RECEIVED' ORDER BY id LIMIT ? OFFSET ?",
                (rs, n) -> new BillingDtos.Credit(rs.getLong(1), rs.getBigDecimal(2),
                        rs.getTimestamp(3).toInstant()), orderId, size, (long) page * size);
        return new PageResponse<>(rows, page, size, count, (int) ((count + size - 1) / size));
    }

    public BillingDtos.Receivables receivables(LocalDate asOf, Long customerId) {
        String where = customerId == null ? "" : " WHERE customer_id=?";
        Object[] args = customerId == null ? new Object[] {asOf} : new Object[] {asOf, customerId};
        // asOf ages today's balances; this endpoint does not claim a historical ledger reconstruction.
        String query = BALANCES + """
                SELECT CASE WHEN balance<0 THEN 'CREDIT_DUE' WHEN balance=0 THEN 'SETTLED'
                    WHEN age<=0 THEN 'NOT_DUE' WHEN age<=30 THEN '1_30'
                    WHEN age<=60 THEN '31_60' WHEN age<=90 THEN '61_90' ELSE 'OVER_90' END AS bucket,
                    count(*) AS count,sum(abs(balance)) AS amount
                FROM (SELECT balance,cast(? AS date)-due_date AS age FROM ledger
                """ + where + ") aged GROUP BY bucket";
        var rows = jdbc.query(query, (rs, n) -> new BillingDtos.AgingBucket(
                rs.getString("bucket"), rs.getLong("count"), rs.getBigDecimal("amount")), args);
        List<String> names = List.of("NOT_DUE", "1_30", "31_60", "61_90", "OVER_90");
        var buckets = names.stream().map(name -> rows.stream().filter(row -> row.name().equals(name))
                .findFirst().orElse(new BillingDtos.AgingBucket(name, 0, BigDecimal.ZERO.setScale(2)))).toList();
        BigDecimal outstanding = buckets.stream().map(BillingDtos.AgingBucket::amount)
                .reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
        BigDecimal overdue = outstanding.subtract(buckets.getFirst().amount());
        BigDecimal refund = rows.stream().filter(row -> row.name().equals("CREDIT_DUE"))
                .map(BillingDtos.AgingBucket::amount).findFirst().orElse(BigDecimal.ZERO.setScale(2));
        return new BillingDtos.Receivables(asOf, "CURRENT_BALANCE", outstanding, overdue, refund, buckets);
    }

    private static Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        var timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static BusinessException missing(String code, String message) {
        return new BusinessException(HttpStatus.NOT_FOUND, code, message);
    }
}
