# INC-47 · El peso facturable de varios bultos sale demasiado bajo

## Qué tiene que pasar

La respuesta correcta contiene `billableKg = 14`.

## Reproduce el fallo

En **Conciliación de almacén**, ejecuta `GET /api/v1/warehouse-reconciliation/billable-weight/{shipment}` con `shipment = SHIP-47`. La respuesta actual muestra `billableKg = 10`

{
"billableKg": 10,
"parcels": [
{
"code": "P1",
"actualKg": 8,
"volumetricKg": 3
},
{
"code": "P2",
"actualKg": 2,
"volumetricKg": 6
}
]
}.

## Depura paso a paso

1. Detente en `charge` tras la consulta. P1 pesa 8 kg reales y 3 volumétricos. P2 pesa 2 reales y 6 volumétricos.
2. Aplica la tarifa a cada bulto a mano. El transportista cobra el mayor de los dos pesos de cada bulto. P1 aporta 8 y P2 aporta 6.
3. Avanza hasta `actual` y `volumetric`. Comprueba cuándo se comparan los pesos en el código. El resultado cambia según se compare cada bulto o se comparen los totales del envío.

[Abre `WarehouseReconciliationService.charge`](../../../src/main/java/com/acme/operations/reconciliation/WarehouseReconciliationService.java)

## Comprueba tu arreglo

El GET debe devolver `billableKg = 14` y conservar los pesos originales de P1 y P2 en `parcels`.
