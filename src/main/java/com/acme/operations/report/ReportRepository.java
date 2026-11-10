package com.acme.operations.report;

import com.acme.operations.common.PageResponse;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// Spring registra el acceso a datos; aquí escribimos el SQL y mapeamos sus resultados.
@Repository
public class ReportRepository {
    // JdbcTemplate ejecuta nuestro SQL y adapta filas; no decide las reglas de negocio.
    private final JdbcTemplate jdbc;

    // Spring suministra las dependencias a este único constructor; no hace falta escribir @Autowired.
    public ReportRepository(JdbcTemplate jdbc) {
        // Conserva la referencia recibida para que los métodos puedan reutilizar esta dependencia.
        this.jdbc = jdbc;
    }

    // Agrega ventas por cliente; no lista cada pedido individualmente.
    public PageResponse<ReportDtos.CustomerSales> sales(
            Instant from, Instant to, int page, int size) {
        // Selecciona los estados que se tienen en cuenta para la consulta de ventas.
        StringBuilder filter = new StringBuilder(" WHERE o.status IN ('COMPLETED')");
        // Los parámetros se acumulan en el orden de los ? del filtro.
        List<Object> args = new ArrayList<>();
        // El límite inicial se incluye mediante >= y se convierte a timestamp SQL.
        if (from != null) {
            filter.append(" AND o.updated_at>=?");
            args.add(Timestamp.from(from));
        }
        // El límite final se excluye mediante <, evitando contar dos veces fechas contiguas.
        if (to != null) {
            filter.append(" AND o.updated_at<?");
            args.add(Timestamp.from(to));
        }
        // Cuenta clientes distintos, ya que cada fila del informe representa un cliente.
        long total =
                jdbc.queryForObject(
                        "SELECT count(DISTINCT o.customer_id) FROM sales_order o" + filter,
                        Long.class,
                        args.toArray());
        // Añade tamaño y desplazamiento después de los posibles parámetros de fecha.
        args.add(size);
        args.add((long) page * size);
        // Agrupa por cliente y suma ventas y costes de envío; ordena por ventas e id.
        var rows =
                jdbc.query(
                        "SELECT"
                            + " c.id,c.name,count(o.id),sum(o.total),sum(coalesce(o.shipping_cost,0))"
                            + " FROM customer c JOIN sales_order o ON o.customer_id=c.id"
                                + filter
                                + " GROUP BY c.id,c.name ORDER BY sum(o.total) DESC,c.id LIMIT ?"
                                + " OFFSET ?",
                        // Convierte los agregados SQL en un DTO de ventas por cliente.
                        (rs, n) ->
                                new ReportDtos.CustomerSales(
                                        rs.getLong(1),
                                        rs.getString(2),
                                        rs.getLong(3),
                                        rs.getBigDecimal(4),
                                        rs.getBigDecimal(5)),
                        args.toArray());
        // Devuelve los agregados y los totales de página.
        return new PageResponse<>(rows, page, size, total, (int) ((total + size - 1) / size));
    }

    // Busca productos activos con pocas unidades disponibles para nuevas reservas.
    public PageResponse<ReportDtos.LowStock> lowStock(long threshold, int page, int size) {
        // LEFT JOIN incluye productos sin saldo; COALESCE los trata como cero.
        String from =
                " FROM product p LEFT JOIN inventory_balance i ON i.product_id=p.id WHERE"
                    + " p.active=true AND coalesce(i.on_hand,0)-coalesce(i.reserved,0)<=?";
        // El mismo filtro cuenta productos disponibles por debajo o igual al umbral.
        long total = jdbc.queryForObject("SELECT count(*)" + from, Long.class, threshold);
        // Lee el saldo y cuenta cuántas reservas tiene cada producto.
        var rows =
                jdbc.query(
                        "SELECT"
                            + " p.id,p.sku,p.name,coalesce(i.on_hand,0),coalesce(i.reserved,0),(SELECT"
                            + " count(*) FROM stock_reservation r WHERE r.product_id=p.id)"
                                + from
                                + " ORDER BY coalesce(i.on_hand,0)-coalesce(i.reserved,0),p.id"
                                + " LIMIT ? OFFSET ?",
                        // Construye el DTO incluyendo disponible = on_hand - reserved.
                        (rs, n) ->
                                new ReportDtos.LowStock(
                                        rs.getLong(1),
                                        rs.getString(2),
                                        rs.getString(3),
                                        rs.getLong(4),
                                        rs.getLong(5),
                                        rs.getLong(4) - rs.getLong(5),
                                        // Este conteo procede de stock_reservation; no es una suma de unidades.
                                        rs.getLong(6)),
                        // Los ? reciben threshold, size y page*size, en ese orden.
                        threshold,
                        size,
                        (long) page * size);
        // Entrega la página sin cambiar ningún saldo.
        return new PageResponse<>(rows, page, size, total, (int) ((total + size - 1) / size));
    }
}
