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

// Spring registra el acceso a datos; aquí escribimos el SQL y mapeamos sus resultados.
@Repository
public class OrderRepository {
    // JdbcTemplate ejecuta nuestro SQL y adapta filas; no decide las reglas de negocio.
    private final JdbcTemplate jdbc;

    // Spring suministra las dependencias a este único constructor; no hace falta escribir @Autowired.
    public OrderRepository(JdbcTemplate jdbc) {
        // Conserva la referencia recibida para que los métodos puedan reutilizar esta dependencia.
        this.jdbc = jdbc;
    }

    // Agrupa datos generales del pedido; no incluye productos, que se consultan como líneas.
    public record Header(
            // Identificador del pedido; no es el customerId usado para crearlo.
            long id,
            // Cliente propietario del pedido y nombre obtenido mediante JOIN.
            long customerId,
            String customerName,
            // Estado e importe general del pedido.
            String status,
            BigDecimal total,
            // Versión del pedido utilizada para detectar cambios entre lectura y finalización.
            long version,
            // Datos de la cotización de envío, que pueden ser null antes de completar.
            String shippingReference,
            BigDecimal shippingCost,
            // Fechas persistidas de creación y última modificación.
            Instant createdAt,
            Instant updatedAt) {}

    // Busca el pedido y el nombre de su cliente; lock decide si se protege para modificarlo.
    public Header header(long id, boolean lock) {
        // JOIN relaciona customer_id con customer.id; el ? recibe el id del pedido.
        var result =
                jdbc.query(
                        "SELECT o.*,c.name AS customer_name FROM sales_order o JOIN customer c ON"
                            + " c.id=o.customer_id WHERE o.id=?"
                                // Con true bloquea solo la fila de sales_order (alias o), no la fila del cliente.
                                + (lock ? " FOR UPDATE OF o" : ""),
                        // JDBC llama al mapper por fila; rs permite leer columnas por su nombre.
                        (rs, n) ->
                                new Header(
                                        // id pertenece al pedido; customer_id identifica al cliente asociado.
                                        rs.getLong("id"),
                                        rs.getLong("customer_id"),
                                        // customer_name es el alias del nombre obtenido mediante el JOIN.
                                        rs.getString("customer_name"),
                                        rs.getString("status"),
                                        // Total y versión proceden de sales_order, no del parámetro recibido.
                                        rs.getBigDecimal("total"),
                                        rs.getLong("version"),
                                        // La referencia y el coste pueden faltar si el pedido todavía no se completó.
                                        rs.getString("shipping_reference"),
                                        rs.getBigDecimal("shipping_cost"),
                                        // Convierte los timestamps de SQL a Instant para representarlos en Java.
                                        rs.getTimestamp("created_at").toInstant(),
                                        rs.getTimestamp("updated_at").toInstant()),
                        // Este id rellena WHERE o.id=?; no es el id del cliente.
                        id);
        // Una lista vacía indica que no se encontró el pedido.
        if (result.isEmpty())
            // El handler convierte este error de negocio en HTTP 404.
            throw new BusinessException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found");
        // Devuelve la única cabecera encontrada, no la lista completa.
        return result.getFirst();
    }

    // Lee los productos del pedido; una línea representa un producto y su cantidad.
    public List<OrderDtos.Line> lines(long id) {
        // JOIN añade SKU y nombre; ordenar por producto ayuda a mantener el orden de bloqueos.
        return jdbc.query(
                "SELECT l.*,p.sku,p.name FROM order_line l JOIN product p ON p.id=l.product_id"
                    + " WHERE l.order_id=? ORDER BY l.product_id",
                // Transforma la fila SQL en el DTO Line para la respuesta.
                (rs, n) ->
                        new OrderDtos.Line(
                                rs.getLong("id"),
                                rs.getLong("product_id"),
                                rs.getString("sku"),
                                rs.getString("name"),
                                rs.getLong("quantity"),
                                rs.getBigDecimal("unit_price"),
                                // El total de línea se calcula multiplicando precio pactado por unidades.
                                rs.getBigDecimal("unit_price")
                                        .multiply(BigDecimal.valueOf(rs.getLong("quantity")))),
                id);
    }

    // Construye el pedido completo mediante dos consultas: cabecera y líneas.
    public OrderDtos.Order get(long id) {
        // false consulta sin solicitar FOR UPDATE; view reúne ambos resultados.
        return view(header(id, false), lines(id));
    }

    // Esta conversión la hacemos nosotros; Spring no rellena el pedido a partir de customerId.
    private OrderDtos.Order view(Header h, List<OrderDtos.Line> lines) {
        // Copia los datos de cabecera y añade la lista de líneas al DTO Order.
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

    // Rechaza productos ausentes o inactivos y bloquea su fila durante la transacción.
    public void requireActiveProduct(long id) {
        List<Boolean> activeRows =
                jdbc.queryForList(
                        "SELECT active FROM product WHERE id=? FOR SHARE",
                        Boolean.class,
                        id);

        if (activeRows.isEmpty() || !Boolean.TRUE.equals(activeRows.get(0))) {
            throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "PRODUCT_UNAVAILABLE",
                    "Product is missing or inactive");
        }
    }

    // Valida al cliente y usa FOR SHARE para impedir cambios incompatibles durante la transacción.
    public void requireActiveCustomer(long id) {
        var matches =
                jdbc.queryForList(
                        "SELECT active FROM customer WHERE id=? FOR SHARE", Boolean.class, id);
        // Si no hubo filas el cliente no existe; el resultado será 404.
        if (matches.isEmpty())
            throw new BusinessException(
                    HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", "Customer not found");
        // Si existe pero está inactivo, la operación falla con 409.
        if (!matches.getFirst())
            throw new BusinessException(
                    HttpStatus.CONFLICT, "CUSTOMER_INACTIVE", "Customer is inactive");
    }

    // Lee el precio de un producto activo; FOR SHARE protege esa lectura durante la transacción.
    public BigDecimal activePrice(long productId) {
        var matches =
                jdbc.queryForList(
                        "SELECT unit_price FROM product WHERE id=? AND active=true FOR SHARE",
                        BigDecimal.class,
                        productId);
        // Sin coincidencia no se permite usar el producto para esta operación.
        if (matches.isEmpty())
            throw new BusinessException(
                    HttpStatus.CONFLICT, "PRODUCT_UNAVAILABLE", "Product is missing or inactive");
        // Devuelve el precio monetario leído como BigDecimal.
        return matches.getFirst();
    }

    // Inserta un pedido nuevo asociado al cliente y obtiene su identificador generado.
    public long create(long customerId) {
        // RETURNING id devuelve sales_order.id como Long; los demás campos usan los valores de la tabla.
        return jdbc.queryForObject(
                "INSERT INTO sales_order(customer_id) VALUES (?) RETURNING id",
                Long.class,
                customerId);
    }

    // Cambia el cliente del pedido e incrementa su versión; el servicio ya validó el estado.
    public void customer(long id, long customerId) {
        jdbc.update(
                "UPDATE sales_order SET customer_id=?,version=version+1,updated_at=now() WHERE"
                    + " id=?",
                customerId,
                id);
    }

    // Mantiene una línea por pedido y producto usando la clave única de esa pareja.
    public void setLine(long id, long productId, long quantity, BigDecimal currentPrice) {
        // Si ya existe, solo reemplaza quantity; conserva el precio pactado originalmente.
        jdbc.update(
                "INSERT INTO order_line(order_id,product_id,quantity,unit_price) VALUES (?,?,?,?)"
                    + " ON CONFLICT(order_id,product_id) DO UPDATE SET quantity=excluded.quantity",
                id,
                productId,
                quantity,
                currentPrice);
        // Actualiza el total del pedido para reflejar la cantidad recién guardada.
        recalculate(id);
    }

    // Borra una línea concreta y exige que realmente existiera.
    public void removeLine(long id, long productId) {
        // jdbc.update devuelve filas afectadas, no la versión ni el número de columnas.
        if (jdbc.update("DELETE FROM order_line WHERE order_id=? AND product_id=?", id, productId)
                == 0)
            // Cero filas significa que no había una línea con esos dos ids: se devuelve 404.
            throw new BusinessException(
                    HttpStatus.NOT_FOUND, "ORDER_LINE_NOT_FOUND", "Order line not found");
        // Recalcula el pedido después de retirar su línea.
        recalculate(id);
    }

    // Suma precio por cantidad de todas las líneas; sin líneas el total es cero.
    private void recalculate(long id) {
        BigDecimal amount =
                jdbc.queryForObject(
                        "SELECT coalesce(sum(DISTINCT quantity*unit_price),0) FROM order_line WHERE"
                            + " order_id=?",
                        BigDecimal.class,
                        id);
        // Persiste el total y aumenta la versión para señalar que el pedido cambió.
        jdbc.update(
                "UPDATE sales_order SET total=?,version=version+1,updated_at=now() WHERE id=?",
                amount,
                id);
    }

    // Guarda el estado y los datos de envío; las reglas de transición están en el servicio.
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

    // Busca pedidos con filtros opcionales y carga sus líneas por lotes.
    public PageResponse<OrderDtos.Order> search(
            Long customerId, String status, int page, int size, String sort) {
        // Solo admite estos órdenes conocidos: no concatena un nombre de columna libre del usuario.
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
        // WHERE 1=1 permite añadir filtros AND sin comprobar cuál es el primero.
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        // Guarda parámetros en el mismo orden en que aparecen los ? del SQL.
        List<Object> args = new ArrayList<>();
        // Añade el filtro por cliente únicamente cuando se proporcionó.
        if (customerId != null) {
            where.append(" AND o.customer_id=?");
            args.add(customerId);
        }
        // Si se envió estado, comprueba que pertenezca a la lista admitida.
        if (status != null) {
            // Rechaza valores desconocidos antes de ejecutar SQL.
            if (!List.of("DRAFT", "CONFIRMED", "COMPLETED", "CANCELLED").contains(status))
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Unknown order status");
            // Añade el estado como parámetro, no como texto SQL concatenado.
            where.append(" AND o.status=?");
            args.add(status);
        }
        // Cuenta pedidos que cumplen los filtros antes de aplicar LIMIT y OFFSET.
        long count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM sales_order o" + where, Long.class, args.toArray());
        // size limita las filas; page*size omite páginas anteriores (page empieza en cero).
        args.add(size);
        args.add((long) page * size);
        // Consulta las cabeceras de esta página junto con el nombre del cliente.
        var headers =
                jdbc.query(
                        "SELECT o.*,c.name AS customer_name FROM sales_order o JOIN customer c ON"
                            + " c.id=o.customer_id"
                                + where
                                + " ORDER BY "
                                + orderBy
                                + " LIMIT ? OFFSET ?",
                        // Cada fila se transforma en Header; las líneas se cargan en la consulta siguiente.
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
        // Si no hay cabeceras evita ejecutar un IN vacío.
        if (headers.isEmpty())
            // Devuelve contenido vacío pero conserva el total para informar de la paginación.
            return new PageResponse<>(
                    List.of(), page, size, count, (int) ((count + size - 1) / size));
        // Genera un ? por pedido; los ids siguen pasando como parámetros JDBC.
        String placeholders = String.join(",", java.util.Collections.nCopies(headers.size(), "?"));
        // Mantiene el id del pedido junto a cada línea para poder agruparlas después.
        record OrderLine(long orderId, OrderDtos.Line line) {}
        // Carga todas las líneas de la página en una consulta, evitando una consulta por pedido.
        var lineRows =
                jdbc.query(
                        "SELECT l.*,p.sku,p.name FROM order_line l JOIN product p ON"
                            + " p.id=l.product_id WHERE l.order_id IN ("
                                + placeholders
                                + ") ORDER BY l.order_id,l.product_id",
                        (rs, n) -> {
                            // Lee precio y cantidad una vez para calcular el total de la línea.
                            BigDecimal price = rs.getBigDecimal("unit_price");
                            long quantity = rs.getLong("quantity");
                            // Construye la línea con datos del producto y del pedido.
                            var line =
                                    new OrderDtos.Line(
                                            rs.getLong("id"),
                                            rs.getLong("product_id"),
                                            rs.getString("sku"),
                                            rs.getString("name"),
                                            quantity,
                                            price,
                                            price.multiply(BigDecimal.valueOf(quantity)));
                            // Asocia la línea al pedido que la contiene.
                            return new OrderLine(rs.getLong("order_id"), line);
                        },
                        // Los ids de las cabeceras rellenan los ? del IN en el mismo orden.
                        headers.stream().map(Header::id).toArray());
        // Agrupa las líneas por orderId para recuperarlas al construir cada pedido.
        Map<Long, List<OrderDtos.Line>> grouped =
                lineRows.stream()
                        .collect(
                                Collectors.groupingBy(
                                        OrderLine::orderId,
                                        Collectors.mapping(OrderLine::line, Collectors.toList())));
        // Une cada cabecera con sus líneas y devuelve la página completa.
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
