SELECT e.nombre AS estante, p.nombre AS pasillo, a.nombre AS almacen
FROM sql_lab20.zonas e
JOIN sql_lab20.zonas p ON p.id = e.padre_id
JOIN sql_lab20.zonas a ON a.id = e.padre_id
WHERE e.id IN (4, 5, 6) AND e.habilitada
ORDER BY e.id;
