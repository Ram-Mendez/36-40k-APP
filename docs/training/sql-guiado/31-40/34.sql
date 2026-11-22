WITH pedidos(id, servidas, solicitadas) AS (VALUES (1,1,2),(2,3,4))
SELECT id, ROUND((servidas / solicitadas * 100)::numeric, 2) AS porcentaje FROM pedidos ORDER BY id;
