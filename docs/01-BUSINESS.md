# Empresa y alcance

La empresa distribuye material a otros negocios. El equipo comercial registra pedidos de clientes empresariales; operaciones comprueba disponibilidad, confirma compromisos y prepara expediciones. Administración mantiene usuarios, permisos y datos maestros. La aplicación centraliza estos procesos en un único backend.

## Personas y responsabilidades

| Rol | Trabajo cotidiano |
| --- | --- |
| ADMIN | Mantener usuarios y datos maestros, supervisar y ejecutar operaciones. |
| OPERATIONS | Consultar clientes y catálogo, tramitar pedidos y existencias. |
| READONLY | Consultar información e informes sin modificar recursos. |

Los permisos efectivos se documentan en `05-SECURITY.md` y en OpenAPI. No existe registro público de usuarios: este es un sistema interno.

## Lenguaje de negocio

- Existencias físicas: unidades que todavía están en almacén.
- Reservadas: unidades comprometidas por pedidos confirmados.
- Disponibles: físicas menos reservadas. No pueden hacerse negativas.
- Pedido borrador: intención de compra todavía modificable.
- Pedido confirmado: compromiso de suministro con reserva atómica.
- Pedido completado: operación expedida que consume existencias y reservas.
- Pedido cancelado: operación terminada sin expedición que libera sus reservas.

La aplicación conserva el precio aceptado en las líneas del pedido. Cambiar el precio de catálogo no reescribe operaciones anteriores. Los importes usan aritmética decimal y representan EUR sin cálculo fiscal: impuestos, facturación y contabilidad están fuera del alcance.

## Problemas que resuelve

Un comercial puede consultar el cliente, crear un pedido con varias líneas y conocer su total. Operaciones puede confirmarlo sin vender dos veces las mismas unidades, cancelarlo sin dejar stock bloqueado y completarlo mediante el transportista. Las consultas permiten identificar pedidos pendientes y necesidades de reposición; el historial de movimientos y la auditoría permiten investigar qué ocurrió.

No es un ERP completo: no incluye compras a proveedores, múltiples almacenes, pagos, devoluciones, facturas ni frontend. Es una unidad de negocio delimitada y mantenible.
