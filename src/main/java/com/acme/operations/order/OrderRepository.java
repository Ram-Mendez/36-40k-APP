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

// Spring registers this repository. Its methods execute SQL and map the results.
@Repository
public class OrderRepository {
    // JdbcTemplate executes the SQL and maps the result rows.
    private final JdbcTemplate jdbc;

    // Spring injects the dependencies through the single constructor.
    public OrderRepository(JdbcTemplate jdbc) {
        // Keep the injected dependency for later calls.
        this.jdbc = jdbc;
    }

    // It gathers general order data; it does not include products, which are consulted as lines.
    public record Header(
            // ID of this sales order. The customerId identifies the customer who placed it.
            long id,
            // Customer owner of the order and name obtained by JOIN.
            long customerId,
            String customerName,
            // State and general amount of order.
            String status,
            BigDecimal total,
            // Order version used to detect changes between reading and finishing.
            long version,
            // Shipping quotation data, which can be null before completing.
            String shippingReference,
            BigDecimal shippingCost,
            // Persistent dates of creation and last modification.
            Instant createdAt,
            Instant updatedAt) {}

    // Find your client's order and name; lock decides if it is protected to modify it.
    public Header header(long id, boolean lock) {
        // Join customer_id to customer.id and bind the order ID to the placeholder.
        var result =
                jdbc.query(
                        "SELECT o.*,c.name AS customer_name FROM sales_order o JOIN customer c ON"
                            + " c.id=o.customer_id WHERE o.id=?"
                                // When the flag is true, lock the sales_order row using alias o.
                                + (lock ? " FOR UPDATE OF o" : ""),
                        // JDBC calls the mapper per row; rs allows you to read columns by name.
                        (rs, n) ->
                                new Header(
                                        // The id identifies the sales order. Its customer_id identifies the
                                        // customer.
                                        rs.getLong("id"),
                                        rs.getLong("customer_id"),
                                        // Use the customer_name alias to read the name returned by the join.
                                        rs.getString("customer_name"),
                                        rs.getString("status"),
                                        // Read the total and version from sales_order.
                                        rs.getBigDecimal("total"),
                                        rs.getLong("version"),
                                        // The reference and the cost may be missing if the order is not yet
                                        // completed.
                                        rs.getString("shipping_reference"),
                                        rs.getBigDecimal("shipping_cost"),
                                        // Convert the SQL timstamps to Instant to represent them in Java.
                                        rs.getTimestamp("created_at").toInstant(),
                                        rs.getTimestamp("updated_at").toInstant()),
                        // This id fills WHERE o.id =?; it is not the customer's id.
                        id);
        // An empty list indicates that the order was not found.
        if (result.isEmpty())
            // The handler turns this business error into HTTP 404.
            throw new BusinessException(HttpStatus.NOT_FOUND, "ORDER_NOT_FOUND", "Order not found");
        // Returns the only header found, not the complete list.
        return result.getFirst();
    }

    // Read the products of the order; a line represents a product and its quantity.
    public List<OrderDtos.Line> lines(long id) {
        // JOIN adds SKU and name; ordering by product helps to maintain the order of blockages.
        return jdbc.query(
                "SELECT l.*,p.sku,p.name FROM order_line l JOIN product p ON p.id=l.product_id"
                    + " WHERE l.order_id=? ORDER BY l.product_id",
                // Transforms the SQL row into the DTO Line for the response.
                (rs, n) ->
                        new OrderDtos.Line(
                                rs.getLong("id"),
                                rs.getLong("product_id"),
                                rs.getString("sku"),
                                rs.getString("name"),
                                rs.getLong("quantity"),
                                rs.getBigDecimal("unit_price"),
                                // The total line is calculated by multiplying price agreed by units.
                                rs.getBigDecimal("unit_price")
                                        .multiply(BigDecimal.valueOf(rs.getLong("quantity")))),
                id);
    }

    // Build the full order by two consultations: header and lines.
    public OrderDtos.Order get(long id) {
        // False consult without requesting FOR UPDATE; view gathers both results.
        return view(header(id, false), lines(id));
    }

    // Map customerId explicitly when building the order.
    private OrderDtos.Order view(Header h, List<OrderDtos.Line> lines) {
        // Copy the header data and add the list of lines to the DTO Order.
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

    // It rejects missing or inactive products and blocks its row during the transaction.
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

    // Valid to the customer and use FOR SHARE to prevent incompatible changes during the transaction.
    public void requireActiveCustomer(long id) {
        var matches =
                jdbc.queryForList(
                        "SELECT active FROM customer WHERE id=? FOR SHARE", Boolean.class, id);
        // If there were no rows the client does not exist; the result will be 404.
        if (matches.isEmpty())
            throw new BusinessException(
                    HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", "Customer not found");
        // If it exists but is inactive, the operation fails with 409.
        if (!matches.getFirst())
            throw new BusinessException(
                    HttpStatus.CONFLICT, "CUSTOMER_INACTIVE", "Customer is inactive");
    }

    // Read the price of an active product; FOR SHARE protects that reading during the transaction.
    public BigDecimal activePrice(long productId) {
        var matches =
                jdbc.queryForList(
                        "SELECT unit_price FROM product WHERE id=? AND active=true FOR SHARE",
                        BigDecimal.class,
                        productId);
        // Without coincidence, the product is not allowed to be used for this operation.
        if (matches.isEmpty())
            throw new BusinessException(
                    HttpStatus.CONFLICT, "PRODUCT_UNAVAILABLE", "Product is missing or inactive");
        // Returns the monetary price read as BigDecimal.
        return matches.getFirst();
    }

    // Insert a new order associated with the client and get its generated identifier.
    public long create(long customerId) {
        // Read the generated sales_order.id as a Long. Database defaults supply the other fields.
        return jdbc.queryForObject(
                "INSERT INTO sales_order(customer_id) VALUES (?) RETURNING id",
                Long.class,
                customerId);
    }

    // Change the order client and increase its version; the service has already validated the status.
    public void customer(long id, long customerId) {
        jdbc.update(
                "UPDATE sales_order SET customer_id=?,version=version+1,updated_at=now() WHERE"
                    + " id=?",
                customerId,
                id);
    }

    // It maintains a line per order and product using that couple's unique key.
    public void setLine(long id, long productId, long quantity, BigDecimal currentPrice) {
        // In the single key conflict, the line retains its agreed price.
        jdbc.update(
                "INSERT INTO order_line(order_id,product_id,quantity,unit_price) VALUES (?,?,?,?)"
                    + " ON CONFLICT(order_id,product_id) DO UPDATE SET quantity=excluded.quantity",
                id,
                productId,
                quantity,
                currentPrice);
        // Update the order total to reflect the amount recently saved.
        recalculate(id);
    }

    // It erases a specific line and demands that it really exist.
    public void removeLine(long id, long productId) {
        // jdbc.update returns affected rows, not the version or number of columns.
        if (jdbc.update("DELETE FROM order_line WHERE order_id=? AND product_id=?", id, productId)
                == 0)
            // Zero rows means there was no line with those two ids: it returns 404.
            throw new BusinessException(
                    HttpStatus.NOT_FOUND, "ORDER_LINE_NOT_FOUND", "Order line not found");
        // Recalculate the order after you withdraw your line.
        recalculate(id);
    }

    // The price per quantity of all the lines; without lines the total is zero.
    private void recalculate(long id) {
        BigDecimal amount =
                jdbc.queryForObject(
                        "SELECT coalesce(sum(quantity*unit_price),0) FROM order_line WHERE"
                            + " order_id=?",
                        BigDecimal.class,
                        id);
        // The total remains and the version increases to point out that the order changed.
        jdbc.update(
                "UPDATE sales_order SET total=?,version=version+1,updated_at=now() WHERE id=?",
                amount,
                id);
    }

    // Save the status and shipping data; the transitional rules are in the service.
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

    // Search for orders with optional filters and load your lines in lots.
    public PageResponse<OrderDtos.Order> search(
            Long customerId, String status, int page, int size, String sort) {
        // It only supports these known orders: it does not concatea free column name of the user.
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
        // WHERE 1 = 1 allows you to add AND filters without checking which one is the first.
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        // Collect parameters in the order of their SQL placeholders.
        List<Object> args = new ArrayList<>();
        // Add the filter per customer only when provided.
        if (customerId != null) {
            where.append(" AND o.customer_id=?");
            args.add(customerId);
        }
        // If it was sent status, check that it belongs to the admitted list.
        if (status != null) {
            // Rejects unknown values before running SQL.
            if (!List.of("DRAFT", "CONFIRMED", "COMPLETED", "CANCELLED").contains(status))
                throw new BusinessException(
                        HttpStatus.BAD_REQUEST, "INVALID_STATUS", "Unknown order status");
            // Add the state as a parameter, not as a concatenated SQL text.
            where.append(" AND o.status=?");
            args.add(status);
        }
        // It counts orders that meet the filters before applying LIMIT and OFSET.
        long count =
                jdbc.queryForObject(
                        "SELECT count(*) FROM sales_order o" + where, Long.class, args.toArray());
        // size limits rows; page * size omits previous pages (page starts at zero).
        args.add(size);
        args.add((long) page * size);
        // See the headers of this page along with the customer name.
        var headers =
                jdbc.query(
                        "SELECT o.*,c.name AS customer_name FROM sales_order o JOIN customer c ON"
                            + " c.id=o.customer_id"
                                + where
                                + " ORDER BY "
                                + orderBy
                                + " LIMIT ? OFFSET ?",
                        // Each row is transformed into Header; the lines are loaded in the next consultation.
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
        // If there are no headers, it avoids running an empty IN.
        if (headers.isEmpty())
            // Returns empty content but keeps the total to report the pagation.
            return new PageResponse<>(
                    List.of(), page, size, count, (int) ((count + size - 1) / size));
        // It generates an? per order; the ids continue to pass as JDBC parameters.
        String placeholders = String.join(",", java.util.Collections.nCopies(headers.size(), "?"));
        // It keeps the order id next to each line to be able to group them later.
        record OrderLine(long orderId, OrderDtos.Line line) {}
        // Load all the lines of the page into a query, avoiding a query on request.
        var lineRows =
                jdbc.query(
                        "SELECT l.*,p.sku,p.name FROM order_line l JOIN product p ON"
                            + " p.id=l.product_id WHERE l.order_id IN ("
                                + placeholders
                                + ") ORDER BY l.order_id,l.product_id",
                        (rs, n) -> {
                            // Read price and quantity once to calculate the total line.
                            BigDecimal price = rs.getBigDecimal("unit_price");
                            long quantity = rs.getLong("quantity");
                            // Build the line with product and order data.
                            var line =
                                    new OrderDtos.Line(
                                            rs.getLong("id"),
                                            rs.getLong("product_id"),
                                            rs.getString("sku"),
                                            rs.getString("name"),
                                            quantity,
                                            price,
                                            price.multiply(BigDecimal.valueOf(quantity)));
                            // It associates the line to the order that contains it.
                            return new OrderLine(rs.getLong("order_id"), line);
                        },
                        // The head ids fill in the IN's? in the same order.
                        headers.stream().map(Header::id).toArray());
        // Group lines by orderId to assemble each order.
        Map<Long, List<OrderDtos.Line>> grouped =
                lineRows.stream()
                        .collect(
                                Collectors.groupingBy(
                                        OrderLine::orderId,
                                        Collectors.mapping(OrderLine::line, Collectors.toList())));
        // Unite each header with its lines and return the full page.
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
