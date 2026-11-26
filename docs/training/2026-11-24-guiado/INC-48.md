# INC-48 · El cierre da por preparado un lote con un bulto sin pesar

## Qué tiene que pasar

La respuesta correcta contiene `ready = false`, `parcels = 2`, `passed = 1`.

## Reproduce el fallo

En **Conciliación de almacén**, ejecuta `GET /api/v1/warehouse-reconciliation/quality/{batch}` con `batch = QC-48`. La respuesta actual muestra `ready = true`, `parcels = 2`, `passed = 1`.
`ready = false`, `parcels = 2`, `passed = 1`.
{
"ready": true,
"parcels": 2,
"passed": 1
}

## Depura paso a paso

1. Detente en `quality`. La condición para un bulto exige que esté sellado y pesado. P1 cumple las dos comprobaciones. P2 tiene el sello, pero le falta el peso.
2. Comprueba primero que el SQL devuelve `parcels = 2` y `passed = 1`. Si esos datos son correctos, sigue solo la expresión que construye `ready`.
3. Pregunta cuántos bultos deben pasar para cerrar el lote. Haz la prueba mental con un lote vacío y con un lote en el que todos pasan.

[Abre `WarehouseReconciliationService.quality`](../../../src/main/java/com/acme/operations/reconciliation/WarehouseReconciliationService.java)

## Comprueba tu arreglo

`QC-48` devuelve `ready = false` y conserva los recuentos. `QC-48-OK` devuelve `ready = true` con un bulto aprobado. `QC-48-EMPTY` devuelve `ready = false` y ambos recuentos en 0.
