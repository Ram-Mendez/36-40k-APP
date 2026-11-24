# INC-25 · El rechazo de una inspección desaparece del historial

Dificultad estimada **8/10**. Usa las rutas del grupo **Traslados de almacén** en Swagger.

El almacén exige guardar cada inspección rechazada. La petición debe devolver el rechazo y el historial debe conservar el intento.

1. Ejecuta `GET /api/v1/warehouse-transfers/{code}/inspections` con `code = TR-25`. El historial empieza vacío.
2. Ejecuta `POST /api/v1/warehouse-transfers/{code}/inspection` con `code = TR-25` y el cuerpo `{"passed":false}`.
3. Comprueba que el POST devuelve `409 INSPECTION_REJECTED`. Repite el GET inmediatamente.

**Fallo observado.** El GET sigue devolviendo `[]`.

**Para darlo por resuelto.** El POST sigue devolviendo 409 y el traslado sigue en `DRAFT`. El GET muestra un registro nuevo con `result = REJECTED` por cada intento rechazado.

Pon el primer breakpoint en [`TransferPracticeService.inspect`](../../../src/main/java/com/acme/operations/warehouse/TransferPracticeService.java).
