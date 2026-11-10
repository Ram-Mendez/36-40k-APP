package com.acme.operations.procurement;

import com.acme.operations.common.BusinessException;
import com.acme.operations.common.PageRequests;
import com.acme.operations.common.PageResponse;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public class ProcurementRepository {
    private static final Map<String, String> SUPPLIER_SORTS = Map.of(
            "id", "id", "name", "name", "taxId", "tax_id",
            "leadTimeDays", "lead_time_days", "active", "active");
    private static final Map<String, String> PURCHASE_SORTS = Map.of(
            "id", "o.id", "createdAt", "o.created_at", "expectedOn", "o.expected_on",
            "total", "o.total", "status", "o.status");
    private static final Set<String> STATUSES = Set.of(
            "DRAFT", "SUBMITTED", "PARTIALLY_RECEIVED", "RECEIVED", "CANCELLED");
    private static final String PURCHASE_SELECT =
            "SELECT o.*,s.name AS supplier_name FROM purchase_order o JOIN supplier s ON s.id=o.supplier_id";
    private final JdbcTemplate jdbc;

    public ProcurementRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ProcurementDtos.Supplier supplier(long id, boolean lock) {
        var rows = jdbc.query("SELECT * FROM supplier WHERE id=?" + (lock ? " FOR UPDATE" : ""),
                (rs, n) -> supplierRow(rs), id);
        if (rows.isEmpty()) throw missing("SUPPLIER_NOT_FOUND", "Supplier not found");
        return rows.getFirst();
    }

    public ProcurementDtos.Supplier activeSupplier(long id) {
        var rows = jdbc.query("SELECT * FROM supplier WHERE id=? FOR SHARE",
                (rs, n) -> supplierRow(rs), id);
        if (rows.isEmpty()) throw missing("SUPPLIER_NOT_FOUND", "Supplier not found");
        var supplier = rows.getFirst();
        if (!supplier.active()) throw new BusinessException(
                HttpStatus.CONFLICT, "SUPPLIER_INACTIVE", "Supplier is inactive");
        return supplier;
    }

    public long createSupplier(ProcurementDtos.SupplierInput input) {
        return jdbc.queryForObject("""
                INSERT INTO supplier(name,tax_id,email,lead_time_days) VALUES (?,?,?,?) RETURNING id
                """, Long.class, input.name().trim(), input.taxId().toUpperCase(Locale.ROOT),
                input.email().trim(), input.leadTimeDays());
    }

    public void updateSupplier(long id, ProcurementDtos.SupplierInput input) {
        jdbc.update("""
                UPDATE supplier SET name=?,tax_id=?,email=?,lead_time_days=?,version=version+1 WHERE id=?
                """, input.name().trim(), input.taxId().toUpperCase(Locale.ROOT),
                input.email().trim(), input.leadTimeDays(), id);
    }

    public void deactivateSupplier(long id) {
        jdbc.update("UPDATE supplier SET active=false,version=version+1 WHERE id=?", id);
    }

    public PageResponse<ProcurementDtos.Supplier> suppliers(
            String search, Boolean active, int page, int size, String sort, String direction) {
        PageRequests.of(page, size, sort, direction, SUPPLIER_SORTS.keySet());
        var where = new StringBuilder(" WHERE true");
        var args = new ArrayList<Object>();
        if (active != null) {
            where.append(" AND active=?");
            args.add(active);
        }
        if (search != null && !search.isBlank()) {
            if (search.length() > 200) throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "INVALID_SEARCH", "Search is limited to 200 characters");
            String pattern = "%" + search.trim().toLowerCase(Locale.ROOT)
                    .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            where.append(" AND (lower(name) LIKE ? ESCAPE '!' OR lower(tax_id) LIKE ? ESCAPE '!')");
            args.add(pattern);
            args.add(pattern);
        }
        long count = jdbc.queryForObject("SELECT count(*) FROM supplier" + where, Long.class, args.toArray());
        args.add(size);
        args.add((long) page * size);
        String order = SUPPLIER_SORTS.get(sort) + " " + direction.toUpperCase(Locale.ROOT)
                + (sort.equals("id") ? "" : ",id ASC");
        var rows = jdbc.query("SELECT * FROM supplier" + where + " ORDER BY " + order + " LIMIT ? OFFSET ?",
                (rs, n) -> supplierRow(rs), args.toArray());
        return page(rows, page, size, count);
    }

    public long create(long supplierId, LocalDate expectedOn) {
        return jdbc.queryForObject(
                "INSERT INTO purchase_order(supplier_id,expected_on) VALUES (?,?) RETURNING id",
                Long.class, supplierId, expectedOn);
    }

    public ProcurementDtos.PurchaseSummary header(long id, boolean lock) {
        var rows = jdbc.query(PURCHASE_SELECT + " WHERE o.id=?" + (lock ? " FOR UPDATE OF o" : ""),
                (rs, n) -> headerRow(rs), id);
        if (rows.isEmpty()) throw missing("PURCHASE_ORDER_NOT_FOUND", "Purchase order not found");
        return rows.getFirst();
    }

    public List<ProcurementDtos.PurchaseLine> lines(long id) {
        return jdbc.query("""
                SELECT l.*,p.sku,p.name FROM purchase_order_line l JOIN product p ON p.id=l.product_id
                WHERE l.purchase_order_id=? ORDER BY l.product_id
                """, (rs, n) -> {
                    long quantity = rs.getLong("quantity");
                    long received = rs.getLong("received_quantity");
                    BigDecimal cost = rs.getBigDecimal("unit_cost");
                    return new ProcurementDtos.PurchaseLine(rs.getLong("product_id"),
                            rs.getString("sku"), rs.getString("name"), quantity, received,
                            quantity - received, cost, cost.multiply(BigDecimal.valueOf(quantity)));
                }, id);
    }

    public ProcurementDtos.Purchase get(long id) {
        var h = header(id, false);
        return new ProcurementDtos.Purchase(h.id(), h.supplierId(), h.supplierName(), h.status(),
                h.expectedOn(), h.total(), h.version(), h.createdAt(), h.updatedAt(), lines(id));
    }

    public PageResponse<ProcurementDtos.PurchaseSummary> search(
            Long supplierId, String status, LocalDate dueBefore,
            int page, int size, String sort, String direction) {
        PageRequests.of(page, size, sort, direction, PURCHASE_SORTS.keySet());
        var where = new StringBuilder(" WHERE true");
        var args = new ArrayList<Object>();
        if (supplierId != null) {
            where.append(" AND o.supplier_id=?");
            args.add(supplierId);
        }
        if (status != null) {
            if (!STATUSES.contains(status)) throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Unknown purchase order status");
            where.append(" AND o.status=?");
            args.add(status);
        }
        if (dueBefore != null) {
            where.append(" AND o.expected_on<?");
            args.add(dueBefore);
        }
        long count = jdbc.queryForObject("SELECT count(*) FROM purchase_order o" + where,
                Long.class, args.toArray());
        args.add(size);
        args.add((long) page * size);
        String order = PURCHASE_SORTS.get(sort) + " " + direction.toUpperCase(Locale.ROOT)
                + (sort.equals("id") ? "" : ",o.id ASC");
        var rows = jdbc.query(PURCHASE_SELECT + where + " ORDER BY " + order + " LIMIT ? OFFSET ?",
                (rs, n) -> headerRow(rs), args.toArray());
        return page(rows, page, size, count);
    }

    public void requireActiveProduct(long id) {
        var matches = jdbc.queryForList("SELECT active FROM product WHERE id=? FOR SHARE", Boolean.class, id);
        if (matches.isEmpty() || !matches.getFirst()) throw new BusinessException(
                HttpStatus.CONFLICT, "PRODUCT_UNAVAILABLE", "Product is missing or inactive");
    }

    public void setLine(long id, long productId, ProcurementDtos.SetLine input) {
        jdbc.update("""
                INSERT INTO purchase_order_line(purchase_order_id,product_id,quantity,unit_cost)
                VALUES (?,?,?,?) ON CONFLICT(purchase_order_id,product_id)
                DO UPDATE SET quantity=excluded.quantity,unit_cost=excluded.unit_cost
                """, id, productId, input.quantity(), input.unitCost());
        recalculate(id);
    }

    public void removeLine(long id, long productId) {
        if (jdbc.update("DELETE FROM purchase_order_line WHERE purchase_order_id=? AND product_id=?",
                id, productId) != 1) throw missing("PURCHASE_LINE_NOT_FOUND", "Purchase line not found");
        recalculate(id);
    }

    private void recalculate(long id) {
        jdbc.update("""
                UPDATE purchase_order SET total=(SELECT coalesce(sum(quantity*unit_cost),0)
                FROM purchase_order_line WHERE purchase_order_id=?),version=version+1,updated_at=now() WHERE id=?
                """, id, id);
    }

    public void transition(long id, String status) {
        jdbc.update("UPDATE purchase_order SET status=?,version=version+1,updated_at=now() WHERE id=?", status, id);
    }

    public record PreviousReceipt(long id, String payloadHash) {}

    public Optional<PreviousReceipt> previousReceipt(long purchaseOrderId, UUID requestId) {
        return jdbc.query("SELECT id,payload_hash FROM purchase_receipt WHERE purchase_order_id=? AND request_id=?",
                (rs, n) -> new PreviousReceipt(rs.getLong("id"), rs.getString("payload_hash")),
                purchaseOrderId, requestId).stream().findFirst();
    }

    public long createReceipt(long purchaseOrderId, UUID requestId, String reference, String payloadHash) {
        return jdbc.queryForObject("""
                INSERT INTO purchase_receipt(purchase_order_id,request_id,delivery_reference,payload_hash)
                VALUES (?,?,?,?) RETURNING id
                """, Long.class, purchaseOrderId, requestId, reference, payloadHash);
    }

    public void receiveLine(long receiptId, long purchaseOrderId, long productId, long quantity) {
        jdbc.update("""
                INSERT INTO purchase_receipt_line(receipt_id,purchase_order_id,product_id,quantity) VALUES (?,?,?,?)
                """, receiptId, purchaseOrderId, productId, quantity);
        jdbc.update("""
                UPDATE purchase_order_line SET received_quantity=received_quantity+?
                WHERE purchase_order_id=? AND product_id=?
                """, quantity, purchaseOrderId, productId);
    }

    public void updateReceiptStatus(long id) {
        jdbc.update("""
                UPDATE purchase_order SET status=CASE WHEN EXISTS(SELECT 1 FROM purchase_order_line
                WHERE purchase_order_id=? AND received_quantity<quantity)
                THEN 'PARTIALLY_RECEIVED' ELSE 'RECEIVED' END,version=version+1,updated_at=now() WHERE id=?
                """, id, id);
    }

    public ProcurementDtos.Receipt receipt(long purchaseOrderId, long receiptId) {
        var rows = jdbc.query("SELECT * FROM purchase_receipt WHERE purchase_order_id=? AND id=?",
                receiptMapper(), purchaseOrderId, receiptId);
        if (rows.isEmpty()) throw missing("PURCHASE_RECEIPT_NOT_FOUND", "Purchase receipt not found in this order");
        var receipt = rows.getFirst();
        var lines = jdbc.query("SELECT product_id,quantity FROM purchase_receipt_line WHERE receipt_id=? ORDER BY product_id",
                (rs, n) -> new ProcurementDtos.ReceiptLine(rs.getLong("product_id"), rs.getLong("quantity")), receiptId);
        return new ProcurementDtos.Receipt(receipt.id(), receipt.purchaseOrderId(), receipt.requestId(),
                receipt.deliveryReference(), receipt.receivedAt(), lines);
    }

    public PageResponse<ProcurementDtos.ReceiptSummary> receipts(long id, int page, int size) {
        PageRequests.of(page, size, "id", "desc", Set.of("id"));
        long count = jdbc.queryForObject("SELECT count(*) FROM purchase_receipt WHERE purchase_order_id=?", Long.class, id);
        var rows = jdbc.query("SELECT * FROM purchase_receipt WHERE purchase_order_id=? ORDER BY id DESC LIMIT ? OFFSET ?",
                receiptMapper(), id, size, (long) page * size);
        return page(rows, page, size, count);
    }

    private static RowMapper<ProcurementDtos.ReceiptSummary> receiptMapper() {
        return (rs, n) -> new ProcurementDtos.ReceiptSummary(rs.getLong("id"), rs.getLong("purchase_order_id"),
                rs.getObject("request_id", UUID.class), rs.getString("delivery_reference"),
                rs.getTimestamp("received_at").toInstant());
    }

    private static ProcurementDtos.Supplier supplierRow(ResultSet rs) throws SQLException {
        return new ProcurementDtos.Supplier(rs.getLong("id"), rs.getString("name"), rs.getString("tax_id"),
                rs.getString("email"), rs.getInt("lead_time_days"), rs.getBoolean("active"), rs.getLong("version"));
    }

    private static ProcurementDtos.PurchaseSummary headerRow(ResultSet rs) throws SQLException {
        return new ProcurementDtos.PurchaseSummary(rs.getLong("id"), rs.getLong("supplier_id"),
                rs.getString("supplier_name"), rs.getString("status"), rs.getObject("expected_on", LocalDate.class),
                rs.getBigDecimal("total"), rs.getLong("version"), rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private static <T> PageResponse<T> page(List<T> content, int page, int size, long count) {
        return new PageResponse<>(content, page, size, count, (int) ((count + size - 1) / size));
    }

    private static BusinessException missing(String code, String detail) {
        return new BusinessException(HttpStatus.NOT_FOUND, code, detail);
    }
}
