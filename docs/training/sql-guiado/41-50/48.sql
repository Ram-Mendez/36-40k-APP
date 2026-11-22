-- Práctica: parte del cálculo de estado de ProcurementRepository.updateReceiptStatus(id).
-- Se consulta el resultado para dos compras sin modificar sus estados.
SELECT o.id,
       CASE WHEN EXISTS (
           SELECT 1
           FROM sql_backend_41_50.purchase_order_line AS l
           WHERE l.received_quantity < l.quantity
       ) THEN 'PARTIALLY_RECEIVED' ELSE 'RECEIVED' END AS calculated_status
FROM sql_backend_41_50.purchase_order AS o
WHERE o.id IN (202, 204)
ORDER BY o.id;
