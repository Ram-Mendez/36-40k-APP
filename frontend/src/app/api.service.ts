import { HttpClient, HttpContext, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom, Subject } from 'rxjs';
import type { ActionDefinition } from './actions';
import { Problem, Tokens } from './models';
import { WAREHOUSE_ACTION } from './warehouse-activity.interceptor';
import { WarehouseActivityService } from './warehouse-activity.service';

export interface RequestResult { ok: boolean; status: number; statusText: string; durationMs: number; data: unknown; }

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly warehouse = inject(WarehouseActivityService);
  private readonly authSubject = new Subject<Tokens | null>();
  readonly authState = this.authSubject.asObservable();
  readonly accessToken = signal(sessionStorage.getItem('accessToken') ?? '');

  async login(username: string, password: string): Promise<Tokens> {
    const tokens = await firstValueFrom(this.http.post<Tokens>('/api/v1/auth/login', { username, password }));
    this.warehouse.clear();
    this.saveTokens(tokens);
    return tokens;
  }

  async logout(): Promise<void> {
    try { if (this.accessToken()) await firstValueFrom(this.http.post('/api/v1/auth/logout', {})); }
    finally { this.clearSession(); }
  }

  clearSession(): void {
    this.warehouse.clear();
    sessionStorage.removeItem('accessToken');
    sessionStorage.removeItem('refreshToken');
    this.accessToken.set('');
    this.authSubject.next(null);
  }

  private saveTokens(tokens: Tokens): void {
    sessionStorage.setItem('accessToken', tokens.accessToken);
    sessionStorage.setItem('refreshToken', tokens.refreshToken);
    this.accessToken.set(tokens.accessToken);
    this.authSubject.next(tokens);
  }

  async execute(action: ActionDefinition): Promise<RequestResult> {
    const started = performance.now();
    const missingParameter = [...action.path.matchAll(/\{(\w+)\}/g)].map(match => match[1]).find(key => !action.values[key]?.trim());
    if (missingParameter) throw new Error(`Completa el campo ${missingParameter} antes de enviar la petición.`);
    let path = action.path.replace(/\{(\w+)\}/g, (_, key: string) => encodeURIComponent(action.values[key] ?? ''));
    let params = new HttpParams();
    for (const key of action.query ?? []) if (action.values[key]) params = params.set(key, action.values[key]);
    const options = { params, observe: 'response' as const, context: new HttpContext().set(WAREHOUSE_ACTION, true) };
    let response;
    if (action.method.startsWith('GET')) response = await firstValueFrom(this.http.get(path, options));
    else if (action.method.startsWith('DELETE')) response = await firstValueFrom(this.http.delete(path, options));
    else if (action.method.startsWith('PUT')) response = await firstValueFrom(this.http.put(path, this.body(action), options));
    else response = await firstValueFrom(this.http.post(path, this.body(action), options));
    if (action.id === 'login' || action.id === 'refresh') {
      if (action.id === 'login') this.warehouse.clear();
      this.saveTokens(response.body as Tokens);
    }
    if (action.id === 'logout' || action.id === 'password' || action.id === 'profile-update') this.clearSession();
    return { ok: true, status: response.status, statusText: response.statusText, durationMs: Math.round(performance.now() - started), data: action.id === 'login' || action.id === 'refresh' ? 'Session updated' : response.body ?? 'No content' };
  }

  errorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 0 || error.status === 502 || error.status === 504) return 'Could not connect to the application. Check that the backend is running.';
      if (error.status === 401) return 'Your session has expired or your credentials are invalid. Sign in again.';
      if (error.status === 403) return 'Your account does not have permission for this operation.';
      const problem = error.error as Problem;
      return problem?.detail ?? `${error.status} ${error.statusText}`;
    }
    return error instanceof Error ? error.message : 'Unexpected error';
  }

  private body(action: ActionDefinition): Record<string, unknown> {
    if (action.jsonBody) {
      let value: unknown;
      try { value = JSON.parse(action.values['payload']); }
      catch { throw new Error('El cuerpo JSON no es válido. Revisa comas, comillas y llaves.'); }
      if (!value || typeof value !== 'object' || Array.isArray(value)) throw new Error('El cuerpo debe ser un objeto JSON.');
      return value as Record<string, unknown>;
    }
    const numeric = new Set(['id', 'customerId', 'productId', 'supplierId', 'orderId', 'quantity', 'unitPrice', 'unitCost', 'amount', 'leadTimeDays', 'version']);
    return Object.fromEntries((action.body ?? []).map((key) => [key,
      numeric.has(key) ? Number(action.values[key]) : key === 'active' ? action.values[key] === 'true' : action.values[key],
    ]));
  }

}
