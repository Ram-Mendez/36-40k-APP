-- Práctica: ReportRepository.sales(...).
-- Informe de ventas completadas entre el 1 y el 2 de noviembre, incluidos.
SELECT c.id AS customer_id, c.name,
       COUNT(o.id) AS orders,
       SUM(o.total) AS total
FROM sql_backend_41_50.customer AS c
JOIN sql_backend_41_50.sales_order AS o ON o.customer_id = c.id
JOIN sql_backend_41_50.order_line AS l ON l.order_id = o.id
WHERE o.status = 'COMPLETED'
  AND o.updated_at >= TIMESTAMPTZ '2026-11-01 00:00:00+00'
  AND o.updated_at <  TIMESTAMPTZ '2026-11-03 00:00:00+00'
GROUP BY c.id, c.name
ORDER BY c.id;
