# INC-36 · El camión admite una carga que supera uno de sus límites

Dificultad estimada **5/10**.

El camión puede llevar como máximo 20 bultos y 100 kg. La carga debe cumplir ambos límites.

1. Ejecuta `POST /api/v1/warehouse-transfers/{code}/preparation/capacity` con `code = TR-31` y `{"parcels":21,"kilograms":80}`.
2. Repite con `{"parcels":10,"kilograms":101}`.

**Fallo observado.** La API devuelve `fits = true` en las dos peticiones.

**Resultado esperado.** Las dos peticiones devuelven `fits = false`. Con `{"parcels":20,"kilograms":100}` devuelve `fits = true`.

Pon un breakpoint en `WarehousePreparationService.fits`. Anota qué comparación se cumple y cuál falla en cada petición.

[WarehousePreparationService.java](../../../src/main/java/com/acme/operations/warehouse/WarehousePreparationService.java)
