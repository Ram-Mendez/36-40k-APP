SELECT centro, SUM(bruto - descuento) AS euros_netos
FROM sql_lab20.expediciones
GROUP BY centro
ORDER BY centro;
