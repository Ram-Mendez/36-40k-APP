package com.acme.operations.inventory;

import com.acme.operations.common.PageResponse;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

// Spring registra el acceso a datos; aquí escribimos el SQL y mapeamos sus resultados.
@Repository
public class InventoryRepository {
    // JdbcTemplate ejecuta nuestro SQL y adapta filas; no decide las reglas de negocio.
    private final JdbcTemplate jdbc;

    // Spring suministra las dependencias a este único constructor; no hace falta escribir @Autowired.
    public InventoryRepository(JdbcTemplate jdbc) {
        // Conserva la referencia recibida para que los métodos puedan reutilizar esta dependencia.
        this.jdbc = jdbc;
    }

    // Foto de las cantidades leídas; el bloqueo vive en PostgreSQL, no en este record.
    public record LockedBalance(long onHand, long reserved) {}

    // Debe ejecutarse dentro de la transacción del servicio para conservar el bloqueo.
    public LockedBalance lock(long productId) {
        // Crea el saldo si falta; ON CONFLICT evita duplicarlo si ya existe.
        jdbc.update(
                "INSERT INTO inventory_balance(product_id) VALUES (?) ON CONFLICT DO NOTHING",
                productId);
        // SELECT FOR UPDATE lee y bloquea la fila con este productId hasta commit o rollback.
        return jdbc.queryForObject(
                "SELECT on_hand,reserved FROM inventory_balance WHERE product_id=? FOR UPDATE",
                // rs representa la fila; las posiciones 1 y 2 corresponden a on_hand y reserved.
                (rs, n) -> new LockedBalance(rs.getLong(1), rs.getLong(2)),
                productId);
    }

    // Pregunta si existe el producto, independientemente de cuánto stock tenga.
    public boolean productExists(long productId) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM product WHERE id=?)",
                        Boolean.class,
                        productId));
    }

    // Recibe el total físico ya calculado; version=version+1 no vuelve a poner la versión en 1.
    public void updateOnHand(long productId, long onHand) {
        // El primer ? recibe onHand y el segundo productId: JDBC los asigna por posición.
        jdbc.update(
                "UPDATE inventory_balance SET on_hand=?,version=version+1 WHERE product_id=?",
                onHand,
                productId);
    }

    // Actualiza el total reservado y crea el detalle de qué pedido lo reservó.
    public void insertReservation(long orderId, long productId, long quantity) {
        // Suma quantity al reserved actual; el segundo ? selecciona el producto.
        jdbc.update(
                "UPDATE inventory_balance SET reserved=reserved+?,version=version+1 WHERE"
                    + " product_id=?",
                quantity,
                productId);
        // Relaciona pedido, producto y cantidad; estos ids son referencias, no el id del movimiento.
        jdbc.update(
                "INSERT INTO stock_reservation(order_id,product_id,quantity) VALUES (?,?,?)",
                orderId,
                productId,
                quantity);
    }

    // Devuelve cuántas filas borró; el servicio exige 1 para confirmar que la reserva coincidía.
    public int deleteReservation(long orderId, long productId, long quantity) {
        return jdbc.update(
                "DELETE FROM stock_reservation WHERE order_id=? AND product_id=? AND quantity=?",
                orderId,
                productId,
                quantity);
    }

    // Aplica las dos restas sobre los valores actuales; enviar consume físico y reservado.
    public void decreaseReservedAndOnHand(
            long productId, long reservedQuantity, long shippedQuantity) {
        jdbc.update(
                "UPDATE inventory_balance SET"
                    + " reserved=reserved-?,on_hand=on_hand-?,version=version+1 WHERE product_id=?",
                reservedQuantity,
                shippedQuantity,
                productId);
    }

    // Añade una entrada al historial; PostgreSQL genera el identificador propio del movimiento.
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

    // Devuelve una página de saldos, opcionalmente limitada a un producto.
    public PageResponse<InventoryDtos.Balance> balances(Long productId, int page, int size) {
        // Si no llega productId no hay WHERE; si llega, se añade un único parámetro ?.
        String where = productId == null ? "" : " WHERE p.id=?";
        // El conteo usa solo el filtro; todavía no necesita LIMIT ni OFFSET.
        Object[] countArgs = productId == null ? new Object[] {} : new Object[] {productId};
        // Cuenta todos los productos coincidentes para calcular el total de páginas.
        long count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM product p" + where, Long.class, countArgs);
        // Los argumentos siguen el orden de los ?: filtro opcional, size y page*size.
        Object[] args =
                productId == null
                        ? new Object[] {size, (long) page * size}
                        : new Object[] {productId, size, (long) page * size};
        // LEFT JOIN incluye productos sin saldo; COALESCE representa sus cantidades ausentes como cero.
        var rows =
                jdbc.query(
                        "SELECT p.id,p.sku,p.name,coalesce(i.on_hand,0),coalesce(i.reserved,0) FROM"
                            + " product p LEFT JOIN inventory_balance i ON i.product_id=p.id"
                                + where
                                + " ORDER BY p.id LIMIT ? OFFSET ?",
                        // El mapper convierte cada fila SQL en un Balance; n es el índice de fila y aquí no se usa.
                        (rs, n) ->
                                new InventoryDtos.Balance(
                                        rs.getLong(1),
                                        rs.getString(2),
                                        rs.getString(3),
                                        rs.getLong(4),
                                        rs.getLong(5),
                                        // Calcula disponible restando reservado a físico; las columnas JDBC empiezan en 1.
                                        rs.getLong(4) - rs.getLong(5)),
                        args);
        // Devuelve resultados y metadatos; la división redondea hacia arriba el total de páginas.
        return new PageResponse<>(rows, page, size, count, (int) ((count + size - 1) / size));
    }

    // Lista los movimientos registrados para una consulta paginada.
    public PageResponse<InventoryDtos.Movement> movements(Long productId, int page, int size) {
        // Reutiliza el mismo filtro en el conteo y en la consulta paginada.
        String where = productId == null ? "" : " WHERE product_id=?";
        Object[] countArgs = productId == null ? new Object[] {} : new Object[] {productId};
        // LIMIT recibe size; OFFSET recibe page*size porque page comienza en cero.
        Object[] args =
                productId == null
                        ? new Object[] {size, (long) page * size}
                        : new Object[] {productId, size, (long) page * size};
        // El conteo no lleva paginación: mide todos los movimientos que cumplen el filtro.
        long count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM stock_movement" + where, Long.class, countArgs);
        // Lee solo la página solicitada y transforma cada fila en un DTO de movimiento.
        var rows =
                jdbc.query(
                        "SELECT * FROM stock_movement"
                                + where
                                + " ORDER BY id DESC LIMIT ? OFFSET ?",
                        (rs, n) ->
                                new InventoryDtos.Movement(
                                        rs.getLong("id"),
                                        rs.getLong("product_id"),
                                        // Long permite conservar null cuando el movimiento no pertenece a ningún pedido.
                                        rs.getObject("order_id", Long.class),
                                        rs.getString("kind"),
                                        rs.getLong("quantity"),
                                        rs.getString("reason"),
                                        rs.getTimestamp("created_at").toInstant()),
                        args);
        // Entrega el historial paginado sin modificar inventario.
        return new PageResponse<>(rows, page, size, count, (int) ((count + size - 1) / size));
    }

    // Lista el detalle de reservas: pedido, producto, SKU y unidades comprometidas.
    public PageResponse<InventoryDtos.Reservation> reservations(int page, int size) {
        // Cuenta reservas, no productos ni unidades totales.
        long count = jdbc.queryForObject("SELECT count(*) FROM stock_reservation", Long.class);
        // Une cada reserva con su producto para devolver también el SKU.
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
                        // Estos valores rellenan LIMIT y OFFSET, en ese orden.
                        size,
                        (long) page * size);
        // Entrega la página de reservas junto con sus totales.
        return new PageResponse<>(rows, page, size, count, (int) ((count + size - 1) / size));
    }
}
