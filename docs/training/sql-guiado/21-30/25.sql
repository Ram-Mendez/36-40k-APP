SELECT l.sku, SUM(l.unidades * l.coste_unitario) AS coste
FROM sql_lab20.lotes l
JOIN sql_lab20.expediciones e ON e.sku = l.sku
WHERE e.centro = 'NORTE' AND e.dia BETWEEN '2024-12-30' AND '2025-01-06'
GROUP BY l.sku
ORDER BY l.sku;
