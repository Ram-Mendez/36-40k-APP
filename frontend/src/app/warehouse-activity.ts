/** Visual explanations of API operations; quantities here never update real stock. */
export type WarehouseZone = 'orders' | 'stock' | 'receiving' | 'packing' | 'dispatch' | 'returns' | 'office' | 'control';
export type WarehouseActivityKind = 'read' | 'reserve' | 'release' | 'inbound' | 'outbound' | 'inspect' | 'adjust' | 'money' | 'document' | 'access';

export interface WarehouseActivity {
  id: string;
  method: string;
  path: string;
  title: string;
  actor: string;
  from: WarehouseZone;
  to: WarehouseZone;
  effect: string;
  state: 'pending' | 'success' | 'error';
  status: number | null;
  startedAt: number;
  finishedAt: number | null;
  quantity: number | null;
  entityId: string | null;
  error: string | null;
  demo: boolean;
  kind?: WarehouseActivityKind;
  cargo?: 'none' | 'boxes' | 'document';
  replayed?: boolean;
  amount?: number | null;
  productId?: number | null;
}

type Description = Pick<WarehouseActivity, 'title' | 'actor' | 'from' | 'to' | 'effect' | 'quantity' | 'entityId' | 'kind' | 'cargo' | 'amount' | 'productId'>;
type SafeRequest = { quantity: number | null; amount: number | null; restock: number | null; damaged: number | null };

const record = (value: unknown): Record<string, unknown> => value !== null && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {};
const numeric = (value: unknown): number | null => typeof value === 'number' && Number.isFinite(value) ? value : null;
const identifier = (value: unknown): string | null => typeof value === 'number' && Number.isSafeInteger(value) && value > 0 ? String(value) : null;
const amountText = (value: number): string => new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' }).format(value);
const units = (value: number | null): string => value === null ? 'las unidades del documento' : `${value} unidades`;

function lineTotal(value: unknown, field: string): number | null {
  const lines = record(value)['lines'];
  if (!Array.isArray(lines) || lines.length === 0) return null;
  const values = lines.map(line => numeric(record(line)[field]));
  return values.some(item => item === null) ? null : values.reduce<number>((total, item) => total + (item ?? 0), 0);
}

function safeRequest(body: unknown): SafeRequest {
  const data = record(body);
  return { quantity: numeric(data['quantity']) ?? lineTotal(body, 'quantity'), amount: numeric(data['amount']), restock: lineTotal(body, 'restockQuantity'), damaged: lineTotal(body, 'damagedQuantity') };
}

/** Strip search text, credentials and arbitrary path values before retaining a route. */
export function warehousePath(raw: string): string {
  const path = raw.split(/[?#]/, 1)[0];
  const allowed = new Set(['api', 'v1', 'orders', 'lines', 'confirm', 'cancel', 'complete', 'inventory', 'movements', 'reservations', 'adjustments', 'purchase-orders', 'submit', 'receipts', 'returns', 'approve', 'reject', 'receive', 'billing', 'invoices', 'payments', 'refunds', 'entries', 'reverse', 'credits', 'receivables', 'products', 'customers', 'suppliers', 'replenishment', 'policies', 'proposals', 'reports', 'sales', 'low-stock', 'audit', 'users', 'auth', 'login', 'logout', 'refresh', 'password', 'user']);
  if (!path.startsWith('/api/v1/')) return '/api/v1/{operacion}';
  return path.split('/').map(part => !part || allowed.has(part) || /^\d{1,19}$/.test(part) ? part : '{valor}').join('/').slice(0, 240);
}

function describe(method: string, path: string, request: SafeRequest, response?: unknown): Description {
  const parts = path.split('/').filter(Boolean);
  const family = parts[2];
  const entityId = /^\d+$/.test(parts[3] ?? '') ? parts[3] : identifier(record(response)['id']);
  const productMatch = path.match(/\/(?:inventory|products|policies)\/(\d+)|\/lines\/(\d+)/);
  const productId = productMatch ? Number(productMatch[1] ?? productMatch[2]) : null;
  const base: Description = { title: 'Operación de gestión', actor: 'Administración', from: 'control', to: 'office', effect: 'Operación registrada. Consulta los datos actualizados.', quantity: null, entityId, kind: 'document', cargo: 'document', amount: null, productId };
  const change = (fields: Partial<Description>): Description => ({ ...base, ...fields });
  const returnedQuantity = lineTotal(response, 'quantity');
  const quantity = returnedQuantity ?? request.quantity;
  if (method === 'GET' || method === 'HEAD' || method === 'OPTIONS') {
    const readZones: Record<string, WarehouseZone> = { orders: 'orders', inventory: 'stock', products: 'stock', 'purchase-orders': 'receiving', suppliers: 'receiving', returns: 'returns', billing: 'office', customers: 'orders', users: 'control', auth: 'control', audit: 'control', reports: 'office', replenishment: 'office' };
    const readNames: Record<string, string> = { orders: 'Consultar pedidos', inventory: 'Consultar existencias', products: 'Consultar catálogo', 'purchase-orders': 'Consultar compras', suppliers: 'Consultar proveedores', returns: 'Consultar devoluciones', billing: 'Consultar facturación', customers: 'Consultar clientes', users: 'Consultar usuarios', audit: 'Consultar auditoría', reports: 'Consultar informes', replenishment: 'Consultar reposición' };
    return change({ title: readNames[family] ?? 'Consultar información', actor: 'Consulta', to: readZones[family] ?? 'control', kind: 'read', effect: 'Lectura de información. No mueve mercancía ni modifica existencias.' });
  }
  if (family === 'orders') {
    if (method === 'POST' && path.endsWith('/confirm')) return change({ title: 'Confirmar y reservar pedido', actor: 'Operario de almacén', from: 'orders', to: 'stock', quantity, kind: 'reserve', cargo: 'none', effect: `Se reservan ${units(quantity)}. Permanecen físicamente en las estanterías.` });
    if (method === 'POST' && path.endsWith('/complete')) return change({ title: 'Completar pedido', actor: 'Preparador de pedidos', from: 'stock', to: 'dispatch', quantity, kind: 'outbound', cargo: quantity === 0 ? 'none' : 'boxes', effect: `Se registra la salida de ${units(quantity)} y se consumen sus reservas. El recorrido representa la salida registrada, no el seguimiento del transportista.` });
    if (method === 'POST' && path.endsWith('/cancel')) return change({ title: 'Cancelar pedido', actor: 'Operario de almacén', from: 'orders', to: 'stock', kind: 'release', cargo: 'none', effect: 'Se cancela el pedido y se liberan las reservas que existían. El stock físico no cambia.' });
    return change({ title: path.includes('/lines/') ? (method === 'DELETE' ? 'Quitar línea del pedido' : 'Editar línea del pedido') : method === 'POST' ? 'Crear pedido en borrador' : 'Editar pedido', actor: 'Atención al cliente', from: 'control', to: 'orders', effect: 'Se actualiza el documento del pedido. El borrador no reserva ni saca mercancía.' });
  }
  if (family === 'purchase-orders') {
    if (method === 'POST' && path.endsWith('/receipts')) return change({ title: 'Recibir mercancía del proveedor', actor: 'Operario de recepción', from: 'receiving', to: 'stock', quantity, kind: 'inbound', cargo: quantity === 0 ? 'none' : 'boxes', effect: `Se registra la entrada de ${units(quantity)}. La recepción puede ser parcial.` });
    if (path.endsWith('/submit')) return change({ title: 'Enviar compra al proveedor', actor: 'Responsable de compras', from: 'office', to: 'receiving', effect: 'Compra enviada al proveedor. Las existencias aumentan cuando se registra la recepción.' });
    if (path.endsWith('/cancel')) return change({ title: 'Cancelar compra', actor: 'Responsable de compras', from: 'receiving', to: 'office', effect: 'Compra cancelada. No se ha registrado entrada ni salida de mercancía.' });
    return change({ title: path.includes('/lines/') ? 'Actualizar líneas de compra' : 'Crear borrador de compra', actor: 'Responsable de compras', from: 'office', to: 'receiving', effect: 'Documento de compra actualizado. El stock todavía no aumenta.' });
  }
  if (family === 'returns') {
    if (method === 'POST' && path.endsWith('/receive')) {
      const restock = lineTotal(response, 'restockQuantity') ?? request.restock;
      const damaged = lineTotal(response, 'damagedQuantity') ?? request.damaged;
      return change({ title: 'Inspeccionar y recibir devolución', actor: 'Operario de devoluciones', from: 'returns', to: restock === 0 ? 'returns' : 'stock', kind: 'inspect', quantity: restock, cargo: restock === 0 ? 'none' : 'boxes', effect: `Se reponen ${units(restock)} aptas para venta.${damaged === null ? '' : ` Se separan ${damaged} unidades dañadas, sin sumarlas al stock.`}` });
    }
    return change({ title: path.endsWith('/approve') ? 'Aprobar devolución' : path.endsWith('/reject') ? 'Rechazar devolución' : 'Solicitar devolución', actor: 'Atención al cliente', from: 'orders', to: 'returns', effect: 'Se registra la solicitud o decisión. La mercancía solo vuelve al stock al recibirla e inspeccionarla.' });
  }
  if (family === 'inventory' && path.endsWith('/adjustments')) {
    const adjustment = numeric(record(response)['quantity']) ?? request.quantity;
    return change({ title: 'Ajustar existencias', actor: 'Responsable de inventario', from: adjustment !== null && adjustment < 0 ? 'stock' : 'control', to: adjustment !== null && adjustment < 0 ? 'control' : 'stock', kind: 'adjust', quantity: adjustment, cargo: 'none', effect: `Recuento corregido${adjustment === null ? '' : ` en ${adjustment > 0 ? '+' : ''}${adjustment} unidades`}. El saldo mostrado procede del backend, no de esta animación.` });
  }
  if (family === 'billing') {
    const amount = numeric(record(response)['amount']) ?? request.amount;
    const refund = path.endsWith('/refunds');
    if (refund || path.endsWith('/payments')) return change({ title: refund ? 'Registrar reembolso' : 'Registrar cobro', actor: 'Administración de caja', from: 'orders', to: 'office', kind: 'money', amount, effect: `${refund ? 'Reembolso' : 'Cobro'} registrado${amount === null ? '' : `: ${amountText(amount)}`}. Cambia el saldo contable, no las existencias.` });
    return change({ title: path.endsWith('/reverse') ? 'Revertir asiento contable' : 'Emitir factura', actor: 'Administración de caja', from: 'orders', to: 'office', kind: 'money', effect: 'Registro contable actualizado. No mueve mercancía.' });
  }
  if (family === 'replenishment') return change({ title: path.includes('/draft') ? 'Preparar propuesta de compra' : 'Configurar reposición', actor: 'Responsable de compras', from: 'stock', to: 'office', effect: 'Se actualiza la planificación de reposición. No se recibe mercancía con esta operación.' });
  if (family === 'auth' || family === 'users') return change({ title: family === 'users' ? 'Gestionar acceso de usuarios' : path.endsWith('/login') ? 'Entrar al almacén' : path.endsWith('/logout') ? 'Cerrar sesión' : 'Actualizar sesión o cuenta', actor: 'Control de acceso', from: 'control', to: 'control', kind: 'access', cargo: 'none', effect: 'Acceso o cuenta actualizados. No se guardan contraseñas ni tokens en este historial.', entityId: null });
  if (family === 'products' || family === 'customers' || family === 'suppliers') {
    const label = { products: 'producto', customers: 'cliente', suppliers: 'proveedor' }[family];
    return change({ title: `${method === 'DELETE' ? 'Desactivar' : method === 'POST' ? 'Crear' : 'Actualizar'} ${label}`, to: family === 'products' ? 'stock' : family === 'suppliers' ? 'receiving' : 'orders', effect: 'Ficha actualizada. Dar de alta o editar una ficha no cambia las existencias.' });
  }
  return base;
}

const messages: Record<string, string> = {
  INSUFFICIENT_STOCK: 'No hay existencias disponibles suficientes para reservar el pedido.',
  INSUFFICIENT_AVAILABLE_STOCK: 'El ajuste dejaría menos existencias que las unidades reservadas.',
  INVALID_ORDER_STATE: 'El estado actual del pedido no permite esta operación.',
  EMPTY_ORDER: 'Añade al menos una línea antes de confirmar el pedido.',
  CONCURRENT_MODIFICATION: 'Otra operación modificó este registro. Actualiza los datos antes de reintentar.',
  ORDER_CHANGED: 'El pedido cambió durante la operación. Actualiza su estado.',
  IDEMPOTENCY_CONFLICT: 'Este identificador de petición ya se usó con datos diferentes.',
  RETURN_QUANTITY_EXCEEDED: 'La devolución supera las unidades que se pueden devolver.',
  INVALID_RETURN_STATE: 'El estado de la devolución no permite esta operación.',
  RETURN_LINES_MISMATCH: 'Las cantidades inspeccionadas no coinciden con la devolución.',
  STOCK_CAPACITY_EXCEEDED: 'La recepción supera la capacidad de existencias admitida.',
  ACCESS_DENIED: 'Tu usuario no tiene permiso para esta operación.',
  VALIDATION_ERROR: 'Revisa los campos y cantidades de la petición.',
  INVALID_REQUEST: 'La petición contiene datos no válidos.',
  INVALID_SORT: 'El criterio de ordenación no está admitido.',
};

export function warehouseError(error: unknown): { status: number; message: string } {
  const data = record(error);
  const status = numeric(data['status']) ?? 0;
  const problem = record(data['error']);
  const code = problem['code'];
  const known = typeof code === 'string' ? messages[code] : undefined;
  const fallback: Record<number, string> = { 0: 'No se pudo confirmar la respuesta. Comprueba la conexión y consulta el estado antes de reintentar.', 400: 'Revisa los campos y cantidades de la petición.', 401: 'Inicia sesión de nuevo para continuar.', 403: 'Tu usuario no tiene permiso para esta operación.', 404: 'No se encontró el recurso solicitado.', 409: 'La operación entra en conflicto con el estado actual. Actualiza los datos.', 422: 'Los datos no cumplen las reglas de esta operación.' };
  return { status, message: known ?? fallback[status] ?? 'El servidor no pudo confirmar la operación. Consulta su estado antes de reintentar.' };
}

/** In-memory session log. Request/response objects and credentials are never retained. */
export class WarehouseActivityLog {
  private sequence = 0;
  private rows: WarehouseActivity[] = [];
  private readonly requests = new Map<string, SafeRequest>();
  private readonly applied = new Set<string>();
  get activities(): WarehouseActivity[] { return this.rows; }

  begin(method: string, rawPath: string, body: unknown, demo = false): string {
    const id = `warehouse-${Date.now()}-${++this.sequence}`;
    const path = warehousePath(rawPath), verb = method.toUpperCase().split(' ', 1)[0];
    const request = safeRequest(body);
    const description = describe(verb, path, request);
    this.requests.set(id, request);
    const event: WarehouseActivity = { ...description, id, method: verb, path, state: 'pending', status: null, startedAt: Date.now(), finishedAt: null, error: null, demo, cargo: 'none', replayed: false, effect: 'Petición enviada. Esperando la respuesta del backend.' };
    this.rows = [event, ...this.rows].slice(0, 60);
    const retained = new Set(this.rows.map(row => row.id));
    for (const key of this.requests.keys()) if (!retained.has(key)) this.requests.delete(key);
    return id;
  }

  succeed(id: string, response: unknown, status: number): void {
    const event = this.rows.find(row => row.id === id);
    if (!event || event.state !== 'pending') return;
    const request = this.requests.get(id) ?? safeRequest(null);
    const description = describe(event.method, event.path, request, response);
    const key = this.replayKey(event, response);
    const replayed = key !== null && this.applied.has(key);
    if (key) {
      this.applied.add(key);
      if (this.applied.size > 300) this.applied.delete(this.applied.values().next().value!);
    }
    this.replace(id, { ...event, ...description, state: 'success', status, finishedAt: Date.now(), replayed,
      ...(replayed ? { from: description.to, cargo: 'none' as const, quantity: null, effect: 'El backend devolvió una operación ya registrada en esta sesión. No se repite el movimiento ni se suman unidades.' } : {}) });
    this.requests.delete(id);
  }

  fail(id: string, error: unknown): void {
    const event = this.rows.find(row => row.id === id);
    if (!event || event.state !== 'pending') return;
    const failure = warehouseError(error);
    this.replace(id, { ...event, state: 'error', status: failure.status, finishedAt: Date.now(), error: failure.message, cargo: 'none', quantity: null, effect: 'Operación sin confirmar. No se representa un cambio de mercancía; consulta el estado real antes de reintentar.' });
    this.requests.delete(id);
  }

  clearHistory(): void { this.rows = []; this.requests.clear(); }
  clear(): void { this.clearHistory(); this.applied.clear(); }
  private replace(id: string, value: WarehouseActivity): void { this.rows = this.rows.map(row => row.id === id ? value : row); }

  private replayKey(event: WarehouseActivity, response: unknown): string | null {
    if (event.method !== 'POST') return null;
    const data = record(response);
    const responseId = identifier(data['id']);
    let identity: string | null = null;
    if (/\/purchase-orders\/\d+\/receipts$/.test(event.path) || /\/billing\/invoices\/\d+\/(payments|refunds)$/.test(event.path)) identity = responseId;
    if (/\/returns\/\d+\/receive$/.test(event.path)) {
      const receipt = data['receiptRequestId'];
      if (typeof receipt === 'string' && /^[0-9a-f-]{36}$/i.test(receipt)) identity = receipt;
    }
    return identity ? `${event.demo ? 'demo' : 'real'}:${event.path}:${identity}` : null;
  }
}
