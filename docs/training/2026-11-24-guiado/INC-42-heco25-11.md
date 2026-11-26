# INC-42 · El control de números de serie identifica mal el bulto sobrante

## Qué tiene que pasar

La respuesta correcta contiene `missing = [S2]` y `unexpected = [S3]`.

## Reproduce el fallo

En **Conciliación de almacén**, ejecuta `GET /api/v1/warehouse-reconciliation/serials/{manifest}` con `manifest = MF-42`. La respuesta actual muestra `missing = [S2]` y `unexpected = [S2]`.

{
"missing": [
"S2"
],
"unexpected": [
"S2"
]
}

## Depura paso a paso

1. Detente en `serialCheck`. El manifiesto esperaba `S1` y `S2`. El almacén escaneó `S1` y `S3`.
2. Entra en la primera llamada a `serialDifference`. Mira los parámetros `left` y `right`. Sin cambiar la consulta, lee qué filas conserva el primer SELECT después de `EXCEPT`.
3. Vuelve a `serialCheck` y compara los parámetros de la segunda llamada. Si la primera lista ya es correcta, céntrate en los datos que recibe la segunda.

[Abre `WarehouseReconciliationService.serialCheck`](../../../src/main/java/com/acme/operations/reconciliation/WarehouseReconciliationService.java)

## Comprueba tu arreglo

El GET debe devolver `S2` solo como faltante y `S3` solo como sobrante. `S1` debe quedar fuera de ambas listas.
