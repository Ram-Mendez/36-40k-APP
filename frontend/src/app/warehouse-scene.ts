import { CommonModule } from '@angular/common';
import { Component, computed, effect, inject, input, OnDestroy, output, signal, untracked } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Icon } from './icon';
import { ActionDefinition, createSections } from './actions';
import { Balance, Order } from './operations.models';
import { WorkspaceService } from './workspace.service';
import { WarehouseActivityService } from './warehouse-activity.service';
import { WarehouseActivity, WarehouseZone } from './warehouse-activity';

type Point = { x: number; y: number };
type Journey = { activity: WarehouseActivity; points: Point[]; progress: number; duration: number; replay: boolean };
type Person = { id: string; activity: WarehouseActivity; x: number; y: number; step: number; moving: boolean; replay: boolean };
interface Zone { id: WarehouseZone; name: string; role: string; icon: string; x: number; y: number; description: string; }

@Component({
  selector: 'warehouse-scene', imports: [CommonModule, FormsModule, Icon],
  templateUrl: './warehouse-scene.html', styleUrls: ['./warehouse-scene-map.scss', './warehouse-scene-panels.scss', './warehouse-scene.scss'],
})
export class WarehouseScene implements OnDestroy {
  readonly data = inject(WorkspaceService);
  readonly activity = inject(WarehouseActivityService);
  readonly action = output<{ id: string; defaults?: Record<string, string | number> }>();
  readonly connect = output<void>();
  readonly openLab = output<void>();
  readonly replayRequest = input(0);
  readonly zones: Zone[] = [
    { id: 'receiving', name: 'Recepción', role: 'Proveedor · recepcionista', icon: 'truck', x: 135, y: 170, description: 'Las compras entran cuando registras una recepción. Enviar la compra al proveedor todavía no aumenta las existencias.' },
    { id: 'stock', name: 'Estanterías', role: 'Responsable de inventario', icon: 'inventory', x: 380, y: 170, description: 'Las cajas reservadas siguen físicamente aquí. Disponibles = existencias físicas − unidades reservadas.' },
    { id: 'packing', name: 'Preparación', role: 'Operario de almacén', icon: 'box', x: 625, y: 170, description: 'Representación del trabajo de preparación. El backend no tiene una confirmación independiente para el embalaje.' },
    { id: 'dispatch', name: 'Expedición', role: 'Operario · transportista', icon: 'truck', x: 870, y: 170, description: 'Completar un pedido obtiene la cotización y registra la salida. El recorrido del camión no es un seguimiento real del transportista.' },
    { id: 'orders', name: 'Pedidos', role: 'Cliente · comercial', icon: 'orders', x: 135, y: 485, description: 'Crear y editar un pedido prepara la lista. Confirmarlo reserva sus productos. Cancelarlo libera las reservas que correspondan.' },
    { id: 'returns', name: 'Devoluciones', role: 'Inspector de calidad', icon: 'refresh', x: 380, y: 485, description: 'Una devolución aprobada aún no repone existencias. Al recibirla, solo las unidades aptas vuelven al almacén.' },
    { id: 'office', name: 'Administración', role: 'Comprador · contable', icon: 'chart', x: 625, y: 485, description: 'Facturas, pagos, reembolsos y abonos cambian los registros contables. No desplazan mercancía.' },
    { id: 'control', name: 'Control', role: 'Administrador · supervisor', icon: 'shield', x: 870, y: 485, description: 'Consultas, informes, auditoría y acceso. Leer información no mueve cajas ni cambia existencias.' },
  ];
  readonly racks = [-63, 9];
  readonly crates = [0, 1, 2, 3, 4, 5];
  readonly rollers = [0, 1, 2, 3, 4, 5, 6, 7];
  readonly idleStaff = [{ x: 380, y: 282, color: '#447bae' }, { x: 135, y: 388, color: '#9b73b5' }, { x: 870, y: 282, color: '#b08a4e' }];
  readonly selectedZoneId = signal<WarehouseZone>('stock');
  readonly selectedZone = computed(() => this.zones.find(z => z.id === this.selectedZoneId())!);
  readonly selectedOrderId = signal<number | null>(null);
  readonly selectedProductId = signal<number | null>(null);
  readonly selectedOrder = computed<Order | null>(() => this.data.orders().find(o => o.id === this.selectedOrderId()) ?? this.data.orders().find(o => o.status === 'CONFIRMED') ?? this.data.orders()[0] ?? null);
  readonly selectedBalance = computed<Balance | null>(() => this.data.balances().find(b => b.productId === this.selectedProductId()) ?? this.data.balances()[0] ?? null);
  readonly totals = computed(() => this.data.balances().reduce((sum, b) => ({ physical: sum.physical + b.onHand, reserved: sum.reserved + b.reserved, available: sum.available + b.available }), { physical: 0, reserved: 0, available: 0 }));
  readonly selectedId = signal<string | null>(null);
  readonly selectedActivity = computed<WarehouseActivity | null>(() => this.activity.activities().find(a => a.id === this.selectedId()) ?? this.activity.activities()[0] ?? null);
  readonly paused = signal(false);
  readonly speed = signal(1);
  readonly reducedMotion = signal(typeof matchMedia === 'function' && matchMedia('(prefers-reduced-motion: reduce)').matches);
  readonly people = signal<Person[]>([]);
  readonly queued = signal(0);
  readonly demonstration = signal('confirm');
  readonly operationSearch = signal('');
  readonly operationId = signal('');
  readonly catalog = createSections().flatMap(section => section.actions);
  readonly availableActions = computed(() => {
    const patterns: Record<WarehouseZone, RegExp> = {
      receiving: /\/(purchase-orders|suppliers)(\/|$)/, stock: /\/(inventory|products|replenishment)(\/|$)/,
      packing: /\/orders(\/|$)/, dispatch: /\/orders(\/|$)/, orders: /\/(orders|customers)(\/|$)/,
      returns: /\/returns(\/|$)/, office: /\/(billing|replenishment)(\/|$)/, control: /\/(auth|users|audit|reports)(\/|$)/,
    };
    const search = this.operationSearch().toLocaleLowerCase('es');
    return this.catalog.filter(a => (search ? `${this.operationLabel(a.id, a.title)} ${a.path}`.toLocaleLowerCase('es').includes(search) : patterns[this.selectedZoneId()].test(a.path)));
  });
  readonly chosenAction = computed<ActionDefinition | null>(() => this.availableActions().find(a => a.id === this.operationId()) ?? this.availableActions()[0] ?? null);
  readonly latestError = computed(() => this.activity.activities().find(a => a.state === 'error') ?? null);
  private readonly seen = new Set<string>();
  private readonly observed = new Map<string, WarehouseActivity['state']>();
  private journeys: Journey[] = [];
  private waiting: Journey[] = [];
  private frame = 0;
  private lastFrame = 0;
  private destroyed = false;
  private replayVersion = 0;

  constructor() {
    effect(() => {
      const events = this.activity.activities();
      for (const event of [...events].reverse()) {
        if (this.observed.get(event.id) !== event.state) { this.observed.set(event.id, event.state); this.selectedId.set(event.id); }
        if (event.state === 'pending' || this.seen.has(event.id)) continue;
        this.seen.add(event.id);
        this.selectedId.set(event.id);
        if (event.state === 'success') this.enqueue(event, false);
      }
      if (!events.length) { this.seen.clear(); this.observed.clear(); this.journeys = []; this.waiting = []; this.queued.set(0); }
      if (this.seen.size > 180) { const ids = new Set(events.map(a => a.id)); for (const id of this.seen) if (!ids.has(id)) { this.seen.delete(id); this.observed.delete(id); } }
      this.drawPeople();
    });
    effect(() => { this.reducedMotion(); this.paused(); this.startClock(); });
    effect(() => {
      const version = this.replayRequest();
      if (version > this.replayVersion) { this.replayVersion = version; untracked(() => this.replayLatest()); }
    });
  }

  ngOnDestroy(): void { this.destroyed = true; cancelAnimationFrame(this.frame); }

  selectZone(id: WarehouseZone): void { this.selectedZoneId.set(id); this.operationSearch.set(''); this.operationId.set(''); }
  zoneName(id: WarehouseZone): string { return this.zones.find(z => z.id === id)?.name ?? id; }
  zoneCount(id: WarehouseZone): string {
    if (this.data.loading()) return 'Actualizando…';
    if (id === 'stock') return `${this.format(this.totals().physical)} unidades`;
    if (id === 'orders') return `${this.data.orders().filter(o => o.status === 'DRAFT').length} borradores`;
    if (id === 'packing') return `${this.data.pending().length} confirmados`;
    if (id === 'dispatch') return `${this.data.completed().length} completados`;
    if (id === 'control') return `${this.activity.pendingCount()} peticiones en curso`;
    return ({ receiving: 'Entrada de compras', returns: 'Inspección y reposición', office: 'Facturas y cobros' } as Record<string, string>)[id] ?? '';
  }
  format(value: number): string { return new Intl.NumberFormat('es-ES', { maximumFractionDigits: 2 }).format(value); }
  money(value: number): string { return new Intl.NumberFormat('es-ES', { style: 'currency', currency: 'EUR' }).format(value); }
  floor(value: number): number { return Math.floor(value); }
  statusLabel(state: string): string { return ({ DRAFT: 'Borrador', CONFIRMED: 'Confirmado', COMPLETED: 'Completado', CANCELLED: 'Cancelado', pending: 'Esperando respuesta', success: 'Confirmada', error: 'Error' } as Record<string, string>)[state] ?? state; }
  requestLabel(event: WarehouseActivity): string { return event.status === null ? 'En curso' : event.status === 0 ? 'Sin conexión' : `HTTP ${event.status}`; }
  elapsed(event: WarehouseActivity): string { return event.finishedAt === null ? 'En curso' : `${Math.max(0, event.finishedAt - event.startedAt)} ms`; }
  hasBoxes(person: Person): boolean { return person.moving && person.activity.state === 'success' && person.activity.cargo === 'boxes' && !person.activity.replayed; }
  personColor(person: Person): string { return person.activity.state === 'error' ? '#c45757' : person.activity.demo ? '#8e63bc' : person.activity.kind === 'money' ? '#b17b39' : '#447bae'; }
  openAction(id: string, defaults: Record<string, string | number> = {}): void { this.action.emit({ id, defaults }); }
  orderAction(id: string): void { const order = this.selectedOrder(); if (order) this.openAction(id, { id: order.id }); }
  productAction(id: string): void { const balance = this.selectedBalance(); if (balance) this.openAction(id, { productId: balance.productId }); }
  runChosen(): void {
    const chosen = this.chosenAction(); if (!chosen) return;
    const defaults: Record<string, string | number> = {};
    if (chosen.path.startsWith('/api/v1/orders/') && this.selectedOrder()) defaults['id'] = this.selectedOrder()!.id;
    if (chosen.path.includes('{productId}') && this.selectedBalance()) defaults['productId'] = this.selectedBalance()!.productId;
    this.openAction(chosen.id, defaults);
  }
  runDemo(): void { this.activity.demo(this.demonstration()); }
  replayLatest(): void { const event = this.activity.activities().find(a => a.state !== 'pending'); if (event) this.replay(event); }
  replay(event: WarehouseActivity): void {
    this.selectedId.set(event.id); this.selectedZoneId.set(event.to);
    if (event.state === 'success') {
      this.journeys = this.journeys.filter(j => j.activity.id !== event.id);
      this.waiting = this.waiting.filter(j => j.activity.id !== event.id);
      this.paused.set(false); this.enqueue(event, true);
    }
    this.drawPeople();
  }
  clearHistory(): void { this.activity.clearHistory(); this.selectedId.set(null); }
  route(event: WarehouseActivity): Point[] {
    const start = this.door(event.from), end = this.door(event.to);
    if (event.from === event.to || event.replayed) return [start, start];
    const points = [start, { x: start.x, y: 332 }];
    if (event.kind === 'outbound' && event.from === 'stock' && event.to === 'dispatch') {
      const packing = this.door('packing'); points.push({ x: packing.x, y: 332 }, packing, { x: packing.x, y: 332 });
    }
    points.push({ x: end.x, y: 332 }, end);
    return points;
  }
  routePath(event: WarehouseActivity): string { return this.route(event).map((p, i) => `${i ? 'L' : 'M'}${p.x},${p.y}`).join(' '); }
  private door(id: WarehouseZone): Point { const z = this.zones.find(v => v.id === id)!; return { x: z.x, y: z.y < 300 ? 282 : 388 }; }
  private enqueue(event: WarehouseActivity, replay: boolean): void {
    const points = this.route(event);
    const length = points.slice(1).reduce((sum, p, i) => sum + Math.hypot(p.x - points[i].x, p.y - points[i].y), 0);
    this.waiting.push({ activity: event, points, progress: 0, duration: Math.max(1700, length * 8), replay });
    if (this.waiting.length > 24) this.waiting.shift();
    this.queued.set(this.waiting.length);
    this.startClock();
  }
  private startClock(): void {
    if (this.frame || this.destroyed || this.paused()) return;
    this.lastFrame = 0;
    this.frame = requestAnimationFrame(time => this.tick(time));
  }
  private tick(time: number): void {
    this.frame = 0;
    if (this.destroyed || this.paused()) return;
    const delta = this.lastFrame ? Math.min(80, time - this.lastFrame) : 0;
    this.lastFrame = time;
    while (this.journeys.length < 3 && this.waiting.length) this.journeys.push(this.waiting.shift()!);
    for (const journey of this.journeys) journey.progress += this.reducedMotion() ? 1 : delta * this.speed() / journey.duration;
    this.drawPeople();
    this.journeys = this.journeys.filter(j => j.progress < 1);
    this.queued.set(this.waiting.length);
    if (this.journeys.length || this.waiting.length) this.frame = requestAnimationFrame(next => this.tick(next));
  }
  private drawPeople(): void {
    const people: Person[] = this.journeys.map((journey, index) => {
      const pos = this.interpolate(journey.points, Math.min(1, journey.progress));
      return { id: `${journey.activity.id}-${index}`, activity: journey.activity, x: pos.x, y: pos.y, moving: journey.progress < 1 && !this.reducedMotion(), step: this.reducedMotion() ? 0 : Math.sin(journey.progress * 50) * 17, replay: journey.replay };
    });
    const pending = this.activity.activities().filter(a => a.state === 'pending').slice(0, Math.max(0, 3 - people.length));
    for (const event of pending) { const point = this.door(event.from); people.push({ id: event.id, activity: event, ...point, step: 0, moving: false, replay: false }); }
    if (!people.length) {
      const event = this.selectedActivity();
      if (event) { const point = this.door(event.state === 'success' ? event.to : event.from); people.push({ id: event.id, activity: event, ...point, step: 0, moving: false, replay: false }); }
    }
    this.people.set(people);
  }
  private interpolate(points: Point[], progress: number): Point {
    const lengths = points.slice(1).map((p, i) => Math.hypot(p.x - points[i].x, p.y - points[i].y));
    let remaining = lengths.reduce((a, b) => a + b, 0) * progress;
    for (let i = 0; i < lengths.length; i++) { if (remaining <= lengths[i]) { const ratio = lengths[i] ? remaining / lengths[i] : 0; return { x: points[i].x + (points[i + 1].x - points[i].x) * ratio, y: points[i].y + (points[i + 1].y - points[i].y) * ratio }; } remaining -= lengths[i]; }
    return points[points.length - 1];
  }
  operationLabel(id: string, fallback: string): string {
    const labels: Record<string, string> = {
      'order-create': 'Crear pedido', 'order-get': 'Consultar pedido', 'order-update': 'Cambiar cliente', 'order-confirm': 'Confirmar y reservar', 'order-cancel': 'Cancelar pedido', 'order-complete': 'Completar y despachar', orders: 'Consultar pedidos', 'line-set': 'Añadir o cambiar producto', 'line-delete': 'Quitar producto', balances: 'Consultar existencias', adjust: 'Ajustar existencias', movements: 'Consultar movimientos', reservations: 'Consultar reservas', products: 'Consultar productos', 'product-create': 'Crear producto', 'product-get': 'Consultar producto', 'product-update': 'Editar producto', 'product-delete': 'Desactivar producto', customers: 'Consultar clientes', 'customer-create': 'Crear cliente', 'customer-get': 'Consultar cliente', 'customer-update': 'Editar cliente', 'customer-delete': 'Desactivar cliente', sales: 'Informe de ventas', 'low-stock': 'Consultar existencias bajas', audit: 'Consultar auditoría', users: 'Consultar usuarios', 'user-create': 'Crear usuario', 'user-update': 'Editar usuario', 'user-delete': 'Desactivar usuario', login: 'Iniciar sesión', logout: 'Cerrar sesión', refresh: 'Renovar sesión', password: 'Cambiar contraseña',
    };
    return labels[id] ?? fallback;
  }
}
