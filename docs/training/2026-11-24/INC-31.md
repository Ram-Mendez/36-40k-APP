# INC-31 · La báscula rechaza el peso cuando cambia el número de decimales

Dificultad estimada **7/10**. Usa las rutas del grupo **Traslados de almacén** en Swagger.

La báscula confirma el peso previsto del traslado. El peso guardado para el traslado `TR-31` es 12,500 kg. Otra báscula envía el mismo valor con dos decimales.

1. Ejecuta `POST /api/v1/warehouse-transfers/{code}/weight` con `code = TR-31` y el JSON `{"kilograms":12.50}`.
2. Repite el POST con `{"kilograms":12.500}`.

**Fallo observado.** El primer POST devuelve `409 WEIGHT_MISMATCH` y el segundo devuelve 200.

**Para darlo por resuelto.** Usa `TR-31-B`. Ambos JSON deben devolver 200. El valor `12.501` debe seguir devolviendo 409.

Pon el primer breakpoint en [`TransferPracticeService.confirmWeight`](../../../src/main/java/com/acme/operations/warehouse/TransferPracticeService.java).
