# INC-16 · La edición del cliente conserva la dirección anterior

Atención al cliente corrige la dirección y la API responde `200`, pero conserva la anterior.

1. Haz `GET /api/v1/customers/21` y anota `version`.
2. {
   "id": 21,
   "name": "Cliente práctica 3",
   "taxId": "T24-C-3",
   "email": "cliente24-3@example.test",
   "address": "Almacén anterior",
   "active": true,
   "version": 0
   }
2. Haz `PUT /api/v1/customers/21` con `{"name":"Cliente práctica 3","taxId":"T24-C-3","email":"cliente24-3@example.test","address":"Muelle nuevo","version":0}`. Si la versión cambió, usa la del paso 1.
   {
   "id": 21,
   "name": "Cliente práctica 3",
   "taxId": "T24-C-3",
   "email": "cliente24-3@example.test",
   "address": "Almacén anterior",
   "active": true,
   "version": 0
   }
3. Repite el `GET`. Actualmente sigue `Almacén anterior`. Tras el arreglo, tanto el PUT como el GET deben mostrar `Muelle nuevo`.

Empieza en [CustomerController.update](../../../src/main/java/com/acme/operations/customer/CustomerController.java) y sigue la petición con el depurador.
