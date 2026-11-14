import { JsonPipe } from '@angular/common';
import { HttpClient, HttpContext, HttpErrorResponse } from '@angular/common/http';
import { Component, EventEmitter, inject, Input, Output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { firstValueFrom } from 'rxjs';
import { WAREHOUSE_ACTION } from './warehouse-activity.interceptor';
import { WorkspaceService } from './workspace.service';
import { warehouseExercises, WarehouseStep } from './warehouse-exercises';

interface ServerResponse {
  title: string;
  path: string;
  status: number;
  sent: unknown;
  content: unknown;
}

const labels: Record<string, string> = {
  productId: 'Product P', secondProductId: 'Product Q', supplierId: 'Supplier A', secondSupplierId: 'Supplier B',
  orderId: 'Customer order', purchaseId: 'Purchase order', draftId: 'Draft purchase order',
  customerId: 'Customer', invoiceId: 'Invoice', receiptId: 'Delivery note', returnId: 'First return',
  newReturnId: 'New return', returnVersion: 'First return version',
  policyVersion: 'Policy version for P', secondPolicyVersion: 'Policy version for Q', uuid: 'Request ID', price: 'Unit selling price (€)',
  productSku: 'Product reference (SKU)', productName: 'Product name',
  supplierName: 'Supplier name', customerName: 'Customer name', taxId: 'Tax ID',
  email: 'Email', address: 'Address', page: 'Page (starting at 0)', size: 'Rows per page',
  date: 'Invoice due date', minimum: 'Minimum stock', target: 'Target stock',
  threshold: 'Low-stock threshold', reason: 'Return reason', paymentReference: 'Payment reference',
  deliveryReference: 'Supplier delivery note number', returnSort: 'Sort order (status,asc or status,desc)',
  firstCost: 'Unit cost for P (€)', secondCost: 'Unit cost for Q (€)'
};
const isNumeric = (key: string) => (key !== 'taxId' && /(?:Id|Version)$/.test(key)) ||
  ['price', 'page', 'size', 'minimum', 'target', 'threshold', 'firstCost', 'secondCost'].includes(key);

@Component({
  selector: 'warehouse-lab', imports: [FormsModule, JsonPipe],
  templateUrl: './warehouse-lab.html', styleUrl: './warehouse-lab.scss'
})
export class WarehouseLab {
  private readonly workspace = inject(WorkspaceService);
  @Input() signedIn = false;
  @Output() connect = new EventEmitter<void>();
  private readonly http = inject(HttpClient);
  readonly exercises = warehouseExercises;
  readonly exerciseIndex = signal(0);
  readonly stepIndex = signal(0);
  readonly busy = signal(false);
  readonly draftAvailability = signal<'checking' | 'available' | 'unavailable' | 'unknown'>('checking');
  readonly notice = signal('');
  readonly labels = labels;
  readonly isNumeric = isNumeric;
  private readonly exerciseValues = new Map<number, Record<string, string>>();
  private readonly exerciseHistory = new Map<number, ServerResponse[]>();
  private readonly stepValues = new Map<string, Record<string, string>>();
  fields: Record<string, string> = {};
  keys: string[] = [];

  constructor() { this.selectStep(0); }

  get exercise() { return this.exercises[this.exerciseIndex()]; }
  get step() { return this.exercise.steps[this.stepIndex()]; }
  get stepPath() { return this.step.path.startsWith('/api/') ? this.step.path : '/api/v1' + this.step.path; }
  get requestBody() { return this.buildBody(this.step.body); }
  get isPurchaseDraft() { return this.step.path === '/api/v1/replenishment/purchase-drafts'; }
  get history() { return this.exerciseHistory.get(this.exerciseIndex()) ?? []; }

  selectExercise(index: number): void {
    this.exerciseIndex.set(index);
    this.selectStep(0);
  }

  // Reuse IDs returned by earlier steps in this exercise.
  selectStep(index: number): void {
    this.stepIndex.set(index);
    this.notice.set('');
    const template = this.step.path + JSON.stringify(this.step.body ?? {});
    this.keys = [...new Set([...template.matchAll(/\{(\w+)\}/g)].map(match => match[1]))];
    const stepKey = this.exerciseIndex() + '/' + index;
    const values = this.stepValues.get(stepKey) ?? this.defaultValues();
    const saved = this.exerciseValues.get(this.exerciseIndex()) ?? {};
    for (const key of this.keys) {
      if (key in saved) values[key] = saved[key];
    }
    this.fields = values;
    this.stepValues.set(stepKey, values);
    if (this.isPurchaseDraft) void this.checkDraftAvailability();
  }

  private async checkDraftAvailability(): Promise<void> {
    this.draftAvailability.set('checking');
    try {
      const document = await firstValueFrom(this.http.get<{ paths: Record<string, { post?: object }> }>('/v3/api-docs'));
      this.draftAvailability.set(document.paths['/api/v1/replenishment/purchase-drafts']?.post ? 'available' : 'unavailable');
    } catch {
      this.draftAvailability.set('unknown');
    }
  }

  private defaultValues(): Record<string, string> {
    const suffix = crypto.randomUUID().slice(0, 8);
    const date = new Date();
    date.setDate(date.getDate() + 30);
    return {
      productSku: 'LAB-' + suffix, productName: 'Product ' + suffix,
      supplierName: 'Supplier ' + suffix, customerName: 'Customer ' + suffix,
      taxId: 'LAB-' + suffix, email: suffix + '@example.test', address: '1 High Street',
      price: this.exerciseIndex() === 4 ? '92.50' : '8.00', date: date.toISOString().slice(0, 10),
      uuid: crypto.randomUUID(), deliveryReference: 'DEL-' + suffix, paymentReference: 'PAY-' + suffix,
      reason: 'The customer wants to return the items', minimum: this.keys.includes('secondPolicyVersion') ? '6' : '5', target: '15', page: '0', size: '20',
      threshold: '10', returnSort: 'status,asc', firstCost: '8.25', secondCost: '5.50'
    };
  }

  newRequestId(): void { this.fields['uuid'] = crypto.randomUUID(); }

  private buildBody(template: unknown): unknown {
    if (Array.isArray(template)) return template.map(value => this.buildBody(value));
    if (template && typeof template === 'object') return Object.fromEntries(
      Object.entries(template).map(([key, value]) => [key, this.buildBody(value)]));
    if (typeof template === 'string') {
      const key = /^\{(\w+)\}$/.exec(template)?.[1];
      if (key) return isNumeric(key) ? Number(this.fields[key]) : this.fields[key];
    }
    return template;
  }

  async run(): Promise<void> {
    if (!this.signedIn || this.busy()) return;
    if (this.isPurchaseDraft && ['unavailable', 'checking'].includes(this.draftAvailability())) return;
    const missing = this.keys.find(key => !this.fields[key]?.trim() ||
      (isNumeric(key) && !Number.isFinite(Number(this.fields[key]))));
    if (missing) { this.notice.set('Check the field “' + this.labels[missing] + '”.'); return; }
    this.busy.set(true);
    this.notice.set('');
    const step = this.step;
    const path = this.stepPath.replace(/\{(\w+)\}/g, (_, key: string) => encodeURIComponent(this.fields[key]));
    const body = this.buildBody(step.body);
    try {
      let responses: ServerResponse[];
      if (step.concurrentPayments) {
        const second = { ...(body as object), requestId: crypto.randomUUID(), reference: this.fields['paymentReference'] + '-B' };
        responses = await Promise.all([this.send(step, path, body, ' · A'), this.send(step, path, second, ' · B')]);
      } else {
        responses = [await this.send(step, path, body, '')];
      }
      this.exerciseHistory.set(this.exerciseIndex(), [...responses, ...this.history]);
      if (step.method !== 'GET' && responses.some(response => response.status >= 200 && response.status < 300)) {
        await this.workspace.load();
      }
    } finally { this.busy.set(false); }
  }

  private async send(step: WarehouseStep, path: string, body: unknown, suffix: string): Promise<ServerResponse> {
    const title = step.title + suffix;
    try {
      const response = await firstValueFrom(this.http.request<Record<string, unknown>>(step.method, path, {
        body, observe: 'response', context: new HttpContext().set(WAREHOUSE_ACTION, true)
      }));
      const data = this.exerciseValues.get(this.exerciseIndex()) ?? {};
      for (const [field, target] of Object.entries(step.capture ?? {})) {
        const value = response.body?.[field];
        if (value != null) data[target] = String(value);
      }
      this.exerciseValues.set(this.exerciseIndex(), data);
      return { title, path, status: response.status, sent: body, content: response.body };
    } catch (error) {
      if (error instanceof HttpErrorResponse) return { title, path, status: error.status, sent: body, content: error.error };
      throw error;
    }
  }
}
