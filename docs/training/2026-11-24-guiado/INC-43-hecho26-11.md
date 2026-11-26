# INC-43 · El plan ofrece más kits de los que se pueden montar

## Qué tiene que pasar

La respuesta correcta contiene el campo superior `kits` devuelve `3`.

## Reproduce el fallo

En **Conciliación de almacén**, ejecuta `GET /api/v1/warehouse-reconciliation/kits/{kit}` con `kit = KIT-43`. La respuesta actual muestra el campo superior `kits` devuelve `8`.
{
"kits": 8,
"components": [
{
"component": "BASE",
"available": 10,
"requiredUnits": 2,
"kits": 5
},
{
"component": "HANDLE",
"available": 8,
"requiredUnits": 1,
"kits": 8
},
{
"component": "WHEEL",
"available": 12,
"requiredUnits": 4,
"kits": 3
}
]
}

## Depura paso a paso

1. Detente en `kitPlan` justo después de la consulta. Comprueba que `BASE`, `WHEEL` y `HANDLE` tienen 10, 12 y 8 unidades disponibles.
2. Revisa cuántas unidades necesita un kit. Cada kit usa 2 bases, 4 ruedas y 1 asa. Las tres filas deben indicar 5, 3 y 8 kits posibles por componente.
3. Si las tres filas son correctas, sigue solo el cálculo del campo superior `kits`. Pregunta qué componente se agota primero y qué valor de las tres filas usa Java.

[Abre `WarehouseReconciliationService.kitPlan`](../../../src/main/java/com/acme/operations/reconciliation/WarehouseReconciliationService.java)

## Comprueba tu arreglo

El GET debe conservar las tres filas con 5, 3 y 8. El campo superior `kits` debe valer 3.
