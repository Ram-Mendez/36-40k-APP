# 22 de noviembre · Incidencias de Java y PostgreSQL

Los fallos están preparados en el código de esta rama. Los datos de cada incidencia ya están en `lab_20261117`, puerto `35690`.

## Cómo trabajar

1. Abre `36-40k-APP` en IntelliJ. En Docker Desktop, mantén `business-operations-batch17` arrancado.
2. Ejecuta **Shipping APP** y **Backend APP** en Debug.
3. Abre [Swagger](http://localhost:38088/swagger-ui/index.html). En `POST /api/v1/auth/login` usa `admin` / `DevPassword123!` y copia `accessToken` en **Authorize**.
4. Abre una incidencia de la lista. Sigue sus pasos en Swagger, pon el breakpoint indicado y corrige el código Java o SQL del backend.
5. Reinicia **Backend APP** y repite la comprobación indicada en esa incidencia.

Las incidencias que escriben datos incluyen un segundo juego preparado para comprobar el arreglo. Los IDs pertenecen a esta base: si la recreas, dejarán de ser válidos.

## Incidencias

| Ticket | Síntoma |
| --- | --- |
| [INC-01](INC-01.md) | Al paginar pedidos desaparece una línea. |
| [INC-02](INC-02.md) | El abono de una devolución parcial es demasiado alto. |
| [INC-03](INC-03.md) | Una entrega aumenta el stock previsto. |
| [INC-04](INC-04.md) | Reenviar un pago idéntico devuelve conflicto. |
| [INC-05](INC-05.md) | Una deuda aparece en un tramo incorrecto. |
| [INC-06](INC-06.md) | Una confirmación rechazada deja stock reservado. |
| [INC-07](INC-07.md) | Una compra se cierra tras la primera entrega. |
| [INC-08](INC-08.md) | Una factura antigua cambia al actualizar la dirección. |
| [INC-09](INC-09.md) | La propuesta muestra un proveedor inexistente. |
| [INC-10](INC-10.md) | Una confirmación falla mientras el stock está bloqueado. |
