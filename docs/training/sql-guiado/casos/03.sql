-- Products with at least two units sold across completed orders.
SELECT l.producto_id, sum(l.unidades) AS unidades_vendidas
FROM sql_lab10.lineas AS l
JOIN sql_lab10.pedidos AS p ON p.id = l.pedido_id
WHERE p.estado = 'COMPLETED' AND l.unidades >= 2
GROUP BY l.producto_id
ORDER BY l.producto_id;
