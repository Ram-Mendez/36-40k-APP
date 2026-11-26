# INC-33 · El cliente conserva datos antiguos después de editar el traslado

Dificultad estimada **8/10**. Usa las rutas del grupo **Traslados de almacén** en Swagger.

La aplicación móvil guarda la respuesta de un traslado y envía su cabecera `ETag` en la siguiente consulta. El backend debe responder 200 con los datos nuevos cuando haya cambiado el traslado.

1. Ejecuta `GET /api/v1/warehouse-transfers/{code}` con `code = TR-33` y copia la cabecera `etag` con sus comillas.
2. {
   "code": "TR-33",
   "externalReference": "ALB-ORIGINAL",
   "revision": 0,
   "status": "DRAFT"
   }
   cache-control: no-cache,no-store,max-age=0,must-revalidate
   connection: keep-alive
   content-type: application/json
   date: Wed,25 Nov 2026 05:52:55 GMT
   etag: "TR-33"
   expires: 0
   keep-alive: timeout=60
   pragma: no-cache
   transfer-encoding: chunked
   x-content-type-options: nosniff
   x-frame-options: DENY
   x-request-id: ebb4ecc0-648f-4dd1-aa99-7884031152c3
   x-xss-protection: 0
2. Ejecuta `PATCH /api/v1/warehouse-transfers/{code}` para `TR-33` con `{"externalReference":"REF-NUEVA-33"}`. Si ya usaste ese valor, escribe una referencia distinta.
3. {
   "code": "TR-33",
   "externalReference": "REF-NUEVA-33",
   "revision": 1,
   "status": "DRAFT"
   }
3. Vuelve a ejecutar `GET /api/v1/warehouse-transfers/{code}` para `TR-33`. Pega el valor copiado en `If-None-Match`.

**Fallo observado.** El GET devuelve 304 aunque el PATCH cambió la referencia.

**Para darlo por resuelto.** Con el ETag anterior, el GET debe devolver 200, incluir la referencia nueva y emitir un ETag diferente. Un GET con el ETag nuevo debe devolver 304 mientras no haya más cambios.

Pon el primer breakpoint en [`TransferPracticeController.transfer`](../../../src/main/java/com/acme/operations/warehouse/TransferPracticeController.java).
