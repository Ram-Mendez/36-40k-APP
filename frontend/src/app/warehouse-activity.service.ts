import { computed, Injectable, signal } from '@angular/core';
import { WarehouseActivity, WarehouseActivityLog } from './warehouse-activity';

@Injectable({ providedIn: 'root' })
export class WarehouseActivityService {
  private readonly log = new WarehouseActivityLog();
  private readonly current = signal<WarehouseActivity[]>([]);
  readonly activities = this.current.asReadonly();
  readonly pendingCount = computed(() => this.activities().filter(activity => activity.state === 'pending').length);

  begin(method: string, path: string, body: unknown, demo = false): string {
    const id = this.log.begin(method, path, body, demo);
    this.sync();
    return id;
  }
  succeed(id: string, response: unknown, status: number): void { this.log.succeed(id, response, status); this.sync(); }
  fail(id: string, error: unknown): void { this.log.fail(id, error); this.sync(); }
  clearHistory(): void { this.log.clearHistory(); this.sync(); }
  clear(): void { this.log.clear(); this.sync(); }

  /** Local examples do not send HTTP and never change WorkspaceService balances. */
  demo(operation: string): void {
    const samples: Record<string, { path: string; body: unknown; response: unknown }> = {
      confirm: { path: '/api/v1/orders/101/confirm', body: {}, response: { id: 101, status: 'CONFIRMED', lines: [{ productId: 1, quantity: 3 }] } },
      complete: { path: '/api/v1/orders/101/complete', body: {}, response: { id: 101, status: 'COMPLETED', lines: [{ productId: 1, quantity: 3 }] } },
      receive: { path: '/api/v1/purchase-orders/201/receipts', body: { lines: [{ productId: 1, quantity: 5 }] }, response: { id: Date.now(), lines: [{ productId: 1, quantity: 5 }] } },
      return: { path: '/api/v1/returns/301/receive', body: { lines: [{ productId: 1, restockQuantity: 2, damagedQuantity: 1 }] }, response: { id: 301, status: 'RECEIVED', lines: [{ productId: 1, restockQuantity: 2, damagedQuantity: 1 }] } },
      payment: { path: '/api/v1/billing/invoices/401/payments', body: { amount: 50 }, response: { id: Date.now(), amount: 50, kind: 'PAYMENT' } },
      error: { path: '/api/v1/orders/101/confirm', body: {}, response: null },
    };
    const sample = samples[operation];
    if (!sample) return;
    const id = this.begin('POST', sample.path, sample.body, true);
    setTimeout(() => operation === 'error'
      ? this.fail(id, { status: 409, error: { code: 'INSUFFICIENT_STOCK' } })
      : this.succeed(id, sample.response, 200), 650);
  }

  private sync(): void { this.current.set(this.log.activities); }
}
