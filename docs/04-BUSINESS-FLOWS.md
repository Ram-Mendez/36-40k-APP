# Flujos de negocio y contrato HTTP

La base común es `/api/v1`; salvo login y refresh, enviar `Authorization: Bearer <accessToken>`. Los identificadores se obtienen de las respuestas, no deben suponerse constantes.

## Venta completa

1. `POST /auth/login` con username/password devuelve accessToken, refreshToken, tokenType y expiresIn.
2. `GET /customers?search=...&active=true` permite seleccionar un cliente; `GET /products` e `GET /inventory?productId=...` muestran precio y disponible.
3. `POST /orders` con `{"customerId":1}` crea DRAFT (201 y Location).
4. `PUT /orders/{id}/lines/{productId}` con `{"quantity":3}` crea o reemplaza la cantidad. Al añadir por primera vez se copia el precio vigente; las ediciones de cantidad conservan ese precio. El total se calcula en servidor.
5. `POST /orders/{id}/confirm` reserva todas las líneas o no reserva ninguna. Devuelve 409 si faltan unidades, hay un maestro inactivo o el estado no permite confirmar.
6. `POST /orders/{id}/complete` consulta `GET /quotes` al transportista simulado. Una respuesta válida permite completar y consumir stock. Un fallo devuelve 503; permanece CONFIRMED y puede reintentarse.
7. `GET /orders/{id}`, `/inventory/movements?productId=...` y `/audit?entityType=ORDER&entityId=...` permiten investigar la operación; auditoría requiere ADMIN.

La cotización incluye coste de transporte separado del total neto de líneas. Este sistema simula una expedición interna utilizando información de transporte; no compra una etiqueta ni contrata un envío real.

## Estados

| Estado actual | Acción | Estado final | Efecto stock |
| --- | --- | --- | --- |
| DRAFT | Editar cliente o líneas | DRAFT | Ninguno |
| DRAFT | Confirmar | CONFIRMED | Reservar |
| DRAFT | Cancelar | CANCELLED | Ninguno |
| CONFIRMED | Cancelar | CANCELLED | Liberar |
| CONFIRMED | Completar | COMPLETED | Consumir físico y reserva |
| COMPLETED / CANCELLED | Transición | Rechazada 409 | Ninguno |

Repetir una transición exitosa devuelve 409; no se duplica su efecto. Los pedidos terminados conservan historial. No existe borrado físico ni reapertura.

## Maestros y ajustes

Clientes: POST/PUT reciben name, taxId, email y address; PUT también requiere la versión leída. Productos: sku, name y unitPrice; PUT requiere version. DELETE realiza baja lógica. Una versión antigua devuelve 409 para evitar sobrescribir una actualización ajena. Los identificadores fiscales y SKU son únicos.

`POST /inventory/{productId}/adjustments` recibe quantity firmada y reason. Una recepción aumenta físicas; una corrección negativa las reduce solo cuando siguen cubriendo reservas. Cada ajuste deja movimiento y auditoría. No modificar saldos por SQL en operación normal.

## Consultas y errores

- Clientes/productos: page desde 0, size 1–100, search, active, sort y direction. sort permite id/name y unitPrice en productos; direction asc/desc.
- Pedidos: customerId, status, page, size y sort `createdAt,asc`, `createdAt,desc`, `total,asc` o `total,desc`.
- Inventario y movimientos: productId, page y size. Reservas: page y size.
- Informes: `/reports/sales` con from/to ISO instant y `/reports/low-stock` con threshold; ambos paginados.

Las páginas de negocio incluyen content, page, size, totalElements y totalPages. Usuarios tiene su DTO de página con content/page/size/totalElements.

Errores: 400 datos inválidos; 401 autenticación ausente/caducada; 403 permiso insuficiente; 404 recurso ausente; 409 conflicto de estado, versión, duplicado o stock; 503 transporte no disponible. La respuesta ProblemDetail incluye code y requestId. Usar code para decisiones del cliente; detail es texto explicativo.
