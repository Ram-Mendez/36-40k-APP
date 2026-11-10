# INC-011 · La búsqueda de una referencia devuelve dos etiquetas

**Abierta · Prioridad media · Catálogo**
**Informante:** Operadora de reposición.

> Busco el código que tengo impreso en la caja y aparecen dos artículos. Sus nombres son distintos y uno ni siquiera lleva el signo de la etiqueta.

**Muestra de soporte.** Da de alta dos productos a 10,00 € con los SKU `ETQ7_A` y `ETQ7XA`.
14 15
En `GET /api/v1/products` busca `ETQ7_A`. Soporte recibió ambas referencias. Al repetir la prueba con datos nuevos, cambia `ETQ7` por el mismo prefijo nuevo en los dos SKU y en la búsqueda.

**Criterios de aceptación**

- Buscar `ETQ7_A` o `etq7_a` devuelve solo el artículo de la etiqueta y `totalElements = 1` en esa muestra.
- Buscar solo el prefijo `ETQ7` sigue encontrando los dos productos. La búsqueda parcial por nombre también sigue disponible.


Qué hice: Recordar este caso como uno previo hecho recientemente. 
la busqueda por SKU no funciona con barra baja

Qué sospeché: como se gestionaba search al recibir la peticion
Qué observé: resulados que no tienen que ver con lo que filtro