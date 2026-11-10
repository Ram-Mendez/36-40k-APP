# INC-015 · Al completar el pedido se aplica una tarifa distinta a la cotizada

**Abierta · Prioridad alta · Expediciones**
**Informante:** Equipo de facturación.

> Para un pedido de 199,60 € nos cotizaron 7,50 € de porte. Cuando operaciones completó el pedido, la factura mostró envío gratuito. El importe de los artículos siguió siendo 199,60 €.
> 
> necesitas
> CUSTOMER 4 ,
> PRODUCTOS 16 y 17 
> ORDER 4 con 1 linea de cada producto

**Muestra de soporte.** Usa un producto de 199,60 €, ajusta su stock en +1 y añade una unidad a un pedido nuevo. Confírmalo. Con su ID real, consulta `GET http://localhost:29096/quotes?orderId={pedido}&amount=199.60`. Guarda la referencia y el coste. Completa el pedido una vez desde el backend y compara `shippingReference` y `shippingCost` con la cotización. Soporte observó que la referencia guardada terminaba en `200.00` y que el coste guardado era cero.

**Criterios de aceptación**

- El pedido de 199,60 € termina en `COMPLETED`, conserva ese total y guarda la misma referencia y los mismos 7,50 € que la cotización de shipping.
- Con un segundo pedido de exactamente 200,00 €, ambas vías aplican envío gratuito y las referencias coinciden. Usa datos nuevos al repetir una finalización.

 
el campo amount es inmutable.
y en el metodo quote() se redondea a 200.
Qué hice: poner un punto de ruptura y ver que pasa con
el campo amount

Qué sospeché:menor o igual a 200 exceptua la regla de shipping gratuito
Qué observé: con amount=199.60 el shipping cost pasaba a 0 debido a redondeo de amount
quitarle esa condicion a amount lo solucionó
