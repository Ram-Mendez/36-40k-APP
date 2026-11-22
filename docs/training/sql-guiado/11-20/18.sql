SELECT persona, bultos
FROM sql_lab20.turnos
WHERE centro = 'SUR'
ORDER BY bultos ASC
LIMIT 2;
