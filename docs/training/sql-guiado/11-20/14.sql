SELECT e.id, e.dia, e.sku,
       (SELECT t.euros
        FROM sql_lab20.tarifas t
        WHERE t.sku = e.sku
        ORDER BY t.desde DESC
        LIMIT 1) AS tarifa
FROM sql_lab20.expediciones e
ORDER BY e.id;
