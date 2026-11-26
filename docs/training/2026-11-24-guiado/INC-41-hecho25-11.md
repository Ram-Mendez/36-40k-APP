# INC-41 · La preparación FIFO asigna menos unidades de las pedidas

## Qué tiene que pasar

La respuesta correcta contiene `allocated` por lote devuelve `[4,3,0]`.

## Reproduce el fallo
[
{
"code": "L41-A",
"units": 4,
"cumulativeUnits": 4,
"allocated": 3
},
{
"code": "L41-B",
"units": 5,
"cumulativeUnits": 9,
"allocated": 0
},
{
"code": "L41-C",
"units": 6,
"cumulativeUnits": 15,
"allocated": 0
}
]
En **Conciliación de almacén**, ejecuta `GET /api/v1/warehouse-reconciliation/fifo/{sku}` con `sku = SKU-41`, `requested = 7`. La respuesta actual muestra `allocated` por lote devuelve `[3,0,0]`.

## Depura paso a paso

1. Detente en `fifo`. El pedido pide 7 unidades. Los tres lotes aportan 4, 5 y 6 unidades, en ese orden.
2. Lee el CTE `ordered`. El acumulado que incluye el lote actual vale 4, 9 y 15. Comprueba esos números en `cumulativeUnits` antes de investigar `allocated`.
3. En la expresión `allocated`, sigue solo la primera fila. Justo antes del primer lote aún faltan 7 unidades. Identifica qué acumulado está usando la consulta para descontar unidades ya asignadas.
4. Repite el razonamiento con el segundo lote. Después de usar las 4 unidades del primero, solo faltan 3. Conserva el orden de los lotes y la columna que informa del acumulado.

[Abre `WarehouseReconciliationService.fifo`](../../../src/main/java/com/acme/operations/reconciliation/WarehouseReconciliationService.java)

## Comprueba tu arreglo

Con 7 unidades espera `[4,3,0]`. Con 4 espera `[4,0,0]`. Con 20 espera `[4,5,6]`, porque solo hay 15 disponibles.
