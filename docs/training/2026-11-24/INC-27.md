# INC-27 · Dos partes sin motivo crean dos saldos para el mismo producto

Dificultad estimada **8/10**. Usa las rutas del grupo **Traslados de almacén** en Swagger.

El almacén acumula las unidades dañadas por producto y motivo. Cuando el operario desconoce el motivo, todos los partes de ese producto deben acumularse juntos.

1. Ejecuta dos veces `POST /api/v1/warehouse-transfers/damage`. Envía primero `{"sku":"DAÑO-27-A","reasonCode":null,"units":2}` y después el mismo cuerpo con `units = 3`.
2. Ejecuta `GET /api/v1/warehouse-transfers/damage` con `sku = DAÑO-27-A`.

**Fallo observado.** Los dos POST devuelven 204. El GET devuelve dos filas con `reasonCode = null`, una con 2 unidades y otra con 3.

**Para darlo por resuelto.** Usa un SKU nuevo, por ejemplo `DAÑO-27-B`. El GET debe devolver una sola fila con `reasonCode = null` y `units = 5`. Si registras además 4 unidades con `reasonCode = PACKAGING`, esa cantidad debe quedar en una fila independiente.

Pon el primer breakpoint en [`TransferPracticeService.recordDamage`](../../../src/main/java/com/acme/operations/warehouse/TransferPracticeService.java).
