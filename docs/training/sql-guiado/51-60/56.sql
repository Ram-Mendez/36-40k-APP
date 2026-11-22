-- Práctica: AuditController.list(...).
-- Eventos del producto cuyo identificador es 1.
SELECT id, entity_type, entity_id, action
FROM sql_backend_51_60.audit_event
WHERE entity_id = '1'
AND enti
ORDER BY id;
