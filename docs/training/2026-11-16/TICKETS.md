# Incidencias del 16/11 — app en ejecución

Arranca **Shipping LAB 16-11** y **Backend LAB 16-11** con Debug, como indica [EMPIEZA-AQUI.md](../../../EMPIEZA-AQUI.md). Backend: `http://localhost:38087`. Shipping: `http://localhost:39097`.

Los cinco ejercicios son independientes. Puedes enviar las peticiones desde Swagger o desde [batch-2026-11-16.http](../../../http/batch-2026-11-16.http). Los tests quedan como comprobación final de cada corrección.

## 01 — El envío gratis falla justo en el límite

**Tema:** Java y cantidades decimales. **Tiempo estimado:** 15–25 minutos.

**Contexto:** enviar una compra de menos de 200 € cuesta 7,50 €. Desde 200 €, incluido ese importe, debe ser gratis. Cotizar solo calcula el precio: no necesitas crear un pedido.

**Reproducción:** abre estas tres direcciones en el navegador, con Shipping en Debug:

```text
http://localhost:39097/quotes?orderId=42&amount=199.99
http://localhost:39097/quotes?orderId=42&amount=200.00
http://localhost:39097/quotes?orderId=42&amount=200.01
```

`orderId=42` es una referencia para la cotización; el servicio no busca ese pedido en la base. `amount` es el importe de la compra.

**Fallo preparado:** para `amount=200.00`, recibes `cost=7.50`.

**Aceptación:** los costes son `7.50`, `0.00` y `0.00`, respectivamente. Se conserva `reference` y HTTP 200. Una tarifa configurada de 12,30 € y un umbral de 100 € también deben funcionar; `100` y `100.00` son equivalentes.

**Depuración:** la petición entra por `services/shipping-service/src/main/java/com/acme/shipping/QuoteController.java`. Sigue el recorrido del importe desde ahí.

## 02 — Se acepta un correo sin arroba

**Tema:** validación REST. **Tiempo estimado:** 20–35 minutos.

**Contexto:** atención al cliente registra un correo para enviar comunicaciones. El formato debe validarse antes de guardar al cliente.

**Reproducción:** inicia sesión como `admin`. En Swagger envía `POST /api/v1/customers`:

```json
{"name":"Batch 16 customer","taxId":"B16-EMAIL-1","email":"ana.example.test","address":"16 Test Street"}
```

**Fallo preparado:** devuelve `201` y guarda el correo sin arroba. Para repetir, usa un `taxId` nuevo, por ejemplo `B16-EMAIL-2`: el anterior ya existe. El archivo HTTP genera ese identificador automáticamente.

**Aceptación:** el correo incorrecto devuelve `400`, `code=VALIDATION_ERROR` y una infracción del campo `email`. No se crea un cliente. Con un `taxId` nuevo y `email=ana@example.test`, devuelve `201`. Un correo vacío también se rechaza. No se exige comprobar si existe el buzón.

**Depuración:** entrada por `src/main/java/com/acme/operations/customer/CustomerController.java`. Observa si la petición inválida llega al método que crea el cliente.

## 03 — Operaciones no puede consultar ventas

**Tema:** autorización de Spring Security. **Tiempo estimado:** 30–45 minutos.

**Contexto:** OPERATIONS gestiona la operativa diaria y necesita consultar ventas. ADMIN administra y READONLY solo consulta. Los tres pueden leer este informe.

**Reproducción:** ejecuta `POST /api/v1/auth/login` con `{"username":"operations","password":"DevPassword123!"}`. Usa el `accessToken` nuevo en Authorize y llama a `GET /api/v1/reports/sales`. Repite con `admin` y `readonly`, cambiando el token cada vez. El archivo HTTP mantiene los tres tokens por separado.

**Fallo preparado:** OPERATIONS recibe `403 ACCESS_DENIED`. ADMIN y READONLY reciben `200`.

**Aceptación:** los tres roles reciben `200`. Sin token se mantiene `401`. No se permite entrar a roles ajenos. El informe puede estar vacío: se comprueba el permiso, no el cálculo de ventas.

**Depuración:** ruta declarada en `src/main/java/com/acme/operations/report/ReportController.java`. Observa si el usuario rechazado llega al cuerpo del método.

## 04 — El catálogo anuncia menos páginas de las necesarias

**Tema:** paginación y colecciones. **Tiempo estimado:** 20–35 minutos.

**Contexto:** la pantalla utiliza `totalPages` para ofrecer las páginas disponibles. Los números de página empiezan en cero. El catálogo inicial tiene ocho productos. Con tres por página, necesita tres páginas: `0`, `1` y `2`.

**Reproducción:** con el token de `admin`, consulta:

```text
GET /api/v1/products?page=0&size=3&sort=id&direction=asc
GET /api/v1/products?page=2&size=3&sort=id&direction=asc
```

**Fallo preparado:** ambas respuestas indican `totalElements=8` y `totalPages=2`. La segunda petición sí devuelve los dos últimos productos, aunque los metadatos no anuncian esa tercera página.

**Aceptación:** ambas respuestas deben indicar `totalPages=3`. El contenido, `totalElements`, `page` y `size` se conservan. Comprueba también páginas exactas y resultados vacíos. Si has añadido productos, calcula las páginas con el nuevo `totalElements`; no borres datos para repetir.

**Depuración:** entrada por `src/main/java/com/acme/operations/product/ProductController.java`. Sigue la construcción de la respuesta paginada. No necesitas modificar consultas SQL.

## 05 — Se pierde el identificador entre servicios

**Tema:** comunicación HTTP y trazabilidad. **Tiempo estimado:** 35–50 minutos.

**Contexto:** `X-Request-ID` es una cabecera que relaciona los logs de una petición entre servicios. No es el ID del pedido ni un token. El backend debe pasar ese mismo identificador al servicio de envíos.

**Preparación:** usa las peticiones del bloque 05 del archivo HTTP, en orden. Buscan el cliente inicial con `search=B28000001` y el producto con `search=GLOVE-100`, y guardan sus `id`. Después crean un pedido nuevo con ese `customerId`, añaden una línea de `quantity=2` y lo confirman. Las dos cajas cuestan 49,80 €, lejos del límite del ejercicio 01. Los IDs se copian automáticamente de las respuestas.

**Reproducción:** completa ese pedido mediante `POST /api/v1/orders/{id}/complete`, con el token ADMIN y la cabecera `X-Request-ID: lab16-trace-001`. En la consola de Backend busca la línea de `/complete`. En la consola de Shipping busca la de `/quotes`. Compara `requestId`.

**Fallo preparado:** el pedido se completa y su `shippingCost` es `7.50`, pero Shipping registra un UUID diferente de `lab16-trace-001`.

**Control:** llama directamente a `GET http://localhost:39097/quotes?orderId=42&amount=49.80` con `X-Request-ID: lab16-direct-001`. Shipping sí conserva ese identificador cuando lo recibe directamente.

**Aceptación:** al completar un pedido nuevo, ambas consolas muestran `requestId=lab16-trace-001`. La cotización y el stock siguen funcionando. Si no envías la cabecera inicial, el identificador generado por Backend también debe llegar a Shipping.

**Repetición:** crea otro pedido con el bloque 05. No reutilices un pedido `COMPLETED`. Cada ejecución correcta consume dos unidades del producto; hay existencias de desarrollo para practicar.

**Depuración:** entrada por `src/main/java/com/acme/operations/order/OrderController.java`. Sigue la petición desde la operación de completar hasta el servicio remoto.

## Validación adicional

Después de corregir un ejercicio y reproducirlo por HTTP:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-batch-2026-11-16.ps1 -Ticket 01
```

Cambia `01` por el ticket elegido. `-Ticket all` comprueba los cinco. Las pruebas mantienen los casos de frontera, incluido el de 21 resultados con páginas de 20; no hace falta preparar esos datos manualmente.
