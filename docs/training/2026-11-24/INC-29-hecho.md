# INC-29 · PATCH no borra la referencia del traslado

Dificultad estimada **6/10 con la pista de este ticket**. Usa las rutas del grupo **Traslados de almacén** en Swagger.

**Pista.** Abre el depurador y mira el valor de `reference` en ambos PATCH. Comprueba si el objeto JSON contiene la clave `externalReference` en cada caso.

El usuario debe poder borrar una referencia externa con `null`. Si omite el campo, la aplicación debe conservar la referencia.

1. Ejecuta `GET /api/v1/warehouse-transfers/{code}` con `code = TR-29`. La referencia inicial es `ALB-ORIGINAL`.
2. {
   "code": "TR-29",
   "externalReference": "ALB-ORIGINAL",
   "revision": 0,
   "status": "DRAFT"
   }
2. En `PATCH /api/v1/warehouse-transfers/{code}` para `TR-29`, envía primero `{}` y comprueba que la referencia sigue igual.
3. En el mismo PATCH, envía `{"externalReference":null}`. Vuelve a consultar el GET.

**Fallo observado.** El PATCH responde 200 y el GET sigue mostrando `ALB-ORIGINAL`.

**Para darlo por resuelto.** El PATCH con `{}` debe conservar la referencia. El PATCH con `null` debe borrarla. El GET posterior debe mostrar `externalReference = null`.

Pon el primer breakpoint en [`TransferPracticeService.patch`](../../../src/main/java/com/acme/operations/warehouse/TransferPracticeService.java).
