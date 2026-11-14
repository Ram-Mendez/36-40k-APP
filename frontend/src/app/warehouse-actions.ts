import type { ActionDefinition, Section } from './actions';

type Field = ActionDefinition['fields'][number];
const id = (label = 'ID'): Field => ['id', label, '', 'number'];
const product: Field = ['productId', 'Producto', '', 'number'];
const page: Field[] = [['page', 'Página (empieza en 0)', '0', 'number'], ['size', 'Resultados por página', '20', 'number']];
const requestId: Field = ['requestId', 'ID de operación (conservar al reintentar)', ''];
const quantity: Field = ['quantity', 'Unidades', '1', 'number'];

function operation(actionId: string, title: string, method: string, path: string,
                   fields: Field[] = [], body?: string[], query?: string[]): ActionDefinition {
  return { id: actionId, title, method: `${method} ${path}`, path, fields, body, query,
    values: Object.fromEntries(fields.map(field => [field[0], field[2] ?? ''])) };
}

function list(actionId: string, title: string, path: string, filters: Field[] = [], paginated = true): ActionDefinition {
  const fields = paginated ? [...filters, ...page] : filters;
  return operation(actionId, title, 'GET', path, fields, undefined,
    fields.filter(field => !path.includes(`{${field[0]}}`)).map(field => field[0]));
}

function json(actionId: string, title: string, method: string, path: string, fields: Field[], template: object): ActionDefinition {
  return { ...operation(actionId, title, method, path, [...fields,
    ['payload', 'Datos de la operación (JSON editable)', JSON.stringify(template, null, 2), 'textarea']]), jsonBody: true };
}

function dateAfter(days: number): string {
  const date = new Date();
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

/** Contratos de negocio existentes: la recepción, no la compra en borrador, aumenta las existencias. */
export function warehouseSections(): Section[] {
  const suppliers = '/api/v1/suppliers', purchases = '/api/v1/purchase-orders';
  const returns = '/api/v1/returns', invoices = '/api/v1/billing/invoices';
  const replenishment = '/api/v1/replenishment';
  const supplierFields: Field[] = [
    ['name', 'Nombre del proveedor', ''], ['taxId', 'Identificación fiscal', ''],
    ['email', 'Correo electrónico', '', 'email'], ['leadTimeDays', 'Plazo de entrega (días)', '7', 'number'],
  ];
  const settlementFields: Field[] = [id('Factura'), requestId, ['amount', 'Importe (€)', '1.00', 'number'],
    ['reference', 'Referencia del cobro o reembolso', '']];
  return [
    { id: 'profile', label: 'Mi cuenta', icon: '◉', actions: [
      operation('profile-update', 'Cambiar usuario y contraseña (cierra las sesiones)', 'PUT', '/api/v1/auth/user',
        [['username', 'Nuevo nombre de usuario', ''], ['currentPassword', 'Contraseña actual', '', 'password'],
          ['newPassword', 'Nueva contraseña (mínimo 12 caracteres)', '', 'password']], ['username', 'currentPassword', 'newPassword']),
    ] },
    { id: 'suppliers', label: 'Proveedores', icon: '↪', actions: [
      list('supplier-list', 'Consultar proveedores', suppliers, [
        ['search', 'Buscar por nombre o identificación', ''], ['active', 'Activo (true / false)', ''],
        ['sort', 'Ordenar por', 'name'], ['direction', 'Dirección (asc / desc)', 'asc']]),
      operation('supplier-create', 'Crear proveedor', 'POST', suppliers, supplierFields, ['name', 'taxId', 'email', 'leadTimeDays']),
      operation('supplier-get', 'Consultar proveedor', 'GET', `${suppliers}/{id}`, [id('Proveedor')]),
      operation('supplier-update', 'Actualizar proveedor', 'PUT', `${suppliers}/{id}`,
        [id('Proveedor'), ...supplierFields, ['version', 'Versión actual del proveedor', '0', 'number']],
        ['name', 'taxId', 'email', 'leadTimeDays', 'version']),
      operation('supplier-delete', 'Desactivar proveedor', 'DELETE', `${suppliers}/{id}`, [id('Proveedor')]),
    ] },
    { id: 'purchases', label: 'Compras y recepción', icon: '▦', actions: [
      list('purchase-list', 'Consultar compras', purchases, [
        ['supplierId', 'Proveedor', '', 'number'], ['status', 'Estado de la compra', ''],
        ['dueBefore', 'Entrega prevista antes de', '', 'date'], ['sort', 'Ordenar por', 'createdAt'],
        ['direction', 'Dirección (asc / desc)', 'desc']]),
      operation('purchase-create', 'Crear compra en borrador', 'POST', purchases,
        [['supplierId', 'Proveedor activo', '', 'number'], ['expectedOn', 'Fecha prevista de entrega', dateAfter(7), 'date']],
        ['supplierId', 'expectedOn']),
      operation('purchase-get', 'Consultar compra y líneas', 'GET', `${purchases}/{id}`, [id('Compra')]),
      operation('purchase-line-set', 'Fijar producto y cantidad de compra', 'PUT', `${purchases}/{id}/lines/{productId}`,
        [id('Compra en borrador'), product, quantity, ['unitCost', 'Coste por unidad (€)', '1.00', 'number']], ['quantity', 'unitCost']),
      operation('purchase-line-delete', 'Quitar producto de la compra', 'DELETE', `${purchases}/{id}/lines/{productId}`,
        [id('Compra en borrador'), product]),
      operation('purchase-submit', 'Enviar compra al proveedor', 'POST', `${purchases}/{id}/submit`, [id('Compra en borrador')]),
      operation('purchase-cancel', 'Cancelar compra sin recepciones', 'POST', `${purchases}/{id}/cancel`, [id('Compra')]),
      json('purchase-receive', 'Recibir mercancía del proveedor', 'POST', `${purchases}/{id}/receipts`, [id('Compra enviada')],
        { requestId: 'GENERAR_UUID', deliveryReference: 'ALBARAN-001', lines: [{ productId: 1, quantity: 1 }] }),
      list('purchase-receipts', 'Consultar recepciones de una compra', `${purchases}/{id}/receipts`, [id('Compra')]),
      operation('purchase-receipt', 'Consultar una recepción', 'GET', `${purchases}/{id}/receipts/{receiptId}`,
        [id('Compra'), ['receiptId', 'Recepción', '', 'number']]),
    ] },
    { id: 'returns', label: 'Devoluciones e inspección', icon: '↶', actions: [
      list('return-list', 'Consultar devoluciones', returns,
        [['orderId', 'Pedido vendido', '', 'number'], ['status', 'Estado de la devolución', ''], ['sort', 'Ordenar por', 'createdAt,desc']]),
      json('return-create', 'Solicitar devolución de productos vendidos', 'POST', returns, [],
        { orderId: 1, requestId: 'GENERAR_UUID', reason: 'Motivo de la devolución', lines: [{ productId: 1, quantity: 1 }] }),
      operation('return-get', 'Consultar devolución', 'GET', `${returns}/{id}`, [id('Devolución')]),
      operation('return-approve', 'Aprobar devolución', 'POST', `${returns}/{id}/approve`,
        [id('Devolución'), ['version', 'Versión actual de la devolución', '0', 'number'], ['reason', 'Motivo de la aprobación', '', 'textarea']], ['version', 'reason']),
      operation('return-reject', 'Rechazar devolución', 'POST', `${returns}/{id}/reject`,
        [id('Devolución'), ['version', 'Versión actual de la devolución', '0', 'number'], ['reason', 'Motivo del rechazo', '', 'textarea']], ['version', 'reason']),
      json('return-receive', 'Recibir e inspeccionar devolución', 'POST', `${returns}/{id}/receive`, [id('Devolución aprobada')],
        { requestId: 'GENERAR_UUID', version: 1, lines: [{ productId: 1, restockQuantity: 1, damagedQuantity: 0 }] }),
    ] },
    { id: 'billing', label: 'Facturas, cobros y reembolsos', icon: '€', actions: [
      operation('invoice-create', 'Emitir factura de pedido completado', 'POST', invoices,
        [['orderId', 'Pedido completado', '', 'number'], ['dueDate', 'Fecha de vencimiento', dateAfter(30), 'date']], ['orderId', 'dueDate']),
      list('invoice-list', 'Consultar facturas', invoices, [['customerId', 'Cliente', '', 'number'], ['status', 'Estado de la factura', '']]),
      operation('invoice-get', 'Consultar factura y saldo', 'GET', `${invoices}/{id}`, [id('Factura')]),
      operation('invoice-payment', 'Registrar cobro', 'POST', `${invoices}/{id}/payments`, settlementFields, ['requestId', 'amount', 'reference']),
      operation('invoice-refund', 'Registrar reembolso', 'POST', `${invoices}/{id}/refunds`, settlementFields, ['requestId', 'amount', 'reference']),
      list('invoice-entries', 'Consultar cobros y reembolsos', `${invoices}/{id}/entries`, [id('Factura')]),
      operation('invoice-entry', 'Consultar asiento de factura', 'GET', `${invoices}/{id}/entries/{entryId}`,
        [id('Factura'), ['entryId', 'Asiento', '', 'number']]),
      operation('invoice-reverse', 'Anular asiento conservando su historial (ADMIN)', 'POST', `${invoices}/{id}/entries/{entryId}/reverse`,
        [id('Factura'), ['entryId', 'Asiento', '', 'number'], ['reason', 'Motivo de la anulación', '', 'textarea']], ['reason']),
      list('invoice-credits', 'Consultar abonos por devoluciones', `${invoices}/{id}/credits`, [id('Factura')]),
      list('receivables', 'Consultar saldos pendientes y vencimientos', '/api/v1/billing/receivables',
        [['asOf', 'Fecha de referencia', '', 'date'], ['customerId', 'Cliente', '', 'number']], false),
    ] },
    { id: 'replenishment', label: 'Reposición de existencias', icon: '◈', actions: [
      list('policies', 'Consultar políticas de reposición', `${replenishment}/policies`,
        [product, ['preferredSupplierId', 'Proveedor preferente', '', 'number'], ['active', 'Activa (true / false)', ''], ['search', 'Buscar producto', '']]),
      operation('policy-get', 'Consultar política de un producto', 'GET', `${replenishment}/policies/{productId}`, [product]),
      json('policy-set', 'Configurar reposición (añadir versión al actualizar)', 'PUT', `${replenishment}/policies/{productId}`, [product],
        { minimumStock: 5, targetStock: 15, preferredSupplierId: null, active: true }),
      list('proposals', 'Consultar propuestas de compra', `${replenishment}/proposals`,
        [product, ['preferredSupplierId', 'Proveedor preferente', '', 'number'], ['search', 'Buscar producto', '']]),
    ] },
  ];
}
