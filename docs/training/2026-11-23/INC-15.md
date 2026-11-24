# INC-15 · El alta de un producto devuelve un enlace roto

El catálogo crea el producto y el cliente intenta abrir la URL que devuelve el alta.

1. Haz `POST /api/v1/products` con `{"sku":"T24-LOC-PRACTICA","name":"Soporte de banco","unitPrice":5}`. Usa un SKU nuevo si lo repites.
   {
   "id": 51,
   "sku": "T24-LOC-PRACTICA",
   "name": "Soporte de banco",
   "unitPrice": 5,
   "active": true,
   "version": 0
   }
2. Tras el `201`, mira `location` en **Response headers**. La respuesta indica `/api/v1/product/{id}`.
3.  location: /api/v1/product/51
4. En otra pestaña de Swagger, `GET /api/v1/products/{id}` con el `id` devuelto sí encuentra el producto.
3. Tras el arreglo, comprueba que `location` contiene `/api/v1/products/` seguido del mismo `id`.

Empieza en [ProductController.create](../../../src/main/java/com/acme/operations/product/ProductController.java) y sigue la petición con el depurador.
