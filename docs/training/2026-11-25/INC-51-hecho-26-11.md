# INC-51 · La desviación cambia de porcentaje según el sentido del error

## Qué tiene que pasar

El almacén compara todo recuento con el número de unidades previsto. Si esperaba 120 y faltan o sobran 30, la desviación tiene la misma magnitud en ambos casos.

## Reproduce el fallo

Ejecuta `GET /api/v1/warehouse-controls/counts/{code}/discrepancy` con `code = CNT-51`. La API devuelve `expected = 120`, `counted = 90` y `discrepancyPercent = 33.33`.
{
"expected": 120,
"counted": 90,
"discrepancyPercent": 33.33
}
Debería devolver 25.00.

## Depura paso a paso

1. Pon un breakpoint en `WarehouseDailyController.discrepancy`. En la función que lee la fila de PostgreSQL, comprueba `expected` y `counted`.
2. Calcula la diferencia a mano. Faltan 30 unidades. El cálculo debe expresar qué parte representan esas 30 del número previsto.
3. Revisa el valor que recibe `divide`. Repite la inspección con `CNT-51-OVER`, que cuenta 150 unidades, y compara ambos resultados.

## Comprueba tu arreglo

`CNT-51` y `CNT-51-OVER` devuelven `discrepancyPercent = 25.00`. `CNT-51-ZERO` devuelve `null` porque no existe una cantidad prevista sobre la que calcular el porcentaje.

[WarehouseDailyController.java](../../../src/main/java/com/acme/operations/reconciliation/WarehouseDailyController.java)
