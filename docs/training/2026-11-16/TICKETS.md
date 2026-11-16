# Incidencias del 16/11/2026

Cinco ejercicios independientes. Los datos de cada test se crean de nuevo al ejecutarlo. No hay que preparar pedidos, recepciones, devoluciones ni facturas. Código y contratos de API conservan sus nombres en inglés.

Los tests son el punto de entrada. Los ejemplos HTTP describen el contrato que comprueban; no tienes que lanzar esas peticiones contra tu aplicación habitual.

## 01 — El envío gratis falla justo en el límite

**Tema:** Java, condiciones y cantidades decimales. **Dificultad:** corta. **Tiempo estimado:** 15–25 minutos.

**Contexto:** el servicio de envíos calcula cuánto cuesta enviar un pedido. La tarifa es 7,50 € para importes inferiores a 200 €. A partir de 200 €, incluido ese importe, el envío es gratis. Cotizar solo calcula el precio; no crea un envío ni necesita un pedido guardado.

**Aviso de soporte:** un cliente compra exactamente 200 € y la cotización añade 7,50 € de envío. Si compra 200,01 €, ya sale gratis.

**Reproducción:** ejecuta el ticket `01`. El test llama al cálculo y también al endpoint HTTP del servicio, que arranca en un puerto libre.

```http
GET /quotes?orderId=42&amount=200.00
```

`orderId=42` es una referencia de la cotización. No debes crear un pedido con ese ID. `amount=200.00` es el importe de la compra.

| `amount` | `cost` esperado |
| --- | --- |
| `199.99` | `7.50` |
| `200.00` | `0.00` |
| `200.01` | `0.00` |

**Aceptación:** los tres casos funcionan, HTTP sigue devolviendo 200 y se conserva la referencia. La regla también respeta una configuración distinta: coste 12,30 € y envío gratis desde 100 €. `100` y `100.00` deben producir la misma cotización.

**Tests:** `services/shipping-service/src/test/java/com/acme/shipping/QuoteServiceTest.java` y `QuoteHttpTest.java` en el mismo directorio.

## 02 — Se acepta un correo sin arroba

**Tema:** validación de entrada en REST. **Dificultad:** corta/media. **Tiempo estimado:** 20–35 minutos.

**Contexto:** una persona de atención al cliente registra el correo al que enviará las comunicaciones. La API debe rechazar un correo mal formado antes de llamar al servicio de clientes.

**Aviso de soporte:** este cuerpo se acepta, aunque `email=ana.example.test` no contiene arroba:

```http
POST /api/v1/customers
Content-Type: application/json

{"name":"Batch 16 customer","taxId":"BATCH16-CUSTOMER","email":"ana.example.test","address":"16 Test Street"}
```

**Reproducción:** ejecuta el ticket `02`. Se usa el controlador real y la validación real. El servicio de clientes se sustituye por una respuesta de prueba: no se escribe en ninguna base de datos. Los permisos se comprueban por separado en el ticket 03.

**Aceptación:** el correo anterior devuelve `400`, `code=VALIDATION_ERROR` y una infracción del campo `email`; el servicio de clientes no llega a ejecutarse. `email=ana@example.test` devuelve `201` y `Location=/api/v1/customers/42`. Un correo vacío también se rechaza. No se exige comprobar si el buzón existe.

**Test:** `src/test/java/com/acme/operations/batch16/Batch16CustomerValidationTest.java`.

## 03 — Operaciones no puede consultar ventas

**Tema:** autorización con Spring Security. **Dificultad:** media. **Tiempo estimado:** 30–45 minutos.

**Contexto:** OPERATIONS es el personal que gestiona la operativa diaria. Necesita consultar el informe de ventas. READONLY solo consulta y ADMIN administra. Los tres roles tienen permiso de lectura sobre este informe.

**Aviso de soporte:** el usuario de OPERATIONS recibe `403 ACCESS_DENIED` al abrir `GET /api/v1/reports/sales`. ADMIN y READONLY pueden abrirlo.

**Reproducción:** ejecuta el ticket `03`. Cada caso aporta una identidad de prueba a la cadena real de seguridad. El informe está vacío de forma deliberada: el objetivo es comprobar si se permite entrar, no calcular ventas.

| Identidad | Respuesta esperada |
| --- | --- |
| `ADMIN` | `200` |
| `OPERATIONS` | `200` |
| `READONLY` | `200` |
| Rol ajeno `OUTSIDE` | `403` |
| Sin autenticación | `401` |

**Aceptación:** se cumple la tabla. Un acceso rechazado no llama al servicio de informes. La corrección conserva la autenticación y los controles de permisos; no debe convertir el informe en una ruta pública.

**Test:** `src/test/java/com/acme/operations/batch16/Batch16ReportPermissionsTest.java`.

## 04 — El listado dice que no hay segunda página

**Tema:** colecciones y metadatos de paginación. **Dificultad:** corta/media. **Tiempo estimado:** 20–35 minutos.

**Contexto:** la pantalla utiliza `totalPages` para saber cuántas páginas puede mostrar. Los números de página empiezan en cero. Si hay 21 resultados y caben 20 por página, existen la página `0` y la página `1`.

**Aviso de soporte:** la respuesta contiene 20 elementos y `totalElements=21`, pero anuncia `totalPages=1`. El último elemento existe: la pantalla no ofrece la segunda página porque el total está mal.

**Reproducción:** ejecuta el ticket `04`. El test proporciona 21 elementos y sus totales mediante una página en memoria. No ejecuta SQL.

| `totalElements` | `size` | `totalPages` esperado |
| --- | --- | --- |
| `0` | `20` | `0` |
| `1` | `20` | `1` |
| `20` | `20` | `1` |
| `21` | `20` | `2` |
| `40` | `20` | `2` |

**Aceptación:** la tabla se cumple. La segunda página conserva el elemento 21, `page=1`, `totalElements=21` y `totalPages=2`. La corrección no cambia el contenido ni el tamaño solicitado.

**Test:** `src/test/java/com/acme/operations/batch16/Batch16PaginationTest.java`.

## 05 — Se pierde el identificador entre servicios

**Tema:** comunicación HTTP y trazabilidad. **Dificultad:** media. **Tiempo estimado:** 35–50 minutos.

**Contexto:** `X-Request-ID` es una cabecera HTTP que permite relacionar los logs de una misma petición entre servicios. No es el ID del pedido ni un token de acceso. El backend recibe ese valor y debe enviarlo también al servicio que cotiza el envío.

**Aviso de soporte:** el backend devuelve una cotización válida, pero el servicio remoto no recibe `X-Request-ID`. Resulta difícil relacionar sus logs con la petición original.

**Reproducción:** ejecuta el ticket `05`. El test pasa `X-Request-ID=batch16-order-42` por el filtro real y solicita una cotización mediante el cliente HTTP real. Un servidor local de prueba captura las cabeceras. No se completa ningún pedido ni se consume stock.

**Esperado:** la respuesta del backend y la petición que recibe el servidor remoto comparten `X-Request-ID=batch16-order-42`. Si el cliente inicial no manda esa cabecera, el backend genera un identificador y propaga ese mismo valor.

**Aceptación:** ambos casos conservan el identificador. La cotización sigue teniendo `reference=BATCH16-QUOTE` y `cost=7.50`. Se envían `orderId=42` y `amount=19.90` sin perder decimales. Al terminar se limpia el contexto de logs de la petición. Los controles previos del cliente HTTP continúan pasando.

**Tests:** `src/test/java/com/acme/operations/batch16/Batch16TracePropagationTest.java` y `src/test/java/com/acme/operations/ShippingClientTest.java`.
