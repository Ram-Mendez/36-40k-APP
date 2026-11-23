package com.acme.operations.inventory;

import com.acme.operations.common.PageResponse;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

// Spring registers this repository. Its methods execute SQL and map the results.
@Repository
public class InventoryRepository {
    // JdbcTemplate executes the SQL and maps the result rows.
    private final JdbcTemplate jdbc;

    // Spring injects the dependencies through the single constructor.
    public InventoryRepository(JdbcTemplate jdbc) {
        // Keep the injected dependency for later calls.
        this.jdbc = jdbc;
    }

    // This record holds a snapshot of the quantities. PostgreSQL holds the row lock.
    public record LockedBalance(long onHand, long reserved) {}

    // It must be run within the service transaction to keep the lock.
    public LockedBalance lock(long productId) {
        // Create the balance if it is missing; ON CONFLICT avoids doubling it if it already exists.
        jdbc.update(
                "INSERT INTO inventory_balance(product_id) VALUES (?) ON CONFLICT DO NOTHING",
                productId);
        // SELECT FOR UPDATE reads and blocks the row with this productId up to commit or rollback.
        return jdbc.queryForObject(
                "SELECT on_hand,reserved FROM inventory_balance WHERE product_id=? FOR UPDATE",
                // rs represents the row; positions 1 and 2 correspond to on_hand and reserved.
                (rs, n) -> new LockedBalance(rs.getLong(1), rs.getLong(2)),
                productId);
    }

    // She asked if the product existed, regardless of how much stock it had.
    public boolean productExists(long productId) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM product WHERE id=?)",
                        Boolean.class,
                        productId));
    }

    // Receive the already calculated physical total; version = version + 1 does not put the version back in 1.
    public void updateOnHand(long productId, long onHand) {
        // The first? receives onHand and the second productId: JDBC assigns them by position.
        jdbc.update(
                "UPDATE inventory_balance SET on_hand=?,version=version+1 WHERE product_id=?",
                onHand,
                productId);
    }

    // Update the total reserved and create the detail of which order you reserved.
    public void insertReservation(long orderId, long productId, long quantity) {
        // Add quantity to the current reserve; the second? selects the product.
        jdbc.update(
                "UPDATE inventory_balance SET reserved=reserved+?,version=version+1 WHERE"
                    + " product_id=?",
                quantity,
                productId);
        // Relation order, product and quantity; these ids are references, not the id of movement.
        jdbc.update(
                "INSERT INTO stock_reservation(order_id,product_id,quantity) VALUES (?,?,?)",
                orderId,
                productId,
                quantity);
    }

    // Returns how many rows you erased; the service requires 1 to confirm that the reservation coincided.
    public int deleteReservation(long orderId, long productId, long quantity) {
        return jdbc.update(
                "DELETE FROM stock_reservation WHERE order_id=? AND product_id=? AND quantity=?",
                orderId,
                productId,
                quantity);
    }

    // Apply the two subsets on current values; send physical and reserved consummation.
    public void decreaseReservedAndOnHand(
            long productId, long reservedQuantity, long shippedQuantity) {
        jdbc.update(
                "UPDATE inventory_balance SET"
                    + " reserved=reserved-?,on_hand=on_hand-?,version=version+1 WHERE product_id=?",
                reservedQuantity,
                shippedQuantity,
                productId);
    }

    // Add an entry to the history; PostgreSQL generates the proper identifier of the movement.
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

    // Returns a balance page, optionally limited to a product.
    public PageResponse<InventoryDtos.Balance> balances(Long productId, int page, int size) {
        // If no productId arrives there is no WHERE; if it arrives, a single parameter is added?.
        String where = productId == null ? "" : " WHERE p.id=?";
        // The count uses only the filter; it does not yet need LIMIT or OFSET.
        Object[] countArgs = productId == null ? new Object[] {} : new Object[] {productId};
        // It counts all the matching products to calculate the total pages.
        long count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM product p" + where, Long.class, countArgs);
        // Bind the optional filter first, followed by the limit and offset.
        Object[] args =
                productId == null
                        ? new Object[] {size, (long) page * size}
                        : new Object[] {productId, size, (long) page * size};
        // LEFT JOIN includes products without an inventory row. COALESCE gives those products a zero balance.
        var rows =
                jdbc.query(
                        "SELECT p.id,p.sku,p.name,coalesce(i.on_hand,0),coalesce(i.reserved,0) FROM"
                            + " product p LEFT JOIN inventory_balance i ON i.product_id=p.id"
                                + where
                                + " ORDER BY p.id LIMIT ? OFFSET ?",
                        // The mapper turns each SQL row into a Balance; n is the row index and here it is not
                        // used.
                        (rs, n) ->
                                new InventoryDtos.Balance(
                                        rs.getLong(1),
                                        rs.getString(2),
                                        rs.getString(3),
                                        rs.getLong(4),
                                        rs.getLong(5),
                                        // Calculate available by subtracting reserved to physical; JDBC columns
                                        // start at 1.
                                        rs.getLong(4) - rs.getLong(5)),
                        args);
        // Returns results and metadata; the division round up total pages.
        return new PageResponse<>(rows, page, size, count, (int) ((count + size - 1) / size));
    }

    // List the movements registered for a paged consultation.
    public PageResponse<InventoryDtos.Movement> movements(Long productId, int page, int size) {
        // It reuses the same filter in the count and in the pagated query.
        String where = productId == null ? "" : " WHERE product_id=?";
        Object[] countArgs = productId == null ? new Object[] {} : new Object[] {productId};
        // LIMIT receives size; OFSET receives page * size because page starts at zero.
        Object[] args =
                productId == null
                        ? new Object[] {size, (long) page * size}
                        : new Object[] {productId, size, (long) page * size};
        // The count does not carry pagination: it measures all the movements that meet the filter.
        long count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM stock_movement" + where, Long.class, countArgs);
        // Read only the requested page and transform each row into a DTO of movement.
        var rows =
                jdbc.query(
                        "SELECT * FROM stock_movement"
                                + where
                                + " ORDER BY id DESC LIMIT ? OFFSET ?",
                        (rs, n) ->
                                new InventoryDtos.Movement(
                                        rs.getLong("id"),
                                        rs.getLong("product_id"),
                                        // Long allows to preserve null when the movement does not belong to any
                                        // order.
                                        rs.getObject("order_id", Long.class),
                                        rs.getString("kind"),
                                        rs.getLong("quantity"),
                                        rs.getString("reason"),
                                        rs.getTimestamp("created_at").toInstant()),
                        args);
        // Deliver the paid history without changing inventory.
        return new PageResponse<>(rows, page, size, count, (int) ((count + size - 1) / size));
    }

    // List the reservation details: order, product, SKU and compromised units.
    public PageResponse<InventoryDtos.Reservation> reservations(int page, int size) {
        // Count reservation records rather than adding the number of units.
        long count = jdbc.queryForObject("SELECT count(*) FROM stock_reservation", Long.class);
        // Join each reservation with your product to return the SKU as well.
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
                        // These values fill in LIMIT and OFSET in that order.
                        size,
                        (long) page * size);
        // Deliver the booking page along with its totals.
        return new PageResponse<>(rows, page, size, count, (int) ((count + size - 1) / size));
    }
}
