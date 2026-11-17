# 17/11 — segunda tanda

Todo se trabaja en `C:\Users\balar\Desktop\36-40k-APP`. Mantén **Backend APP** y **Shipping APP**, con el arranque habitual de [EMPIEZA-AQUI.md](../../../EMPIEZA-AQUI.md). No crees otra carpeta ni otra configuración.

Son cinco investigaciones independientes. La dificultad está en reproducir y contrastar los casos, no en cuántas líneas ocupa la corrección. No se asigna una nota artificial a todos los ejercicios.

Backend: **38088**; Shipping: **39098**; frontend: **4200**. En Swagger inicia sesión como `admin` con `DevPassword123!` y usa su `accessToken` en Authorize, salvo los cambios de token expresos del 02.

Los datos de preparación se generan mediante peticiones reales, sin escribir SQL. Los scripts dejan los IDs y cuerpos en `target/current-batch/01.json`, `03.json`, etc. También preparan el entorno **current** de [http/current-batch.http](../../../http/current-batch.http), que puedes seleccionar en IntelliJ para no copiar IDs.

## 01 — Reintentar la misma entrega cambia el resultado al ordenar las líneas

**Tema:** normalización Java e idempotencia. **Contexto:** llega una entrega con dos productos. Registrar su recepción aumenta el stock una sola vez. Si el navegador reenvía la misma operación, su UUID `requestId` permite devolver la recepción original sin duplicarla. El orden visual de las líneas no cambia qué mercancía llegó.

Prepara una compra ya enviada al proveedor, con dos productos pendientes:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\prepare-current-batch.ps1 -Ticket 01
```

1. Copia `endpoint` y `firstBody` del resultado en Swagger: `POST /api/v1/purchase-orders/{id}/receipts`. Envía: llegan 2 unidades de P y 3 de Q. Guarda el `id` de la recepción devuelta.
2. Envía `retryBody`: **mismo requestId**, misma referencia, mismas cantidades; solo se ha invertido el orden de las líneas.
3. Como control, vuelve a la primera disposición y cambia las unidades de P de 2 a 4 manteniendo el mismo UUID.

**Fallo preparado:** el paso 2 responde `409 IDEMPOTENCY_CONFLICT`, como si se intentara reutilizar el UUID para otra entrega.

**Aceptación:** el paso 2 devuelve 200 y la misma recepción del paso 1. P sigue con 2 unidades recibidas y Q con 3; no hay otra recepción, movimiento ni auditoría de recepción. El paso 3 sí devuelve 409. Con un UUID nuevo, una nueva entrega real sigue siendo posible.

La comparación debe reconocer el significado de la operación sin perder la detección de cambios reales. No basta con devolver siempre la recepción anterior.

## 02 — Cerrar sesión en un dispositivo expulsa al otro

**Tema:** seguridad y alcance de una sesión. **Contexto:** la misma persona puede tener dos sesiones independientes, una en el portátil y otra en otro navegador. Un **login nuevo** crea una sesión independiente; renovar un token pertenece a la misma cadena anterior.

Aquí **no uses refresh**. Haz exactamente dos logins:

1. `POST /api/v1/auth/login` con `operations` / `DevPassword123!`. Guarda su `accessToken` como **A**.
2. Repite LOGIN con las mismas credenciales. Guarda ese `accessToken` distinto como **B**.
3. En Authorize pon B y consulta productos: 200. Comprueba también A: 200.
4. En Authorize pon **A**. Ejecuta `POST /api/v1/auth/logout`: 204.
5. En Authorize pon **B**, sin hacer otro login. Consulta productos otra vez.

**Fallo preparado:** B recibe 401 aunque solo cerraste A.

**Aceptación:** A queda rechazado y B continúa con 200. Otra cuenta también conserva su acceso. El refresh perteneciente a A queda revocado; el de B sigue siendo válido. El cierre no cambia roles ni desactiva al usuario. La validación debe comprobar los dos lados: no se acepta conservar A para que B sobreviva.

El archivo HTTP conserva A y B en variables separadas para evitar confundirlos. El test añade también el control de otra cuenta y de los refresh de ambos dispositivos.

## 03 — Un pedido con dos líneas se cuenta como dos ventas

**Tema:** informes SQL y nivel de agregación. **Contexto:** una fila del informe representa un cliente; `completedOrders` cuenta pedidos, no productos. El total de cada pedido debe sumarse una sola vez.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\prepare-current-batch.ps1 -Ticket 03
```

La preparación crea un cliente nuevo y completa dos pedidos: A tiene dos líneas de 10 y 20 €; B tiene una línea de 3 unidades a 10 €. **Cada pedido suma 30 €, y las ventas suman 60 €.** Shipping debe estar arrancado.

1. Lee `customerId`, `orderA` y `orderB` del resultado.
2. Consulta `GET /api/v1/orders/{id}` para ambos pedidos y verifica sus líneas y totales.
3. Consulta `GET /api/v1/reports/sales?size=100`. Busca en `content` la fila de ese `customerId`.

**Fallo preparado:** el informe muestra 3 pedidos y 90 € para ese cliente. Los pedidos individuales siguen mostrando sus 30 € correctos.

**Aceptación:** `completedOrders=2`, `netSales=60.00` y el coste de envío es la suma de los dos envíos. Comprueba paginación y filtros de fecha. Es intencionado que ambos pedidos tengan el mismo total: eliminar importes repetidos perdería una venta válida. La corrección no modifica pedidos ni precios.

## 04 — El límite de espera configurado no se respeta

**Tema:** configuración y tiempos de HTTP. **Contexto:** conectar al servidor y esperar su respuesta son fases distintas. Backend APP tiene 5 segundos para conectar y **1 segundo para esperar la respuesta**. Una cotización que tarde 1,5 segundos debe rechazarse con 503 y dejar el pedido confirmado.

1. Mantén Backend APP arrancado. Detén únicamente **Shipping APP**.
2. Desde la raíz, inicia un servicio lento de prueba: `node scripts/slow-shipping.mjs`. Escucha en el mismo 39098; no tienes que cambiar puertos ni Program arguments.
3. En otro terminal prepara un pedido confirmado:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\prepare-current-batch.ps1 -Ticket 04
```

4. Lee `orderId`. Envía `POST /api/v1/orders/{orderId}/complete` con tu token ADMIN. Observa `durationMs` en el log y vuelve a consultar el pedido.

**Fallo preparado:** espera aproximadamente 1,5 segundos, devuelve 200 y completa el pedido pese al límite de lectura de 1 segundo.

**Aceptación:** el límite de lectura gobierna la espera de respuesta: se obtiene 503 aproximadamente al llegar a ese límite, el pedido conserva `CONFIRMED` y su reserva, y no se descuenta stock. El límite de conexión sigue siendo independiente. No reduzcas todos los tiempos ni inventes una cotización para ocultar el fallo.

Al terminar, detén el simulador con Ctrl+C y arranca **Shipping APP**. Con el servicio rápido, completar debe funcionar. Si el intento anterior ya completó el pedido, prepara uno nuevo para repetir.

El test automático retiene una respuesta con una señal y comprueba que el timeout la corta antes de liberarla; no necesita esperar una caída ni detener tus aplicaciones.

## 05 — La API tiene todos los productos y Angular omite la última página

**Tema:** carga paginada del frontend. **Contexto:** para pintar el catálogo, Angular solicita páginas de 100 productos y reúne sus resultados. La API informa correctamente cuántas páginas existen. La última página puede contener menos de 100 y también debe cargarse.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\prepare-current-batch.ps1 -Ticket 05
```

El script añade 101 productos nuevos y muestra el total real del catálogo. No lo repitas innecesariamente; no necesitas borrarlos para trabajar.

1. Desde `frontend`, ejecuta `npm start`; abre http://localhost:4200 e inicia sesión como admin.
2. En Products refresca los datos y busca los productos con nombre `ZZ Batch B ...` del último tramo.
3. En Swagger consulta `GET /api/v1/products?page=0&size=100` y después `page=1`. Si ya hay más de 200 productos, continúa hasta la página final indicada por `totalPages - 1`.
4. Compara los IDs de la página final con los productos cargados en la interfaz. En la pestaña Network del navegador puedes comprobar qué números de página pidió Angular.

**Fallo preparado:** la API devuelve la página final, pero Angular no la solicita. Parte del catálogo no se puede encontrar en la pantalla.

**Aceptación:** la pantalla reúne todas las páginas, incluida la última incompleta, sin duplicados ni consultas fuera del rango. Cero resultados y una única página siguen funcionando. Conserva la protección contra respuestas antiguas al cerrar sesión. No cambies los totales de la API ni soluciones el problema cargando una página gigantesca.

## Comprobar una corrección

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\check-current-batch.ps1 -Ticket 01
```

Cambia el número, o usa `-Ticket all`. Los checks de 01–03 usan una base desechable de Testcontainers. El 04 usa un servidor HTTP local, y el 05 ejecuta el servicio Angular real con respuestas controladas. La práctica se hace con la app abierta y los tests sirven de regresión.

Anota en [PROGRESO.md](PROGRESO.md) el síntoma, la causa y los controles que pasaron.
