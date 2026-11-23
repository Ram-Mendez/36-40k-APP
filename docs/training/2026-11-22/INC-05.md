# INC-05 · Una deuda aparece en un tramo de antigüedad incorrecto

**Incidencia.** Una factura de 17,50 € aparece en un tramo demasiado reciente. El total del informe puede ser correcto aunque la antigüedad esté mal.

**Datos preparados:** cliente `8`; factura `2`; vencimiento `2026-11-24`.

## Reproducir en Swagger

1. Consulta `GET /api/v1/billing/invoices/{id}` con `id = 2`.
   {
   "id": 2,
   "number": "INV-2",
   "orderId": 8,
   "customerId": 8,
   "customerName": "L22-1a5b6e17afb04d3a85b4",
   "customerTaxId": "L22-1A5B6E17AFB04D3A85B4",
   "customerAddress": "Calle Olmo 4",
   "currency": "EUR",
   "productTotal": 10,
   "shippingTotal": 7.5,
   "total": 17.5,
   "credited": 0,
   "netPaid": 0,
   "balance": 17.5,
   "status": "OPEN",
   "dueDate": "2026-11-24",
   "issuedAt": "2026-11-22T05:29:15.719299Z"
   }
2. En `GET /api/v1/billing/receivables` usa siempre `customerId = 8`. Cambia `asOf` y busca el tramo que tenga `invoiceCount = 1`:

   {
   "name": "1_30",
   "invoiceCount": 1,
   "amount": 17.5
   },

| `asOf` | Días desde el vencimiento | Tramo esperado |
| --- | ---: | --- |
| `2026-11-24` | 0 | `NOT_DUE` |  SI NOT DUE
| `2026-11-25` | 1 | `1_30` |  DUE
CON ESTE SI HAY rango 1_30 correcto
| `2026-12-24` | 30 | `1_30` |

aqui tiene mas de 30 dias y aparece en el rango de 1_30
| `2026-12-25` | 31 | `31_60` |
lo sigue mostrando en el 1_30
| `2027-01-23` | 60 | `31_60` |
vuelve a aparecer en NOT DUE. el 24 es sospechoso
| `2027-01-24` | 61 | `61_90` |
aparece ahora en el 1_30
| `2027-02-22` | 90 | `61_90` |
aparece en el 1_30
| `2027-02-23` | 91 | `OVER_90` |

**Resultado esperado:** una sola factura por 17,50 € en el tramo indicado en cada consulta.

**Para investigar:** pon un breakpoint en [BillingController.receivables](../../../src/main/java/com/acme/operations/billing/BillingController.java) y sigue las fechas hasta la consulta SQL. Después del arreglo puedes repetir las mismas lecturas.
