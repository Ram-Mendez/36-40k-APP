# INC-32 · El inventario confunde un lote sin contar con un lote vacío

Dificultad estimada **7/10**. Usa las rutas del grupo **Traslados de almacén** en Swagger.

El lote `LOT-32-PENDING` tiene 8 unidades previstas y todavía no se ha contado. El lote `LOT-32-EMPTY` también tiene 8 unidades previstas y el operario contó 0.

1. Ejecuta `GET /api/v1/warehouse-transfers/lots/{lot}/count` con `lot = LOT-32-PENDING`.
2. Repite el GET con `lot = LOT-32-EMPTY`.

**Fallo observado.** Ambos devuelven `countedUnits = 0`.

**Para darlo por resuelto.** El lote pendiente debe devolver `countedUnits = null`. El lote contado debe seguir devolviendo `countedUnits = 0`. Ambos deben mantener `expectedUnits = 8`.

Pon el primer breakpoint en [`TransferPracticeService.count`](../../../src/main/java/com/acme/operations/warehouse/TransferPracticeService.java).
