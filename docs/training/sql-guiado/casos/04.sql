-- Pages start at 0. Change the first number to request another page.
WITH parametros(pagina, tamano) AS (VALUES (1, 2))
SELECT p.id, p.creado
FROM sql_lab10.pedidos AS p
ORDER BY p.creado DESC, p.id ASC
LIMIT (SELECT tamano FROM parametros)
OFFSET (SELECT pagina FROM parametros);
