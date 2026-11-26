# INC-32 · El inventario confunde un lote sin contar con un lote vacío

Dificultad estimada **6/10 con la pista de este ticket**. Usa las rutas del grupo **Traslados de almacén** en Swagger.

**Pista.** Mira primero qué devuelve PostgreSQL en `counted_units` para los dos lotes. Después comprueba qué valor le asigna Java al construir la respuesta.

El lote `LOT-32-PENDING` tiene 8 unidades previstas y todavía no se ha contado. El lote `LOT-32-EMPTY` también tiene 8 unidades previstas y el operario contó 0.

1. Ejecuta `GET /api/v1/warehouse-transfers/lots/{lot}/count` con `lot = LOT-32-PENDING`.
2. {
   "lot": "LOT-32-PENDING",
   "expectedUnits": 8,
   "countedUnits": 0
   }
2. Repite el GET con `lot = LOT-32-EMPTY`.
3. {
   "lot": "LOT-32-EMPTY",
   "expectedUnits": 8,
   "countedUnits": 0
   }

**Fallo observado.** Ambos devuelven `countedUnits = 0`.

**Para darlo por resuelto.** El lote pendiente debe devolver `countedUnits = null`. El lote contado debe seguir devolviendo `countedUnits = 0`. Ambos deben mantener `expectedUnits = 8`.

Pon el primer breakpoint en [`TransferPracticeService.count`](../../../src/main/java/com/acme/operations/warehouse/TransferPracticeService.java).
