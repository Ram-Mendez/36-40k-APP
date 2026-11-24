Tus tareas de backend de hoy

1. INC-06 · Reserva que queda tras una confirmación rechazada
   El pedido 9 está en DRAFT y tiene 2 unidades reservadas. En Swagger, consulta GET /api/v1/orders/9 y GET /api/v1/inventory con productId = 18. Revisa también GET /api/v1/inventory/movements con productId = 18.

order con lineas
2. {
   "id": 9,
   "customerId": 9,
   "customerName": "L22-d538d249d81e4a4faf0d",
   "status": "DRAFT",
   "total": 30,
   "version": 2,
   "shippingReference": null,
   "shippingCost": null,
   "createdAt": "2026-11-22T05:29:21.787839Z",
   "updatedAt": "2026-11-22T05:29:21.874709Z",
   "lines": [
   {
   "id": 10,
   "productId": 18,
   "sku": "L22-1378805444A747AB8A23",
   "productName": "L22-1378805444a747ab8a23",
   "quantity": 2,
   "unitPrice": 10,
   "lineTotal": 20
   },
   {
   "id": 11,
   "productId": 19,
   "sku": "L22-33DBCE58A19E4A66AC91",
   "productName": "L22-33dbce58a19e4a66ac91",
   "quantity": 1,
   "unitPrice": 10,
   "lineTotal": 10
   }
   ]
   }
3. inventario:
4. {
   "content": [
   {
   "productId": 18,
   "sku": "L22-1378805444A747AB8A23",
   "productName": "L22-1378805444a747ab8a23",
   "onHand": 5,
   "reserved": 2,
   "available": 3
   }
   ],
   "page": 0,
   "size": 20,
   "totalElements": 1,
   "totalPages": 1
   }
5. movimientos inventario producto:
6. {
   "content": [
   {
   "id": 49,
   "productId": 18,
   "orderId": 9,
   "kind": "RESERVE",
   "quantity": 2,
   "reason": "Order confirmed",
   "createdAt": "2026-11-23T03:58:00.479008Z"
   },
   {
   "id": 21,
   "productId": 18,
   "orderId": null,
   "kind": "ADJUSTMENT",
   "quantity": 5,
   "reason": "Entrada del laboratorio 22",
   "createdAt": "2026-11-22T05:29:21.667819Z"
   }
   ],
   "page": 0,
   "size": 20,
   "totalElements": 2,
   "totalPages": 1
   }
3. 
4. 
   Sigue la confirmación desde OrderController.confirm en IntelliJ. Al rechazarse la confirmación, el pedido debe seguir en DRAFT sin dejar reservas. Consulta los pasos y los datos de comprobación en docs/training/2026-11-22/INC-06.md.

2. INC-02 · Comprobar el arreglo de devoluciones
   Hay tres devoluciones guardadas con cabecera 56,00 € y líneas por 18,50 €. Son datos anteriores. El código actual ya calcula usando la cantidad devuelta. Comprueba el comportamiento con una devolución nueva y revisa que creditAmount coincida con sus líneas. Consulta docs/training/2026-11-22/INC-02.md y usa un pedido que aún tenga cantidades disponibles.

La medición de Jenkins de hoy a las 08:53, hora de Madrid, encontró el backend UP, 1 pedido creado y 4 devoluciones solicitadas en las últimas 24 horas.

Informe: http://127.0.0.1:8082/job/parte-backend/4/artifact/informe.txt