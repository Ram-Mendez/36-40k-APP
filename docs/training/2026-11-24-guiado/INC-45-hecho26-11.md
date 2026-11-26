# INC-45 · Dos muelles comparten la misma lista de transportistas

## Qué tiene que pasar

La respuesta correcta contiene A muestra `[NORTE,SUR]` y B muestra `[CENTRO]`.

## Reproduce el fallo

En **Conciliación de almacén**, ejecuta `GET /api/v1/warehouse-reconciliation/dock-visits/{batch}` con `batch = VIS-45`. La respuesta actual muestra ambos muelles muestran `[NORTE,SUR,CENTRO]`.
[
{
"dock": "A",
"carriers": [
"NORTE",
"SUR",
"CENTRO"
]
},
{
"dock": "B",
"carriers": [
"NORTE",
"SUR",
"CENTRO"
]
}
]
## Depura paso a paso

1. Detente en `dockVisits` después de `jdbc.query`. Comprueba que el SQL devuelve `A/NORTE`, `A/SUR` y `B/CENTRO`.
2. Avanza hasta acabar el `for` y mira las entradas de `byDock` antes de crear los DTO. Si la mezcla ya aparece ahí, descarta el `map` que construye la respuesta.
3. Vuelve a ejecutar y detente en el `for` cuando llega la fila de B. Compara las referencias de las listas que reciben A y B, además de su contenido. Fíjate en qué objeto devuelve `computeIfAbsent` para una clave nueva.

[Abre `WarehouseReconciliationService.dockVisits`](../../../src/main/java/com/acme/operations/reconciliation/WarehouseReconciliationService.java)

## Comprueba tu arreglo

El GET debe devolver exactamente dos muelles. A conserva `NORTE` y `SUR`. B solo contiene `CENTRO`.
