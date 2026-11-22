-- 06. Sales leaves the start date blank and requests completed orders
-- from before November 2. The end date excludes that day.
WITH limites(desde, hasta) AS (
    VALUES (CAST(NULL AS date), DATE '2026-11-02')
)
SELECT p.id, p.creado, p.total
FROM sql_lab10.pedidos AS p
CROSS JOIN limites AS l
WHERE p.estado = 'COMPLETED'
  AND p.creado >= l.desde
  AND (l.hasta IS NULL OR p.creado < l.hasta)
ORDER BY p.id;
