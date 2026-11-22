-- Práctica: OrderRepository.recalculate(orderId).
-- Comprobamos el cálculo para dos pedidos, uno de ellos sin líneas.
SELECT o.id AS order_id,
       (SELECT SUM(l.quantity * l.unit_price)
        FROM sql_backend_41_50.order_line AS l
        WHERE l.order_id = o.id) AS calculated_total
FROM sql_backend_41_50.sales_order AS o
WHERE o.id IN (101, 107)
ORDER BY o.id;
