# INC-44 · El segundo pedido usa stock que ya recibió el primero

## Qué tiene que pasar

La respuesta correcta contiene `allocated = [6,4,3]` y `remaining.BOLT = 0`.

## Reproduce el fallo

En **Conciliación de almacén**, ejecuta `GET /api/v1/warehouse-reconciliation/allocation/{batch}` con `batch = PICK-44`. La respuesta actual muestra `allocated = [6,6,3]` y `remaining.BOLT = -2`.

{
"lines": [
{
"position": 1,
"sku": "BOLT",
"requested": 6,
"allocated": 6
},
{
"position": 2,
"sku": "BOLT",
"requested": 6,
"allocated": 6
},
{
"position": 3,
"sku": "TAPE",
"requested": 3,
"allocated": 3
}
],
"remaining": {
"BOLT": -2,
"TAPE": 2
}
}
## Depura paso a paso

1. Detente en `allocate` antes del `for`. Verifica `startingStock.BOLT = 10`, `startingStock.TAPE = 5` y las peticiones `6`, `6` y `3`.
2. Ejecuta una sola iteración. Después de asignar 6 tornillos, mira `remaining.BOLT`. Debe valer 4 en ese momento.
3. En la segunda iteración, detente antes de calcular `allocated`. Compara `available`, `startingStock.BOLT` y `remaining.BOLT`. Identifica cuál de esos valores debe limitar al segundo pedido.
4. Sigue también la tercera petición. Cinta y tornillos tienen saldos independientes. Comprueba que el problema ocurre cuando dos líneas usan la misma referencia.

[Abre `WarehouseReconciliationService.allocate`](../../../src/main/java/com/acme/operations/reconciliation/WarehouseReconciliationService.java)

## Comprueba tu arreglo

Las tres líneas deben indicar 6, 4 y 3 unidades asignadas. El saldo debe terminar en 0 para `BOLT` y 2 para `TAPE`.
