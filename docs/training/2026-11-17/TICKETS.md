## 01 — La cotización recuerda un importe antiguo
## 02 — Dos pestañas pisan el precio del producto
## 03 — Se detecta la reutilización del refresh, pero la sesión sigue viva
## 04 — Shipping vuelve, pero Backend no se recupera
**Tema:** ciclo de vida de un cliente HTTP y recuperación de dependencias. **Dificultad estimada:** 4/5.
Un corte temporal del servicio de envíos debe permitir reintentar cuando se recupera. Un pedido confirmado conserva las unidades reservadas hasta que la cotización funciona.

1. Reinicia Backend al comenzar este ejercicio. Mantén Shipping en marcha.
2. Con el bloque 04 crea un pedido nuevo del cliente inicial
3. {
   "id": 2,
   "customerId": 1,
   "customerName": "Ferretería Central SL",
   "status": "DRAFT",
   "total": 0,
   "version": 0,
   "shippingReference": null,
   "shippingCost": null,
   "createdAt": "2026-11-17T10:31:23.288082Z",
   "updatedAt": "2026-11-17T10:31:23.288082Z",
   "lines": []
   }
4. y añade dos cajas de `GLOVE-100`;
5. {
   "id": 2,
   "customerId": 1,
   "customerName": "Ferretería Central SL",
   "status": "DRAFT",
   "total": 49.8,
   "version": 1,
   "shippingReference": null,
   "shippingCost": null,
   "createdAt": "2026-11-17T10:31:23.288082Z",
   "updatedAt": "2026-11-17T10:31:47.839112Z",
   "lines": [
   {
   "id": 2,
   "productId": 1,
   "sku": "GLOVE-100",
   "productName": "Caja de guantes de trabajo",
   "quantity": 2,
   "unitPrice": 24.9,
   "lineTotal": 49.8
   }
   ]
   }
6. confirma el pedido. Su total es 49,80 €. SI
7. {
   "id": 2,
   "customerId": 1,
   "customerName": "Ferretería Central SL",
   "status": "CONFIRMED",
   "total": 49.8,
   "version": 2,
   "shippingReference": null,
   "shippingCost": null,
   "createdAt": "2026-11-17T10:31:23.288082Z",
   "updatedAt": "2026-11-17T10:32:08.112019Z",
   "lines": [
   {
   "id": 2,
   "productId": 1,
   "sku": "GLOVE-100",
   "productName": "Caja de guantes de trabajo",
   "quantity": 2,
   "unitPrice": 24.9,
   "lineTotal": 49.8
   }
   ]
   }
3. Detén **solo Shipping LAB 17-11** desde IntelliJ.  si
5. Intenta completar el pedido: debe devolver 503.si
   
4. Arranca Shipping otra vez. Confirma que `http://localhost:39098/actuator/health/readiness` devuelve UP y una consulta directa a `/quotes` devuelve 200. si
5. Sin reiniciar Backend, vuelve a completar **el mismo pedido confirmado**.

**Fallo preparado:** el paso 5 vuelve a dar 503 y Shipping no recibe una nueva consulta. Reiniciar Backend permite completar el pedido.

**Esperado:** una caída no invalida permanentemente el cliente; al recuperarse Shipping, reintentar completa el pedido una sola vez. Antes del éxito no se consume stock ni se pierde la reserva. No añadas reintentos infinitos ni soluciones que devuelvan cotizaciones ficticias.

**Entrega:** diferencia conexión rechazada, servicio recuperado y estado interno del cliente; comprueba si el segundo intento llega por la red. Al terminar reinicia Backend si no has corregido todavía el fallo, para no interferir con otros tickets.
