SELECT id, centro, estado
FROM sql_lab20.expediciones
WHERE centro = 'NORTE' AND estado = 'LISTA' OR estado = 'URGENTE'
ORDER BY id;
