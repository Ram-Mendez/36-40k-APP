# INC-29 · PATCH no borra la referencia del traslado

Dificultad estimada **7/10**. Usa las rutas del grupo **Traslados de almacén** en Swagger.

El usuario debe poder borrar una referencia externa con `null`. Si omite el campo, la aplicación debe conservar la referencia.

1. Ejecuta `GET /api/v1/warehouse-transfers/{code}` con `code = TR-29`. La referencia inicial es `ALB-ORIGINAL`.
2. En `PATCH /api/v1/warehouse-transfers/{code}` para `TR-29`, envía primero `{}` y comprueba que la referencia sigue igual.
3. En el mismo PATCH, envía `{"externalReference":null}`. Vuelve a consultar el GET.

**Fallo observado.** El PATCH responde 200 y el GET sigue mostrando `ALB-ORIGINAL`.

**Para darlo por resuelto.** El PATCH con `{}` debe conservar la referencia. El PATCH con `null` debe borrarla. El GET posterior debe mostrar `externalReference = null`.

Pon el primer breakpoint en [`TransferPracticeService.patch`](../../../src/main/java/com/acme/operations/warehouse/TransferPracticeService.java).
