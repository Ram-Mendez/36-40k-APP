-- Bug del caso 01: esta consulta omite a los clientes sin pedidos.
-- Resultado esperado al corregirla: todos los clientes, con 0 si no tienen pedidos.
SELECT c.id, c.nombre, count(p.id) AS pedidos
FROM sql_lab10.clientes AS c
LEFT JOIN sql_lab10.pedidos AS p ON p.cliente_id = c.id
GROUP BY c.id, c.nombre
ORDER BY c.id;
