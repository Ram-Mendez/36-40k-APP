# 17/11 — segunda tanda

Todo se trabaja en `C:\Users\balar\Desktop\36-40k-APP`. Mantén **Backend APP** y **Shipping APP**, con el arranque habitual de [EMPIEZA-AQUI.md](../../../EMPIEZA-AQUI.md). No crees otra carpeta ni otra configuración.

Son cinco investigaciones independientes. La dificultad está en reproducir y contrastar los casos, no en cuántas líneas ocupa la corrección. No se asigna una nota artificial a todos los ejercicios.

Backend: **38088**; Shipping: **39098**; frontend: **4200**. En Swagger inicia sesión como `admin` con `DevPassword123!` y usa su `accessToken` en Authorize, salvo los cambios de token expresos del 02.

Los datos de preparación se generan mediante peticiones reales, sin escribir SQL. Los scripts dejan los IDs y cuerpos en `target/current-batch/01.json`, `03.json`, etc. También preparan el entorno **current** de [http/current-batch.http](../../../http/current-batch.http), que puedes seleccionar en IntelliJ para no copiar IDs.

## 01 — Reintentar la misma entrega cambia el resultado al ordenar las líneas
## 02 — Cerrar sesión en un dispositivo expulsa al otro
## 03 — Un pedido con dos líneas se cuenta como dos ventas
## 04 — El límite de espera configurado no se respeta

**Tema:** configuración y tiempos de HTTP. **Contexto:** conectar al servidor y esperar su respuesta son fases distintas. Backend APP tiene 5 segundos para conectar y **1 segundo para esperar la respuesta**. Una cotización que tarde 1,5 segundos debe rechazarse con 503 y dejar el pedido confirmado.

1. Mantén Backend APP arrancado. Detén únicamente **Shipping APP**.
2.Desde la raíz, inicia un servicio lento de prueba: `node scripts/slow-shipping.mjs`. Escucha en el mismo 39098; no tienes que cambiar puertos ni Program arguments.
3. 
3. En otro terminal prepara un pedido confirmado:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\prepare-current-batch.ps1 -Ticket 04
```

4. Lee `orderId`. Envía `POST /api/v1/orders/{orderId}/complete` con tu token ADMIN. Observa `durationMs` en el log y vuelve a consultar el pedido.
   {
   "id": 10,
   "customerId": 8,
   "customerName": "Batch B buyer 1118035833584",
   "status": "COMPLETED",
   "total": 20,
   "version": 3,
   "shippingReference": "SLOW-10",
   "shippingCost": 7.5,
   "createdAt": "2026-11-18T02:58:41.713566Z",
   "updatedAt": "2026-11-18T02:59:37.482808Z",
   "lines": [
   {
   "id": 13,
   "productId": 122,
   "sku": "B2-1118035833584-P",
   "productName": "Batch B product",
   "quantity": 2,
   "unitPrice": 10,
   "lineTotal": 20
   }
   ]
   }

**Fallo preparado:** espera aproximadamente 1,5 segundos, devuelve 200 y completa el pedido pese al límite de lectura de 1 segundo.

**Aceptación:** el límite de lectura gobierna la espera de respuesta: se obtiene 503 aproximadamente al llegar a ese límite, el pedido conserva `CONFIRMED` y su reserva, y no se descuenta stock. El límite de conexión sigue siendo independiente. No reduzcas todos los tiempos ni inventes una cotización para ocultar el fallo.

Al terminar, detén el simulador con Ctrl+C y arranca **Shipping APP**. Con el servicio rápido, completar debe funcionar. Si el intento anterior ya completó el pedido, prepara uno nuevo para repetir.

El test automático retiene una respuesta con una señal y comprueba que el timeout la corta antes de liberarla; no necesita esperar una caída ni detener tus aplicaciones.
