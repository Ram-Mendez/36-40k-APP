import { HttpClient, HttpParams } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { firstValueFrom, timeout } from 'rxjs';
import { ApiService } from './api.service';
import { PageResponse } from './models';
import { Audit, Balance, Customer, Movement, Order, Product, Reservation, Sale, User } from './operations.models';
import { createDemoData } from './demo-data';

@Injectable({ providedIn: 'root' })
export class WorkspaceService {
  private readonly http = inject(HttpClient);
  private readonly api = inject(ApiService);
  private generation = 0;
  readonly demo = signal(!this.api.accessToken());
  readonly loading = signal(false);
  readonly error = signal('');
  readonly sectionErrors = signal<Record<string, string>>({});
  readonly orders = signal<Order[]>([]);
  readonly products = signal<Product[]>([]);
  readonly customers = signal<Customer[]>([]);
  readonly balances = signal<Balance[]>([]);
  readonly movements = signal<Movement[]>([]);
  readonly reservations = signal<Reservation[]>([]);
  readonly sales = signal<Sale[]>([]);
  readonly audit = signal<Audit[]>([]);
  readonly users = signal<User[]>([]);
  readonly syncedAt = signal<Date | null>(null);
  readonly lowStock = computed(() => this.balances().filter(b => b.available <= 10 && this.products().some(p => p.id === b.productId && p.active)));
  readonly pending = computed(() => this.orders().filter(o => o.status === 'CONFIRMED'));
  readonly completed = computed(() => this.orders().filter(o => o.status === 'COMPLETED'));
  readonly revenue = computed(() => this.completed().reduce((sum, o) => sum + o.total, 0));

  constructor() {
    if (this.demo()) this.loadDemo();
    else void this.load();
  }

  loadDemo(): void {
    this.generation++;
    const d = createDemoData();
    this.demo.set(true);
    this.loading.set(false);
    this.error.set('');
    this.sectionErrors.set({});
    this.orders.set(d.orders); this.products.set(d.products); this.customers.set(d.customers);
    this.balances.set(d.balances); this.movements.set(d.movements); this.reservations.set(d.reservations);
    this.sales.set(d.customers.map(c => { const orders = d.orders.filter(o => o.customerId === c.id && o.status === 'COMPLETED'); return {
      customerId: c.id, customerName: c.name, completedOrders: orders.length,
      netSales: orders.reduce((a, o) => a + o.total, 0), shippingCost: orders.reduce((a, o) => a + (o.shippingCost ?? 0), 0),
    }; }));
    this.audit.set(d.movements.slice(0, 12).map(m => ({ id: m.id, actor: 'demo.team', action: m.kind,
      entityType: 'ORDER', entityId: String(m.orderId), details: m.reason, requestId: `demo-${m.id}`, createdAt: m.createdAt })));
    this.users.set([{ id: 1, username: 'demo.team', role: 'ADMIN', active: true, createdAt: new Date().toISOString() }]);
    this.syncedAt.set(null);
  }

  async load(): Promise<void> {
    const generation = ++this.generation;
    this.demo.set(false); this.loading.set(true); this.error.set(''); this.sectionErrors.set({});
    // Clear the demo data before loading live records.
    this.orders.set([]); this.products.set([]); this.customers.set([]); this.balances.set([]);
    this.movements.set([]); this.reservations.set([]); this.sales.set([]); this.audit.set([]); this.users.set([]);
    this.syncedAt.set(null);
    const tasks = [
      this.collect<Order>('/orders').then(v => { if (generation === this.generation) this.orders.set(v); }),
      this.collect<Product>('/products').then(v => { if (generation === this.generation) this.products.set(v); }),
      this.collect<Customer>('/customers').then(v => { if (generation === this.generation) this.customers.set(v); }),
      this.collect<Balance>('/inventory').then(v => { if (generation === this.generation) this.balances.set(v); }),
      this.collect<Movement>('/inventory/movements').then(v => { if (generation === this.generation) this.movements.set(v); }),
      this.collect<Reservation>('/inventory/reservations').then(v => { if (generation === this.generation) this.reservations.set(v); }),
    ];
    const results = await Promise.allSettled(tasks);
    if (generation !== this.generation) return;
    const names = ['Orders', 'Products', 'Customers', 'Inventory', 'Movements', 'Reservations'];
    const failures = results.flatMap((r, i) => r.status === 'rejected' ? [`${names[i]}: ${this.api.errorMessage(r.reason)}`] : []);
    if (failures.length) this.error.set(failures.join(' '));
    else this.syncedAt.set(new Date());
    this.loading.set(false);
  }

  async loadExtra(section: string): Promise<void> {
    if (this.demo()) return;
    const generation = this.generation;
    try {
      if (section === 'reports') { const result = await this.collect<Sale>('/reports/sales'); if (generation === this.generation) this.sales.set(result); }
      if (section === 'activity') { const result = await this.collect<Audit>('/audit'); if (generation === this.generation) this.audit.set(result); }
      if (section === 'settings') { const result = await this.collect<User>('/users'); if (generation === this.generation) this.users.set(result); }
      if (generation === this.generation) this.sectionErrors.update(errors => ({ ...errors, [section]: '' }));
    } catch (error) {
      if (generation === this.generation) this.sectionErrors.update(errors => ({ ...errors, [section]: this.api.errorMessage(error) }));
    }
  }

  private async collect<T>(path: string): Promise<T[]> {
    const content: T[] = [];
    let page = 0;
    let totalPages = 1;
    do {
      const params = new HttpParams().set('page', page).set('size', 100);
      const result = await firstValueFrom(this.http.get<PageResponse<T>>(`/api/v1${path}`, { params }).pipe(timeout(15000)));
      content.push(...result.content);
      totalPages = result.totalPages;
      page++;
    } while (page < totalPages);
    return content;
  }
}
