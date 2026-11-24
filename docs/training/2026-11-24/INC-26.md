# INC-26 · El traslado sale y el consumidor nunca recibe el aviso

Dificultad estimada **8/10**. Usa las rutas del grupo **Traslados de almacén** en Swagger.

El almacén confirma el envío. El sistema debe dejar un aviso guardado para que otro proceso pueda comunicar la salida al destino.

1. Ejecuta `GET /api/v1/warehouse-transfers/{code}` con `code = TR-26` y comprueba que el traslado está en `DRAFT`.
2. Ejecuta `POST /api/v1/warehouse-transfers/{code}/dispatch` con `code = TR-26`. Este POST no lleva cuerpo. La respuesta muestra `status = DISPATCHED`.
3. Ejecuta `GET /api/v1/warehouse-transfers/{code}/dispatch-events` para `TR-26`.

**Fallo observado.** El GET devuelve `[]` aunque el traslado ya salió.

**Para darlo por resuelto.** Usa el traslado nuevo `TR-26-B`. Después del POST, el GET devuelve un aviso `TRANSFER_DISPATCHED`. Si vuelves a ejecutar el POST con el mismo traslado, debes recibir 409 y el GET debe seguir mostrando un solo aviso.

Pon el primer breakpoint en [`TransferPracticeService.dispatch`](../../../src/main/java/com/acme/operations/warehouse/TransferPracticeService.java).
