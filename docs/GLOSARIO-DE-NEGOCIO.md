# Glosario de negocio de 36–40k-APP

Lee los términos desde el punto de vista de **tu empresa, la distribuidora**. Tu empresa compra al proveedor, vende al cliente, guarda mercancía en el almacén y cobra las facturas. En Swagger puedes representar a varias personas durante un mismo ejercicio.

## ¿En qué parte del flujo estoy?

| Si estás haciendo esto… | Sigue este recorrido |
|---|---|
| Un cliente te pide mercancía | **Customer → Order → Confirm → Complete**. Registras la venta, apartas las unidades y después las envías. |
| Necesitas más mercancía | **Replenishment proposal → Purchase order → Submit → Receipt**. Revisas la sugerencia, haces el pedido al proveedor y registras cada entrega. |
| Un cliente devuelve algo | **Return → Approve → Receive**. Registras la solicitud, la autorizas y el almacén inspecciona lo recibido. |
| Tienes que cobrar una venta | **Order completada → Issue invoice → Payment → Entry**. Emites la factura, registras el cobro y ese cobro queda anotado en el historial. |
| Quieres explicar una cifra | **Movements** explica qué pasó con las unidades. **Entries** explica qué pasó con los cobros y reembolsos. **Audit** indica quién hizo una operación. |

## Facturación y dinero · Billing

| Término de la app | Qué significa en tu trabajo |
|---|---|
| **Invoice / Issue invoice** | Factura / emitir factura. Al emitirla dejas constancia de cuánto debe el cliente por una venta y cuándo vence. Cada pedido completado admite una factura. |
| **Invoice number / invoiceId / orderId** | El número identifica la factura comercialmente. El invoiceId identifica esa factura dentro de la app. El orderId identifica la venta que la originó. |
| **Entry / Entries / Ledger** | Un **asiento** es una anotación de dinero en una factura. Por ejemplo, «hemos cobrado 5 € por transferencia». **Entries** es la lista de esos asientos y **ledger** es el libro donde quedan registrados. Una factura puede tener varios. |
| **Entry ID / entryId** | Identificador de un asiento concreto. Si quieres anular el segundo de tres cobros, eliges la factura y el ID de ese segundo cobro. En la respuesta de un cobro aparece como **id**. |
| **Payment / PAYMENT** | Cobro. El dinero entra del cliente a tu empresa. Puede cubrir una parte de la factura o todo su saldo pendiente. |
| **Credit / creditAmount / credited** | Un abono reconoce un importe a favor del cliente. Una devolución muestra su **creditAmount** según el precio original de venta. Al recibirla, ese importe cuenta en **credited**, la suma de abonos de la factura. |
| **Refund / REFUND** | Reembolso. Tu empresa devuelve dinero al cliente cuando hay saldo a su favor. Por ejemplo, si ya pagó todo y luego devuelve mercancía. |
| **Settlement** | En facturación, operación con la que registras un cobro o un reembolso. Puede ser parcial. |
| **Reverse / Reversal** | Anular el efecto de un asiento concreto. Si anotaste por error un cobro de 5 €, reviertes ese asiento. Sigue visible en el historial con fecha y motivo de anulación. |
| **Amount / kind / reference** | Importe, tipo de operación y referencia para reconocerla. En tu ejemplo son **5 €**, **PAYMENT** y **«pago inicial»**. |
| **Product total / shipping total / total / currency** | Importe de los productos, importe del envío, suma facturada y moneda. En tu pedido 8 son **59,95 € + 7,50 € = 67,45 €**. |
| **Net paid / netPaid** | Dinero cobrado menos dinero reembolsado, contando los asientos vigentes. Si cobras 5 € y anulas ese único cobro, debería volver a 0 €. |
| **Balance** | En una factura, saldo pendiente: total facturado menos abonos y menos dinero neto cobrado. Positivo: el cliente debe dinero. Cero: está saldada. Negativo: hay dinero a favor del cliente. |
| **OPEN / PARTIALLY_PAID / SETTLED / CREDIT_DUE** | Estados de factura: abierta, parcialmente pagada, saldada o con saldo a favor del cliente. La app calcula el estado a partir de los importes. |
| **Issued at / due date / overdue** | Fecha de emisión, fecha límite de pago y deuda vencida. Una factura puede estar pendiente de pago aunque aún quede plazo para pagarla. |
| **Receivables / outstanding / refundsDue** | Cuentas a cobrar, total que los clientes aún deben y total pendiente de reembolsar a clientes. **Overdue** recoge la parte de la deuda que ya venció. |
| **Aging / bucket / asOf / CURRENT_BALANCE** | El informe agrupa el saldo actual por antigüedad respecto a una fecha de referencia. **NOT_DUE** indica que aún no ha vencido; los siguientes tramos son 1–30, 31–60, 61–90 y más de 90 días. |

**Tu bug 4 en dinero.** Venta facturada por 67,45 € → cobras 5 € → quedan 62,45 € → anulas ese asiento → deberían volver a quedar 67,45 €. La venta, la factura y el asiento siguen teniendo cada uno su propio ID.

## Personas, catálogo y precios

| Término | Qué significa |
|---|---|
| **Customer / supplier / user** | El cliente te compra. El proveedor te vende. El usuario es la persona que entra en la app y registra la operación. |
| **Product / catalog / SKU** | Producto, catálogo de productos y código comercial de una referencia concreta. «Botas talla 42» es el producto; **BOOT-42** es su SKU; **productId** es su identificador interno. |
| **Tax ID / address** | Identificación fiscal y dirección de la empresa cliente o proveedora, según la ficha. |
| **Active / inactive** | La ficha está habilitada o dada de baja para las operaciones que requieren que esté activa. Los pedidos históricos conservan su relación con ella. |
| **Unit price / unit cost** | Precio al que **vendes** una unidad al cliente y coste al que **compras** una unidad al proveedor. |
| **Header / line / quantity / line total** | La cabecera reúne los datos de todo el documento. Cada línea corresponde a un producto. La cantidad indica cuántas unidades lleva esa línea. Su importe es cantidad por precio o coste unitario. |

## Pedidos de venta y envíos · Orders & Shipping

| Término | Qué ocurre |
|---|---|
| **Order / sales order** | Pedido de un cliente a tu empresa. Cuando completas la venta, la mercancía sale de tu almacén. |
| **DRAFT** | Borrador. Puedes cambiar productos y cantidades antes de comprometer existencias. |
| **Confirm / CONFIRMED** | Confirmas que atenderás el pedido y reservas sus unidades. Siguen físicamente en el almacén, apartadas para ese cliente. |
| **Complete / COMPLETED** | Registras la expedición. Se consumen las unidades reservadas, baja el stock físico y queda la información del envío. Facturación ya puede emitir la factura. |
| **Cancel / CANCELLED** | Cancelas el pedido. Si ya estaba confirmado, sus reservas se liberan y esas unidades vuelven a estar disponibles. |
| **Shipping / carrier / quote** | Envío, transportista y cotización del transporte. **Shipping cost** es el coste del envío y **shipping reference** es la referencia que devuelve el servicio de envío, como QUOTE-8-59.95. |

## Compras y recepciones · Procurement

| Término | Qué ocurre |
|---|---|
| **Procurement / purchase order / PO** | Aprovisionamiento / pedido de compra. Es el pedido que **tu empresa hace al proveedor**. Puedes comprar un producto aunque tu almacén tenga cero unidades. |
| **Lead time days / expectedOn / dueBefore** | Plazo habitual del proveedor en días, fecha prevista de llegada y filtro de entregas anteriores a una fecha. Con dueBefore del día 12, entra el día 11 y queda fuera el 12. |
| **DRAFT / submit / SUBMITTED** | Preparas la compra en borrador y la emites al proveedor. Al emitirla quedan fijadas sus líneas y costes. El almacén sigue esperando la mercancía. |
| **Receipt / receive / deliveryReference** | La recepción es el registro que hace el almacén cuando llega una entrega del proveedor. La referencia de entrega permite identificarla, por ejemplo por su número de albarán. Una compra puede tener varias recepciones. |
| **Quantity / receivedQuantity / outstandingQuantity** | Cantidad pedida originalmente, cantidad ya recibida y cantidad que todavía debe entregar el proveedor. En tu compra: **12 pedidas, 4 recibidas, 8 pendientes**. |
| **PARTIALLY_RECEIVED / RECEIVED / CANCELLED** | Ha llegado una parte, ha llegado toda la compra o la compra se canceló. |
| **Open commitment** | Compromiso de compra aún pendiente de entrega. Si pediste 12 y llegaron 4, quedan 8 comprometidas con ese proveedor. |

## Almacén y reposición · Inventory & Replenishment

| Término | Qué significa |
|---|---|
| **Inventory / stock / inventory balance** | Inventario, existencias y saldo de unidades de un producto. En esta parte de la app, **balance** se mide en unidades. |
| **On hand / reserved / available** | Existencias físicas registradas, unidades apartadas para pedidos y unidades libres para vender. Con 10 en almacén y 3 reservadas, quedan **7 disponibles**. |
| **Reservation / reserve / release** | Reserva, apartar unidades y liberar una reserva. Liberarlas permite destinarlas a otro pedido. |
| **Adjustment / movement** | Un ajuste corrige existencias o registra stock inicial. Un movimiento deja constancia de una operación sobre el inventario. |
| **ADJUSTMENT / RESERVE / RELEASE / SHIP / RECEIPT / RETURN** | Tipos de movimiento: ajuste, reserva, liberación, salida por venta, entrada por compra y reincorporación por devolución. RESERVE y RELEASE afectan a lo apartado; SHIP afecta también al físico. |
| **Replenishment / policy** | Reposición y regla de reposición. La regla fija cuándo conviene plantear otra compra de ese producto. |
| **Minimum stock / target stock** | El mínimo activa la propuesta si el stock previsto cae por debajo. El objetivo indica hasta dónde quieres llegar al comprar. Pueden ser **15 de mínimo y 20 de objetivo**. |
| **Incoming / projected** | Unidades que aún deben llegar de compras emitidas y stock previsto cuando lleguen. Con 4 disponibles y 8 en camino, tienes **12 previstas**. |
| **Proposal / suggested quantity** | Propuesta de reposición y cantidad que convendría pedir. Con 12 previstas, mínimo 15 y objetivo 20, la app sugiere **8 más**. Para encargarlas, alguien tiene que emitir la compra. |
| **Preferred supplier / purchasable / blocking reason** | Proveedor preferido, posibilidad de tramitar la propuesta y motivo que la bloquea, por ejemplo que falte el proveedor preferido o esté inactivo. |

## Devoluciones e inspección · Returns

| Término | Qué ocurre |
|---|---|
| **Return / RMA** | Devolución de mercancía que vendiste. Se vincula al pedido de venta original para saber qué producto se vendió, cuántas unidades y a qué precio. |
| **Request / REQUESTED** | Registras que el cliente quiere devolver ciertas unidades. En ese momento pueden seguir en casa del cliente. |
| **Review / approve / reject** | Revisar la solicitud y aprobarla o rechazarla. **APPROVED** autoriza la devolución; **REJECTED** la cierra como rechazada. **Review reason** explica la decisión. |
| **Receive / RECEIVED** | El operario de almacén registra que ha recibido e inspeccionado la mercancía del cliente. En compras, el mismo verbo se refiere a la entrega que llega del proveedor. |
| **Disposition / restockQuantity / damagedQuantity** | Resultado de la inspección: cuántas unidades vuelven a estar vendibles y cuántas llegaron deterioradas. Si vuelven 3 y solo 1 está bien, el stock vendible sube **1**. |
| **Credit amount** | Importe reconocido al cliente por la devolución según el precio de la venta. En tu ejemplo vuelven 3 teclados de 14 €: **42 € de abono** al recibirlos, aunque solo 1 pueda venderse otra vez. |

## Informes, seguimiento y campos que se repiten

| Término | Cómo leerlo |
|---|---|
| **Sales report / customer sales / completedOrders / netSales** | Informe de ventas agrupadas por cliente. Cuenta pedidos completados y suma sus importes de productos. El coste de envío aparece aparte. |
| **Low stock / threshold / confirmedOrders** | Informe de productos con pocas unidades disponibles según un umbral. En ese informe, confirmedOrders cuenta las reservas de pedidos del producto. |
| **Audit / event / actor / action / entity** | Historial de actividad. Permite identificar quién hizo qué y sobre qué registro, por ejemplo quién aprobó la devolución 3. |
| **ADMIN / OPERATIONS / READONLY** | Administrador, personal de operaciones y usuario de consulta. Por ejemplo, operaciones puede registrar un cobro y hace falta un administrador para revertirlo. |
| **ID / number / reference** | Un ID identifica un registro dentro de la app; un número identifica un documento comercial; una referencia permite relacionar la operación con algo reconocible, como una transferencia o un albarán. |
| **Order ID / invoice ID / entry ID / return ID / receipt ID** | Identifican respectivamente el pedido, la factura, el asiento, la devolución y la recepción. Dos documentos distintos pueden tener el mismo número de ID. |
| **Request ID / UUID / replay** | En cobros y recepciones, el requestId identifica un intento de registrar una operación de negocio. Se reutiliza al reintentar la misma operación para evitar duplicarla. UUID es el formato del identificador. |
| **Create request ID / receipt request ID** | En una devolución, el primero identifica la solicitud inicial y el segundo el registro de recepción. Ambos pertenecen a la misma devolución. |
| **Status / version / reason** | Fase actual, revisión actual del registro y motivo de la acción. Para aprobar o recibir una devolución, se usa la versión que devolvió el paso anterior. |
| **CreatedAt / updatedAt / reviewedAt / receivedAt / reversedAt** | Cuándo se creó el registro, cuándo cambió por última vez, cuándo se revisó, cuándo se recibió o cuándo se anuló un asiento. Una fecha vacía indica que esa acción aún no consta. |
| **Content / page / size / totalElements / totalPages** | Resultados que ves, página actual, máximo de filas por página, total de coincidencias y total de páginas. **Content vacío** significa que esa página no contiene resultados. |

## Tres documentos del mismo pedido

| Documento | Pregunta que responde | Ejemplo |
|---|---|---|
| **Order** | ¿Qué vendimos y a quién? | Pedido 8: unas botas de 59,95 € para el cliente. |
| **Invoice** | ¿Cuánto facturamos y qué queda por cobrar? | Factura 1: 67,45 € con envío. |
| **Entry** | ¿Qué operación de dinero se registró en esa factura? | Asiento 1: cobro de 5 € con referencia «pago inicial». |
