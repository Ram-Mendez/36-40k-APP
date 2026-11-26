# INC-39 · El saldo histórico ignora una salida registrada tarde

## Qué tiene que pasar

La respuesta correcta contiene `units = 6`.

## Reproduce el fallo

En **Conciliación de almacén**, ejecuta `GET /api/v1/warehouse-reconciliation/balance/{sku}` con `sku = SKU-39`, `cutoff = 2026-11-24T10:00:00Z`. La respuesta actual muestra `units = 10`.

## Depura paso a paso

1. Pon el breakpoint en `balance`. Ejecuta el GET. Comprueba que `cutoff` vale `2026-11-24T10:00:00Z` antes de entrar en `JdbcTemplate`.
2. Reconstruye el saldo a mano. `M39-1` suma 10 a las 08:00. `M39-2` resta 4 a las 09:45, aunque se registró a las 11:00. `M39-3` suma 8 a las 10:15.
3. Lee el `WHERE` de la consulta. Pregunta qué columna decide si `M39-2` entra en la suma. Si la consulta excluye esa fila, concentra el cambio en ese filtro.

[Abre `WarehouseReconciliationService.balance`](../../../src/main/java/com/acme/operations/reconciliation/WarehouseReconciliationService.java)

## Comprueba tu arreglo

A las `09:00Z` deben quedar 10 unidades. A las `10:00Z` deben quedar 6. A las `11:30Z` deben quedar 14. Usa la misma fecha en los tres GET.
