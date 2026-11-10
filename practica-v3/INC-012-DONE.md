# INC-012 · El diario de movimientos no cuadra con el recuento

**Abierta · Prioridad alta · Control de almacén**
**Informante:** Responsable de inventario.

> Entraron doce unidades y retiramos cinco dañadas. En la estantería quedan siete. En la exportación del diario parece que tuvimos dos entradas.

**Muestra de soporte.** Crea un producto sin existencias. Registra dos ajustes, primero `+12` por «Entrada inicial» y después `-5` por «Retirada por daños». Ambos devuelven 204. Consulta el saldo y el diario filtrados por el mismo `productId`. El saldo muestra 7 y el movimiento «Retirada por daños» muestra una cantidad de 5.

**Criterios de aceptación**

- El diario recoge las dos cantidades originales, 12 y −5, con el motivo y el producto correspondientes.
- El saldo físico y la suma de estos movimientos son ambos 7. Un ajuste que dejaría el saldo negativo se rechaza y conserva saldo e historial.



Qué hice:
Depuré el flujo de inventario de un producto,
y descubrí que la cantidad de un producto estaba encapsulada 
en un metodo Math.abs que retornaba el valor absoluto de la cantidad,
por lo que los movimientos de ajuste siempre mostraban cantidades positivas.

Qué sospeché: Al ingresar suficiente stock de un producto, 
y al retirar cantidades de ese producto,
en la peticion de movimientos aparecia como saldo positivo
Qué observé:
-- movimientos
{
"content": [
{
"id": 10,
"productId": 9,
"orderId": null,
"kind": "ADJUSTMENT",
"quantity": 5,
"reason": "Retirada por daños",
"createdAt": "2026-11-09T06:19:07.614744Z"
},
{
"id": 9,
"productId": 9,
"orderId": null,
"kind": "ADJUSTMENT",
"quantity": 12,
"reason": "stock inicial",
"createdAt": "2026-11-09T06:18:52.440190Z"
}
],
"page": 0,
"size": 20,
"totalElements": 2,
"totalPages": 1
}