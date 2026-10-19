package com.acme.operations.inventory;

import com.acme.operations.common.PageResponse;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryRepository {
    private final JdbcTemplate jdbc;

    public InventoryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record LockedBalance(long onHand, long reserved) {}

    public LockedBalance lock(long productId) {
        // Creating the missing balance is safe under the product FK and unique key.
        jdbc.update(
                "INSERT INTO inventory_balance(product_id) VALUES (?) ON CONFLICT DO NOTHING",
                productId);
        return jdbc.queryForObject(
                "SELECT on_hand,reserved FROM inventory_balance WHERE product_id=? FOR UPDATE",
                (rs, n) -> new LockedBalance(rs.getLong(1), rs.getLong(2)),
                productId);
    }

    public boolean productExists(long productId) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM product WHERE id=?)",
                        Boolean.class,
                        productId));
    }

    public void updateOnHand(long productId, long onHand) {
        jdbc.update(
                "UPDATE inventory_balance SET on_hand=?,version=version+1 WHERE product_id=?",
                onHand,
                productId);
    }

    public void insertReservation(long orderId, long productId, long quantity) {
        jdbc.update(
                "UPDATE inventory_balance SET reserved=reserved+?,version=version+1 WHERE"
                    + " product_id=?",
                quantity,
                productId);
        jdbc.update(
                "INSERT INTO stock_reservation(order_id,product_id,quantity) VALUES (?,?,?)",
                orderId,
                productId,
                quantity);
    }

    public int deleteReservation(long orderId, long productId, long quantity) {
        return jdbc.update(
                "DELETE FROM stock_reservation WHERE order_id=? AND product_id=? AND quantity=?",
                orderId,
                productId,
                quantity);
    }

    public void decreaseReservedAndOnHand(
            long productId, long reservedQuantity, long shippedQuantity) {
        jdbc.update(
                "UPDATE inventory_balance SET"
                    + " reserved=reserved-?,on_hand=on_hand-?,version=version+1 WHERE product_id=?",
                reservedQuantity,
                shippedQuantity,
                productId);
    }

    public void insertMovement(
            long productId, Long orderId, String kind, long quantity, String reason) {
        jdbc.update(
                "INSERT INTO stock_movement(product_id,order_id,kind,quantity,reason) VALUES"
                    + " (?,?,?,?,?)",
                productId,
                orderId,
                kind,
                quantity,
                reason);
    }

    public PageResponse<InventoryDtos.Balance> balances(Long productId, int page, int size) {
        String where = productId == null ? "" : " WHERE p.id=?";
        Object[] countArgs = productId == null ? new Object[] {} : new Object[] {productId};
        long count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM product p" + where, Long.class, countArgs);
        Object[] args =
                productId == null
                        ? new Object[] {size, (long) page * size}
                        : new Object[] {productId, size, (long) page * size};
        var rows =
                jdbc.query(
                        "SELECT p.id,p.sku,p.name,coalesce(i.on_hand,0),coalesce(i.reserved,0) FROM"
                            + " product p LEFT JOIN inventory_balance i ON i.product_id=p.id"
                                + where
                                + " ORDER BY p.id LIMIT ? OFFSET ?",
                        (rs, n) ->
                                new InventoryDtos.Balance(
                                        rs.getLong(1),
                                        rs.getString(2),
                                        rs.getString(3),
                                        rs.getLong(4),
                                        rs.getLong(5),
                                        rs.getLong(4) - rs.getLong(5)),
                        args);
        return new PageResponse<>(rows, page, size, count, (int) ((count + size - 1) / size));
    }

    public PageResponse<InventoryDtos.Movement> movements(Long productId, int page, int size) {
        String where = productId == null ? "" : " WHERE product_id=?";
        Object[] countArgs = productId == null ? new Object[] {} : new Object[] {productId};
        Object[] args =
                productId == null
                        ? new Object[] {size, (long) page * size}
                        : new Object[] {productId, size, (long) page * size};
        long count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM stock_movement" + where, Long.class, countArgs);
        var rows =
                jdbc.query(
                        "SELECT * FROM stock_movement"
                                + where
                                + " ORDER BY id DESC LIMIT ? OFFSET ?",
                        (rs, n) ->
                                new InventoryDtos.Movement(
                                        rs.getLong("id"),
                                        rs.getLong("product_id"),
                                        rs.getObject("order_id", Long.class),
                                        rs.getString("kind"),
                                        rs.getLong("quantity"),
                                        rs.getString("reason"),
                                        rs.getTimestamp("created_at").toInstant()),
                        args);
        return new PageResponse<>(rows, page, size, count, (int) ((count + size - 1) / size));
    }

    public PageResponse<InventoryDtos.Reservation> reservations(int page, int size) {
        long count = jdbc.queryForObject("SELECT count(*) FROM stock_reservation", Long.class);
        var rows =
                jdbc.query(
                        "SELECT r.order_id,r.product_id,p.sku,r.quantity FROM stock_reservation r"
                            + " JOIN product p ON p.id=r.product_id ORDER BY"
                            + " r.order_id,r.product_id LIMIT ? OFFSET ?",
                        (rs, n) ->
                                new InventoryDtos.Reservation(
                                        rs.getLong(1),
                                        rs.getLong(2),
                                        rs.getString(3),
                                        rs.getLong(4)),
                        size,
                        (long) page * size);
        return new PageResponse<>(rows, page, size, count, (int) ((count + size - 1) / size));
    }
}
