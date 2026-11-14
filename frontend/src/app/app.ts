import { CommonModule } from '@angular/common';
import { Component, computed, ElementRef, HostListener, inject, signal, ViewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { ApiService, RequestResult } from './api.service';
import { ActionDefinition, createSections } from './actions';
import { WorkspaceService } from './workspace.service';
import { WarehouseLab } from './warehouse-lab';
import { WarehouseScene } from './warehouse-scene';
import { Icon } from './icon';
import { Order, Product, Customer } from './operations.models';

@Component({
  selector: 'app-root', imports: [CommonModule, FormsModule, Icon, WarehouseLab, WarehouseScene],
  host: { '[style.--green]': 'accent().main', '[style.--lime]': 'accent().light', '[style.--forest]': 'accent().dark' },
  templateUrl: './app.html', styleUrl: './app.scss',
})
export class App {
  readonly colors = [
    { name: 'Green', main: '#4d7456', light: '#d6eea4', dark: '#203e30' },
    { name: 'Blue', main: '#3269a1', light: '#bedfff', dark: '#204872' },
    { name: 'Violet', main: '#7854a0', light: '#e3ceff', dark: '#51366e' },
    { name: 'Pink', main: '#a04270', light: '#ffd1e6', dark: '#702e50' },
    { name: 'Orange', main: '#985121', light: '#ffdab4', dark: '#653815' },
  ] as const;
  readonly accent = signal(this.readAccent());

  private readAccent() {
    try {
      const legacyColors: Record<string, string> = { Verde: 'Green', Azul: 'Blue', Violeta: 'Violet', Rosa: 'Pink', Naranja: 'Orange' };
      const name = localStorage.getItem('nexo.accent') ?? legacyColors[localStorage.getItem('nexo.colorAcento') ?? ''];
      return this.colors.find(color => color.name === name) ?? this.colors[0];
    } catch {
      return this.colors[0];
    }
  }

  selectAccent(color: (typeof this.colors)[number]): void {
    this.accent.set(color);
    try {
      localStorage.setItem('nexo.accent', color.name);
    } catch {
      // Keep the selected color for this session when storage is unavailable.
    }
  }

  readonly data = inject(WorkspaceService);
  readonly api = inject(ApiService);
  @ViewChild('modal') private modal?: ElementRef<HTMLDialogElement>;
  readonly warehouseReplay = signal(0);
  readonly navigation = [
    { id: 'overview', label: 'Overview', icon: 'grid', group: 'workspace' },
    { id: 'warehouse', label: 'Almacén en vivo', icon: 'box', group: 'workspace' },
    { id: 'orders', label: 'Orders', icon: 'orders', group: 'workspace' },
    { id: 'inventory', label: 'Inventory', icon: 'inventory', group: 'workspace' },
    { id: 'products', label: 'Products', icon: 'box', group: 'workspace' },
    { id: 'customers', label: 'Customers', icon: 'users', group: 'workspace' },
    { id: 'reports', label: 'Reports', icon: 'chart', group: 'insight' },
    { id: 'activity', label: 'Activity', icon: 'activity', group: 'insight' },
    { id: 'lab', label: 'Warehouse lab', icon: 'layers', group: 'workspace' },
    { id: 'blueprints', label: 'Use cases', icon: 'layers', group: 'insight' },
  ];
  readonly section = signal(this.initialSection());
  readonly mobileNav = signal(false);
  readonly search = signal('');
  readonly orderFilter = signal('ALL');
  readonly inventoryTab = signal('balances');
  readonly onlyLowStock = signal(false);
  readonly page = signal(0);
  readonly days = signal(30);
  readonly modalKind = signal<'login' | 'action' | 'order' | 'search' | null>(null);
  readonly selectedOrder = signal<Order | null>(null);
  readonly selectedAction = signal<ActionDefinition | null>(null);
  readonly busy = signal(false);
  readonly formError = signal('');
  readonly result = signal<RequestResult | null>(null);
  readonly resultOrder = signal<Order | null>(null);
  readonly toast = signal('');
  readonly login = { username: '', password: '' };
  readonly sections = createSections();
  readonly actions = this.sections.flatMap(s => s.actions);
  readonly apiFilter = signal('');
  readonly expandedCase = signal('order');
  private toastTimer?: ReturnType<typeof setTimeout>;
  readonly today = new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'long', year: 'numeric' }).format(new Date());
  readonly sectionLabel = computed(() => this.navigation.find(n => n.id === this.section())?.label ?? 'Workspace settings');
  readonly userName = computed(() => {
    const token = this.api.accessToken();
    if (!token) return 'Demo workspace';
    try { return JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))['sub'] as string; }
    catch { return 'My workspace'; }
  });
  readonly periodOrders = computed(() => this.data.orders().filter(o => new Date(o.updatedAt).getTime() >= Date.now() - this.days() * 86400000));
  readonly periodRevenue = computed(() => this.periodOrders().filter(o => o.status === 'COMPLETED').reduce((a, o) => a + o.total, 0));
  readonly reservedUnits = computed(() => this.data.balances().reduce((a, b) => a + b.reserved, 0));
  readonly availableUnits = computed(() => this.data.balances().reduce((a, b) => a + b.available, 0));
  readonly completion = computed(() => this.periodOrders().length ? Math.round(this.periodOrders().filter(o => o.status === 'COMPLETED').length / this.periodOrders().length * 100) : 0);
  readonly filteredOrders = computed(() => this.data.orders().filter(o =>
    (this.orderFilter() === 'ALL' || o.status === this.orderFilter()) && this.matches(`${o.id} ${o.customerName} ${this.statusLabel(o.status)}`)));
  readonly pagedOrders = computed(() => this.filteredOrders().slice(this.page() * 8, this.page() * 8 + 8));
  readonly orderPages = computed(() => Math.max(1, Math.ceil(this.filteredOrders().length / 8)));
  readonly filteredProducts = computed(() => this.data.products().filter(p => this.matches(`${p.name} ${p.sku}`)));
  readonly filteredCustomers = computed(() => this.data.customers().filter(c => this.matches(`${c.name} ${c.email} ${c.taxId}`)));
  readonly filteredBalances = computed(() => this.data.balances().filter(b => this.matches(`${b.productName} ${b.sku}`) && (!this.onlyLowStock() || b.available <= 10)));
  readonly filteredActions = computed(() => this.actions.filter(a => `${a.title} ${a.path}`.toLowerCase().includes(this.apiFilter().toLowerCase())));
  readonly chart = computed(() => {
    const end = Date.now(), duration = this.days() * 86400000, start = end - duration;
    const values = Array.from({ length: 7 }, () => 0);
    this.data.completed().forEach(o => {
      const at = new Date(o.updatedAt).getTime();
      if (at >= start && at <= end) values[Math.min(6, Math.floor((at - start) / duration * 7))] += o.total;
    });
    const maximum = Math.max(100, Math.ceil(Math.max(...values) / 100) * 100);
    const points = values.map((value, i) => ({ x: 16 + i * 106, y: 168 - value / maximum * 140, value,
      label: new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short' }).format(new Date(start + i / 6 * duration)) }));
    const path = points.map((p, i) => i === 0 ? `M ${p.x} ${p.y}` : `C ${points[i - 1].x + 53} ${points[i - 1].y}, ${p.x - 53} ${p.y}, ${p.x} ${p.y}`).join(' ');
    return { points, path, area: `${path} L 652 180 L 16 180 Z`, maximum, total: values.reduce((a, v) => a + v, 0) };
  });
  readonly cases = [
    { id: 'order', number: '01', icon: 'orders', label: 'From order to delivery', category: 'SALES', description: 'Follow each step of the workflow.', outcome: 'A completed order has one outbound stock movement.', steps: [
      { title: 'Create the order', text: 'Choose a customer to create a draft. Stock is reserved when you confirm the order.', action: 'order-create' },
      { title: 'Add products', text: 'Select a product and a quantity. The order total is calculated from its lines.', action: 'line-set' },
      { title: 'Confirm and reserve', text: 'Check available stock and reserve the units in one transaction.', action: 'order-confirm' },
      { title: 'Complete delivery', text: 'Request a shipping quote and record a single stock movement.', action: 'order-complete' },
    ] },
    { id: 'stock', number: '02', icon: 'inventory', label: 'Stock control', category: 'INVENTORY', description: 'Stock on hand, reserved and available.', outcome: 'Every adjustment appears in the stock movement history.', steps: [
      { title: 'Check the balance', text: 'Available stock equals on-hand stock minus reserved units.', action: 'balances' },
      { title: 'Record an adjustment', text: 'Add or remove units and record the reason.', action: 'adjust' },
      { title: 'View history', text: 'Review adjustments, reservations, releases and shipments.', action: 'movements' },
    ] },
    { id: 'recovery', number: '03', icon: 'shield', label: 'When a shipment fails', category: 'SHIPPING RECOVERY', description: 'Recover from a shipping failure.', outcome: 'If shipping fails, the order keeps its reservation.', steps: [
      { title: 'Check status', text: 'A confirmed order keeps its reservation until it is completed or cancelled.', action: 'order-get' },
      { title: 'Retry delivery', text: 'When the shipping service is available again, retry order completion. The order status prevents a duplicate stock deduction.', action: 'order-complete' },
      { title: 'Verify the result', text: 'Check that the shipment has only one stock movement.', action: 'movements' },
    ] },
    { id: 'access', number: '04', icon: 'users', label: 'Role-based access', category: 'TEAM', description: 'Users, roles and the actions they performed.', outcome: 'Each user has the permissions assigned to their role.', steps: [
      { title: 'Create an account', text: 'Assign ADMIN, OPERATIONS or READONLY according to the responsibilities of the user.', action: 'user-create' },
      { title: 'Review permissions', text: 'Review users and update their roles as needed.', action: 'users' },
      { title: 'Track activity', text: 'The audit log records who acted and which resource they changed.', action: 'audit' },
    ] },
  ];

  constructor() { if (['reports', 'activity', 'settings'].includes(this.section())) void this.data.loadExtra(this.section()); }
  @HostListener('window:hashchange') onHashChange(): void { if (location.hash !== '#main') this.navigate(this.initialSection(), false); }
  @HostListener('document:keydown', ['$event']) onKey(event: KeyboardEvent): void {
    if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') { event.preventDefault(); this.openModal('search'); }
  }
  navigate(section: string, updateHash = true): void {
    this.section.set(section); this.search.set(''); this.page.set(0); this.mobileNav.set(false);
    if (updateHash) history.replaceState(null, '', `#/${section}`);
    void this.data.loadExtra(section); window.scrollTo({ top: 0, behavior: 'smooth' });
  }
  setSearch(value: string): void { this.search.set(value); this.page.set(0); }
  setOrderFilter(value: string): void { this.orderFilter.set(value); this.page.set(0); }
  private matches(value: string): boolean { return value.toLocaleLowerCase('es').includes(this.search().toLocaleLowerCase('es')); }
  money(value: number): string { return new Intl.NumberFormat('en-GB', { style: 'currency', currency: 'EUR', maximumFractionDigits: 2 }).format(value); }
  number(value: number): string { return new Intl.NumberFormat('en-GB').format(value); }
  initials(name: string): string { return name.split(' ').filter(Boolean).map(v => v[0]).slice(0, 2).join('').toUpperCase(); }
  statusLabel(status: string): string { return ({ DRAFT: 'Draft', CONFIRMED: 'Confirmed', COMPLETED: 'Completed', CANCELLED: 'Cancelled' } as Record<string, string>)[status] ?? status; }
  movementLabel(kind: string): string { return ({ ADJUSTMENT: 'Stock adjustment', RESERVE: 'Stock reservation', RELEASE: 'Reservation released', SHIP: 'Order shipped' } as Record<string, string>)[kind] ?? kind; }
  statusCount(status: string): number { return this.data.orders().filter(o => o.status === status).length; }
  stockFor(id: number): number { return this.data.balances().find(b => b.productId === id)?.available ?? 0; }
  productName(id: number): string { return this.data.products().find(p => p.id === id)?.name ?? `Product ${id}`; }
  customerOrders(id: number): number { return this.data.orders().filter(o => o.customerId === id).length; }
  customerSales(id: number): number { return this.data.completed().filter(o => o.customerId === id).reduce((a, o) => a + o.total, 0); }
  barWidth(value: number, total: number): number { return total > 0 ? Math.min(100, value / total * 100) : 0; }
  async refresh(): Promise<void> { if (this.data.demo()) { this.data.loadDemo(); this.notify('Demo reset'); } else { await this.data.load(); await this.data.loadExtra(this.section()); } }
  openModal(kind: 'login' | 'action' | 'order' | 'search'): void {
    this.formError.set(''); this.result.set(null); this.resultOrder.set(null); this.modalKind.set(kind);
    if (!this.modal?.nativeElement.open) this.modal?.nativeElement.showModal();
  }
  closeModal(): void { if (this.busy()) return; this.modal?.nativeElement.close(); this.modalKind.set(null); }
  dialogClosed(): void { this.modalKind.set(null); this.login.password = ''; }
  showOrder(order: Order): void { this.selectedOrder.set(order); this.openModal('order'); }
  openAction(id: string, defaults: Record<string, string | number> = {}): void {
    const source = this.actions.find(a => a.id === id); if (!source) return;
    if (this.data.demo() && id !== 'login') { this.openModal('login'); return; }
    const values = { ...source.values, ...Object.fromEntries(Object.entries(defaults).map(([k, v]) => [k, String(v)])) };
    if ('requestId' in values) values['requestId'] = crypto.randomUUID();
    if (source.jsonBody && values['payload']) values['payload'] = values['payload'].replaceAll('GENERAR_UUID', crypto.randomUUID());
    if (!source.query) {
      if ('customerId' in values && defaults['customerId'] === undefined) values['customerId'] = '';
      if ('productId' in values && defaults['productId'] === undefined) values['productId'] = '';
    }
    if (id === 'refresh') values['refreshToken'] = sessionStorage.getItem('refreshToken') ?? '';
    this.selectedAction.set({ ...source, values }); this.openModal('action');
  }
  editProduct(p: Product): void { this.openAction('product-update', { id: p.id, sku: p.sku, name: p.name, unitPrice: p.unitPrice, version: p.version }); }
  editCustomer(c: Customer): void { this.openAction('customer-update', { id: c.id, name: c.name, taxId: c.taxId, email: c.email, address: c.address, version: c.version }); }
  fieldType(key: string, type?: string): string { return type ?? (key === 'refreshToken' ? 'password' : ['id', 'productId', 'customerId', 'supplierId', 'orderId', 'receiptId', 'entryId', 'quantity', 'unitPrice', 'unitCost', 'amount', 'leadTimeDays', 'version', 'page', 'size', 'threshold'].includes(key) ? 'number' : ['dueDate', 'expectedOn', 'dueBefore', 'asOf'].includes(key) ? 'date' : key === 'email' ? 'email' : 'text'); }
  actionTitle(a: ActionDefinition): string { return ({ 'order-confirm': 'Confirm order', 'order-cancel': 'Cancel order', 'order-complete': 'Complete order', 'balances': 'View stock' } as Record<string, string>)[a.id] ?? a.title; }
  async submitLogin(): Promise<void> {
    if (this.busy()) return; this.busy.set(true); this.formError.set('');
    try { await this.api.login(this.login.username, this.login.password); this.login.password = ''; this.busy.set(false); this.closeModal(); await this.data.load(); await this.data.loadExtra(this.section()); }
    catch (error) { this.formError.set(this.api.errorMessage(error)); } finally { this.busy.set(false); }
  }
  async execute(): Promise<void> {
    const action = this.selectedAction(); if (!action || this.busy()) return;
    this.busy.set(true); this.formError.set(''); this.result.set(null);
    try {
      const result = await this.api.execute(action); this.result.set(result);
      const order = result.data as Partial<Order> | null;
      if (order && typeof order === 'object' && Array.isArray(order.lines) && typeof order.id === 'number' && typeof order.customerName === 'string') this.resultOrder.set(order as Order);
      if (!action.method.startsWith('GET')) {
        if (this.api.accessToken()) { await this.data.load(); await this.data.loadExtra(this.section()); }
        else this.data.loadDemo();
        this.notify('Operation completed');
      }
    } catch (error) {
      this.formError.set(this.api.errorMessage(error));
      if (error instanceof HttpErrorResponse) this.result.set({ ok: false, status: error.status, statusText: error.statusText, durationMs: 0, data: error.error });
    } finally { this.busy.set(false); }
  }
  viewWarehouseResult(): void {
    this.closeModal();
    this.navigate('warehouse');
    this.warehouseReplay.update(value => value + 1);
  }
  async logout(): Promise<void> {
    try { await this.api.logout(); this.data.loadDemo(); this.notify('Signed out'); }
    catch { this.data.loadDemo(); this.notify('Signed out locally. The server could not confirm that the session was revoked.'); }
  }
  notify(message: string): void { clearTimeout(this.toastTimer); this.toast.set(message); this.toastTimer = setTimeout(() => this.toast.set(''), 4500); }
  exportOrders(): void { this.downloadCsv('orders', ['Order', 'Customer', 'Status', 'Total EUR', 'Date'], this.filteredOrders().map(o => [o.id, o.customerName, this.statusLabel(o.status), o.total, o.createdAt])); }
  exportSales(): void { this.downloadCsv('sales', ['Customer', 'Completed orders', 'Sales EUR', 'Shipping EUR'], this.data.sales().map(s => [s.customerName, s.completedOrders, s.netSales, s.shippingCost])); }
  private downloadCsv(name: string, headers: string[], rows: (string | number)[][]): void {
    const cell = (value: string | number) => `"${String(value).replace(/^[=+@-]/, "'$&").replace(/"/g, '""')}"`;
    const csv = '\uFEFF' + [headers, ...rows].map(row => row.map(cell).join(';')).join('\r\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8;' }));
    const link = document.createElement('a'); link.href = url; link.download = `nexo-${name}${this.data.demo() ? '-demo' : ''}.csv`; link.click(); URL.revokeObjectURL(url);
    this.notify('File exported');
  }
  private initialSection(): string {
    const section = location.hash.replace('#/', '');
    return ['overview', 'warehouse', 'orders', 'inventory', 'products', 'customers', 'reports', 'activity', 'blueprints', 'lab', 'settings'].includes(section) ? section : 'warehouse';
  }
}
