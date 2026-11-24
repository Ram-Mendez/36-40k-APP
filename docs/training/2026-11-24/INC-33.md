# INC-33 · El cliente conserva datos antiguos después de editar el traslado

Dificultad estimada **8/10**. Usa las rutas del grupo **Traslados de almacén** en Swagger.

La aplicación móvil guarda la respuesta de un traslado y envía su cabecera `ETag` en la siguiente consulta. El backend debe responder 200 con los datos nuevos cuando haya cambiado el traslado.

1. Ejecuta `GET /api/v1/warehouse-transfers/{code}` con `code = TR-33` y copia la cabecera `etag` con sus comillas.
2. Ejecuta `PATCH /api/v1/warehouse-transfers/{code}` para `TR-33` con `{"externalReference":"REF-NUEVA-33"}`. Si ya usaste ese valor, escribe una referencia distinta.
3. Vuelve a ejecutar `GET /api/v1/warehouse-transfers/{code}` para `TR-33`. Pega el valor copiado en `If-None-Match`.

**Fallo observado.** El GET devuelve 304 aunque el PATCH cambió la referencia.

**Para darlo por resuelto.** Con el ETag anterior, el GET debe devolver 200, incluir la referencia nueva y emitir un ETag diferente. Un GET con el ETag nuevo debe devolver 304 mientras no haya más cambios.

Pon el primer breakpoint en [`TransferPracticeController.transfer`](../../../src/main/java/com/acme/operations/warehouse/TransferPracticeController.java).
