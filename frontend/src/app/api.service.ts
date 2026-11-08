import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { firstValueFrom, Subject } from 'rxjs';
import type { ActionDefinition } from './actions';
import { Problem, Tokens } from './models';

export interface RequestResult { ok: boolean; status: number; statusText: string; durationMs: number; data: unknown; }

@Injectable({ providedIn: 'root' })
export class ApiService {
  private readonly http = inject(HttpClient);
  private readonly authSubject = new Subject<Tokens | null>();
  readonly authState = this.authSubject.asObservable();
  readonly accessToken = signal(sessionStorage.getItem('accessToken') ?? '');

  async login(username: string, password: string): Promise<Tokens> {
    const tokens = await firstValueFrom(this.http.post<Tokens>('/api/v1/auth/login', { username, password }));
    this.saveTokens(tokens);
    return tokens;
  }

  async logout(): Promise<void> {
    try { if (this.accessToken()) await firstValueFrom(this.http.post('/api/v1/auth/logout', {})); }
    finally { this.clearSession(); }
  }

  clearSession(): void {
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
    let path = action.path.replace(/\{(\w+)\}/g, (_, key: string) => encodeURIComponent(action.values[key] ?? ''));
    let params = new HttpParams();
    for (const key of action.query ?? []) if (action.values[key]) params = params.set(key, action.values[key]);
    const options = { params, observe: 'response' as const };
    let response;
    if (action.method.startsWith('GET')) response = await firstValueFrom(this.http.get(path, options));
    else if (action.method.startsWith('DELETE')) response = await firstValueFrom(this.http.delete(path, options));
    else if (action.method.startsWith('PUT')) response = await firstValueFrom(this.http.put(path, this.body(action), options));
    else response = await firstValueFrom(this.http.post(path, this.body(action), options));
    if (action.id === 'login' || action.id === 'refresh') {
      this.saveTokens(response.body as Tokens);
    }
    if (action.id === 'logout' || action.id === 'password') this.clearSession();
    return { ok: true, status: response.status, statusText: response.statusText, durationMs: Math.round(performance.now() - started), data: action.id === 'login' || action.id === 'refresh' ? 'Sesión actualizada correctamente' : response.body ?? 'Sin contenido' };
  }

  errorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 0 || error.status === 502 || error.status === 504) return 'No se puede conectar con la aplicación. Comprueba que el backend está disponible en el puerto 8086.';
      if (error.status === 401) return 'La sesión ha caducado o las credenciales no son válidas. Inicia sesión de nuevo.';
      if (error.status === 403) return 'Tu usuario no tiene permiso para esta operación.';
      const problem = error.error as Problem;
      return problem?.detail ?? `${error.status} ${error.statusText}`;
    }
    return error instanceof Error ? error.message : 'Error inesperado';
  }

  private body(action: ActionDefinition): Record<string, unknown> {
    const numeric = new Set(['id', 'customerId', 'productId', 'quantity', 'unitPrice', 'version']);
    return Object.fromEntries((action.body ?? []).map((key) => [key,
      numeric.has(key) ? Number(action.values[key]) : key === 'active' ? action.values[key] === 'true' : action.values[key],
    ]));
  }

}
