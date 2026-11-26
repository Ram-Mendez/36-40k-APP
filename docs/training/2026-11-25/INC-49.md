# INC-49 · La báscula descuenta dos veces la tara del palé

## Qué tiene que pasar

El palé lleva dos bultos de 50 y 40 kg. La tara del palé es 12 kg. La API debe descontar una sola tara del total de 90 kg.

## Reproduce el fallo

En **Controles de almacén**, ejecuta `GET /api/v1/warehouse-controls/pallets/{code}/weight` con `code = PAL-49`. La API devuelve `grossKg = 90`, `tareKg = 12` y `netKg = 66`. El peso neto debería ser 78.

## Depura paso a paso

1. Pon un breakpoint en `WarehouseDailyController.weight` y comprueba que `code` vale `PAL-49`.
2. Lee la consulta del peso. La tabla `pallet` aporta la tara y `pallet_parcel` aporta dos filas de peso. Anota qué valor de tara queda en cada fila tras el JOIN.
3. Revisa las expresiones del SELECT que calculan `grossKg` y `netKg`. El peso bruto ya cuadra. Sigue cuántas restas de 12 hace la segunda expresión.

## Comprueba tu arreglo

Con `PAL-49` espera `netKg = 78`. Con `PAL-49-SINGLE`, que tiene un solo bulto de 50 kg, espera `netKg = 38` y `tareKg = 12`.

[WarehouseDailyController.java](../../../src/main/java/com/acme/operations/reconciliation/WarehouseDailyController.java)
