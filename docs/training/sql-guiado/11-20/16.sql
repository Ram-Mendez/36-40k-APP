SELECT sku, COUNT(*) AS unidades_recibidas
FROM sql_lab20.lotes
GROUP BY sku
ORDER BY sku;
