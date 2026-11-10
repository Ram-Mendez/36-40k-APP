# EXP-20261110 — Ampliación del dominio B2B

Tipo: Epic. Prioridad: P1, desbloqueo de variedad funcional del laboratorio. Entorno: Java 21, Spring Boot 3.5.12, PostgreSQL 16. Base: `48a5b99440e4e6f3fbe125c822209d3499822b25`. Estos work items describen la ampliación implementada, no nuevas incidencias asignadas al alumno. Evidencia de cierre: [VALIDATION.md](VALIDATION.md).

## EXP-201 · Gestionar proveedores y compromisos de compra

**Tipo / componente:** Story / Compras. **Prioridad:** P1.

**Necesidad:** Operaciones debe registrar proveedores y emitir pedidos con coste y fecha pactados sin recurrir a ajustes manuales de stock.

**Precondiciones:** Usuario ADMIN u OPERATIONS; productos activos del catálogo. **Alcance:** proveedor versionado, baja lógica, borrador con líneas, emisión y cancelación anterior a cualquier recepción.

**Criterios de aceptación**

- AC1: alta y edición conservan identificador fiscal único; una versión obsoleta devuelve 409 y no modifica datos.
- AC2: emitir una compra exige proveedor activo, al menos una línea válida y total dentro del rango soportado.
- AC3: el coste de las líneas emitidas permanece inmutable; cambiar el catálogo no cambia el compromiso.
- AC4: un proveedor desactivado no admite nuevas compras ni emisiones; los compromisos ya emitidos pueden recibirse.
- AC5: listados conservan filtros, conteo y páginas estables; READONLY puede consultar y no escribir.

## EXP-202 · Registrar recepciones parciales sin duplicar existencias

**Tipo / componente:** Story / Compras e inventario. **Prioridad:** P1.

**Necesidad:** Una compra puede llegar en varias entregas y los terminales pueden repetir una petición tras perder la respuesta.

**Precondiciones:** Compra SUBMITTED con cantidades comprometidas. **Resultado:** comprobante persistido, pendiente actualizado y stock físico recibido.

**Criterios de aceptación**

- AC1: una recepción menor que el pendiente produce PARTIALLY_RECEIVED; recibir el resto produce RECEIVED.
- AC2: repetir UUID y contenido devuelve el mismo comprobante sin nuevos movimientos ni auditoría; cambiar contenido da 409.
- AC3: ninguna entrega, incluidas dos simultáneas, supera lo comprado.
- AC4: una línea inválida o un fallo de capacidad revierte la operación completa; no quedan comprobantes parciales.
- AC5: aumenta `on_hand`, conserva `reserved` y no habilita cancelación después de recibir mercancía.

## EXP-203 · Autorizar devoluciones dentro de las unidades vendidas

**Tipo / componente:** Story / Postventa. **Prioridad:** P1.

**Necesidad:** Postventa debe solicitar y decidir devoluciones vinculadas a una expedición verificable.

**Precondiciones:** Pedido COMPLETED y productos presentes en sus líneas. **Alcance:** solicitud, consulta, aprobación o rechazo versionados.

**Criterios de aceptación**

- AC1: solicitudes activas y recibidas no exceden las unidades vendidas, incluso ante creación concurrente.
- AC2: rechazar una solicitud libera su cupo; aprobarla conserva la cantidad comprometida.
- AC3: solicitud repetida con el mismo UUID/contenido no duplica el expediente; otro contenido da 409.
- AC4: una decisión con versión obsoleta o estado incompatible no cambia expediente ni inventario.
- AC5: el importe se calcula con el precio del pedido y nunca con un precio posterior del catálogo.

## EXP-204 · Inspeccionar mercancía devuelta y reconocer el abono

**Tipo / componente:** Story / Postventa, stock y saldos. **Prioridad:** P1.

**Necesidad:** Separar mercancía recuperable y dañada, y reconocer el crédito cuando la devolución se recibe.

**Precondiciones:** Devolución APPROVED. **Resultado:** estado RECEIVED, inspección por línea, stock y crédito coherentes.

**Criterios de aceptación**

- AC1: cada línea aparece una vez; recuperable más dañado coincide exactamente con la cantidad solicitada.
- AC2: solo recuperable aumenta stock; el reservado permanece intacto.
- AC3: las unidades aceptadas, también dañadas, abonan el precio vendido; el transporte no se abona.
- AC4: un reintento exacto no duplica efectos y una segunda recepción incompatible se rechaza.
- AC5: un fallo en una línea posterior revierte las anteriores, el estado, el crédito efectivo y la auditoría.

## EXP-205 · Emitir facturas operativas con datos históricos

**Tipo / componente:** Story / Facturación. **Prioridad:** P1.

**Necesidad:** Documentar una deuda por pedido completado sin que ediciones posteriores de cliente o catálogo alteren el documento.

**Precondiciones:** Pedido COMPLETED. **Alcance:** registro interno en EUR con productos, envío, cliente y vencimiento.

**Criterios de aceptación**

- AC1: solo existe una factura por pedido; repetir los mismos términos recupera la original, otros términos dan 409.
- AC2: el vencimiento está entre hoy UTC y 90 días; una entrada inválida no deja factura ni auditoría.
- AC3: nombre, identificación y dirección del cliente y los importes quedan fijados al emitir.
- AC4: el saldo descuenta únicamente devoluciones RECEIVED y asientos vigentes.
- AC5: el contrato no realiza cobros externos ni calcula impuestos no definidos por el dominio.

## EXP-206 · Conciliar cobros, créditos y reembolsos

**Tipo / componente:** Story / Tesorería operativa. **Prioridad:** P1.

**Necesidad:** Registrar cobros parciales, devolver importes abonados tras devoluciones y corregir asientos manteniendo trazabilidad.

**Precondiciones:** Factura existente; ADMIN u OPERATIONS para registrar, ADMIN para revertir.

**Criterios de aceptación**

- AC1: no se cobra más que el saldo ni se reembolsa más que el crédito; la regla se conserva bajo concurrencia.
- AC2: UUID y payload identifican un asiento; el replay devuelve el mismo registro incluso si fue revertido, sin reactivarlo.
- AC3: la reversión conserva asiento, fecha y motivo; otro motivo para una reversión ya aplicada da 409.
- AC4: no se revierte un cobro si sus reembolsos dejarían el total neto cobrado negativo.
- AC5: un asiento solicitado mediante la factura incorrecta devuelve 404; los rechazos no crean efectos persistidos.

## EXP-207 · Consultar deuda y antigüedad sin multiplicar importes

**Tipo / componente:** Story / Reporting. **Prioridad:** P2.

**Necesidad:** Operaciones necesita saldos por factura/cliente y tramos de vencimiento para priorizar el seguimiento.

**Criterios de aceptación**

- AC1: dos pagos y dos devoluciones no multiplican el total por cardinalidad del JOIN.
- AC2: el listado conserva conteo, filtros de cliente/estado y orden vencimiento/id entre páginas.
- AC3: deuda vencida, no vencida y créditos reembolsables se separan sin compensar clientes distintos.
- AC4: `asOf` clasifica los saldos actuales y la respuesta lo declara mediante `CURRENT_BALANCE`.
- AC5: una factura con saldo cero queda SETTLED y no incrementa importes pendientes.

## EXP-208 · Proponer reposición considerando suministro pendiente

**Tipo / componente:** Story / Planificación de inventario. **Prioridad:** P1.

**Necesidad:** Evitar tanto roturas de stock como compras duplicadas cuando ya hay mercancía pendiente de llegar.

**Precondiciones:** Producto con política activa, mínimo y objetivo válidos; proveedor preferido opcional.

**Criterios de aceptación**

- AC1: `available + incoming < minimum` genera una propuesta por `target − available − incoming`; igualdad al mínimo no genera compra.
- AC2: solo SUBMITTED/PARTIALLY_RECEIVED aportan unidades aún pendientes, sin contar borradores, cancelados o unidades recibidas.
- AC3: producto activo sin fila de inventario se trata como saldo cero; productos/políticas inactivos quedan fuera.
- AC4: política versionada rechaza actualizaciones obsoletas; proveedor inactivo no produce propuesta ejecutable.
- AC5: consultar propuestas no escribe compras; filtros, total y páginas corresponden a la misma población.

## EXP-209 · Restaurar la compatibilidad de la base integrada

**Tipo / componente:** Bug / Catálogo. **Prioridad:** P1.

**Observado:** El CI del SHA inicial falló en `catalogSearchTreatsSkuAndNamePunctuationLiterally`; la ordenación `active` quedó omitida después de integrar ambos contratos de catálogo.

**Impacto:** La búsqueda devuelve referencias adicionales y una ordenación admitida por el contrato de integración devuelve error.

**Criterios de aceptación**

- AC1: búsqueda por SKU con `_` identifica su texto literal y conserva la búsqueda por nombre.
- AC2: se admiten las ordenaciones SKU y activo, junto con filtro y paginación estable.
- AC3: las ordenaciones desconocidas continúan devolviendo 400.
- AC4: se mantienen las pruebas y assertions existentes y se ejecuta una regresión que combina ambos contratos.

## EXP-210 · Fijar base, trazabilidad y criterios de futuros lotes

**Tipo / componente:** Task / Laboratorio. **Prioridad:** P1.

**Criterios de aceptación**

- AC1: publicar la ampliación sana y registrar su SHA remoto, comandos y resultado real de validación.
- AC2: conservar el lote ya entregado el 10/11 y no enviar otro correo ni generar bugs/conflictos adicionales hoy.
- AC3: mantener horario, destinatario y dificultad de la tarea; añadir revisión previa y antirrepetición por mecanismo/invariante.
- AC4: cada ticket futuro contiene contexto operativo mínimo, observado/esperado y criterios verificables sin causa ni solución.
- AC5: conservar histórico y contador acreditado de referencias; un push no cierra tareas del alumno automáticamente.
