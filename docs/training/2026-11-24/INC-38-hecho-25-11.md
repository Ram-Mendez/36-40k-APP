# INC-38 · El reparto recomienda cargar más bultos de los que hay

Dificultad estimada **6/10**.

El operario reparte 10 bultos entre tres carros. La suma de las cargas debe ser 10. Si sobran bultos al dividir, los primeros carros reciben uno más.

1. Ejecuta `POST /api/v1/warehouse-transfers/{code}/preparation/cart-loads` con `code = TR-31`.
2. Envía `{"parcels":10,"carts":3}` y suma los valores de `parcelsPerCart`.

**Fallo observado.** La API devuelve `[4,4,4]`. La lista suma 12.

**Resultado esperado.** La lista es `[4,3,3]`. Con `{"parcels":9,"carts":3}` debe seguir devolviendo `[3,3,3]`. Con `{"parcels":2,"carts":3}` debe devolver `[1,1,0]`.

Pon un breakpoint en `WarehousePreparationService.cartLoads`. Comprueba cuántos bultos recibe cada carro antes de formar la lista.

[WarehousePreparationService.java](../../../src/main/java/com/acme/operations/warehouse/WarehousePreparationService.java)
