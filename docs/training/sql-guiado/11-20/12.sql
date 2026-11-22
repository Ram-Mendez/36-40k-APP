SELECT id, servidas, solicitadas,
       round((servidas / solicitadas) * 100, 2) AS porcentaje
FROM sql_lab20.expediciones
ORDER BY id;
