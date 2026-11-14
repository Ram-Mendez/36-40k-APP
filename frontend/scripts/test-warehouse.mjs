import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { createRequire } from 'node:module';
import { test } from 'node:test';
import ts from 'typescript';

// Exercise the same pure model used by Angular without a second browser test stack.
const modelUrl = new URL('../src/app/warehouse-activity.ts', import.meta.url);
const program = ts.createProgram([fileURLToPath(modelUrl)], {
  strict: true, noEmit: true, skipLibCheck: true, types: [],
  target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022,
  noPropertyAccessFromIndexSignature: true, noImplicitReturns: true,
});
const diagnostics = ts.getPreEmitDiagnostics(program);
if (diagnostics.length) {
  console.error(ts.formatDiagnosticsWithColorAndContext(diagnostics, {
    getCanonicalFileName: name => name, getCurrentDirectory: () => process.cwd(), getNewLine: () => '\n',
  }));
  process.exit(1);
}
const compiled = ts.transpileModule(await readFile(modelUrl, 'utf8'), {
  compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022 },
}).outputText;
const { WarehouseActivityLog, warehousePath } = await import(`data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`);
const modelModule = `data:text/javascript;base64,${Buffer.from(compiled).toString('base64')}`;

function run(method, path, body, response, status = 200) {
  const log = new WarehouseActivityLog();
  const id = log.begin(method, path, body);
  const pending = log.activities[0];
  log.succeed(id, response, status);
  return { log, pending, event: log.activities[0], id };
}

test('una consulta de recepciones nunca representa entrada de cajas', () => {
  const { event } = run('GET', '/api/v1/purchase-orders/8/receipts', null, { content: [{ id: 2, lines: [{ quantity: 40 }] }] });
  assert.equal(event.kind, 'read');
  assert.equal(event.quantity, null);
  assert.equal(event.cargo, 'document');
});

test('confirmar reserva las cantidades devueltas; no lleva cajas fuera', () => {
  const { event, pending } = run('POST', '/api/v1/orders/4/confirm', {}, { id: 4, lines: [{ quantity: 2 }, { quantity: 7 }] });
  assert.equal(pending.state, 'pending');
  assert.equal(pending.cargo, 'none');
  assert.equal(event.quantity, 9);
  assert.equal(event.kind, 'reserve');
  assert.equal(event.cargo, 'none');
  assert.equal(event.to, 'stock');
});

test('completar lleva las unidades confirmadas al muelle', () => {
  const { event } = run('POST', '/api/v1/orders/4/complete', {}, { id: 4, lines: [{ quantity: 2 }, { quantity: 3 }] });
  assert.equal(event.quantity, 5);
  assert.equal(event.kind, 'outbound');
  assert.equal(event.cargo, 'boxes');
  assert.equal(event.from, 'stock');
  assert.equal(event.to, 'dispatch');
});

test('cancelar no inventa una cantidad de reservas que el DTO no revela', () => {
  const { event } = run('POST', '/api/v1/orders/4/cancel', {}, { id: 4, status: 'CANCELLED', lines: [{ quantity: 10 }] });
  assert.equal(event.quantity, null);
  assert.equal(event.kind, 'release');
  assert.equal(event.cargo, 'none');
});

test('enviar compra no incrementa existencias', () => {
  const { event } = run('POST', '/api/v1/purchase-orders/2/submit', {}, { lines: [{ quantity: 100 }] });
  assert.equal(event.kind, 'document');
  assert.equal(event.cargo, 'document');
  assert.equal(event.quantity, null);
});

test('una recepción parcial muestra la recepción concreta, no el pedido completo', () => {
  const { event } = run('POST', '/api/v1/purchase-orders/2/receipts', { lines: [{ quantity: 3 }] }, { id: 9, lines: [{ quantity: 3 }] });
  assert.equal(event.quantity, 3);
  assert.equal(event.kind, 'inbound');
});

test('la devolución repone solo unidades aptas y distingue las dañadas', () => {
  const { event } = run('POST', '/api/v1/returns/2/receive', { lines: [{ restockQuantity: 2, damagedQuantity: 4 }] }, { lines: [{ quantity: 6, restockQuantity: 2, damagedQuantity: 4 }] });
  assert.equal(event.quantity, 2);
  assert.match(event.effect, /4 unidades dañadas/);
  const damagedOnly = run('POST', '/api/v1/returns/3/receive', {}, { lines: [{ quantity: 4, restockQuantity: 0, damagedQuantity: 4 }] }).event;
  assert.equal(damagedOnly.to, 'returns');
  assert.equal(damagedOnly.cargo, 'none');
});

test('los ajustes negativos conservan el signo y no simulan una recepción', () => {
  const { event } = run('POST', '/api/v1/inventory/7/adjustments', { quantity: -2 }, { productId: 7, onHand: 8, reserved: 1 });
  assert.equal(event.quantity, -2);
  assert.equal(event.from, 'stock');
  assert.equal(event.to, 'control');
  assert.equal(event.productId, 7);
});

test('un cobro es contable, sin cantidades de mercancía', () => {
  const { event } = run('POST', '/api/v1/billing/invoices/7/payments', { amount: 50 }, { id: 12, amount: 50 });
  assert.equal(event.amount, 50);
  assert.equal(event.quantity, null);
  assert.equal(event.kind, 'money');
  assert.notEqual(event.cargo, 'boxes');
});

test('peticiones simultáneas se resuelven por identidad aunque lleguen en orden inverso', () => {
  const log = new WarehouseActivityLog();
  const first = log.begin('POST', '/api/v1/orders/1/complete', {});
  const second = log.begin('POST', '/api/v1/orders/2/confirm', {});
  log.succeed(second, { id: 2, lines: [{ quantity: 8 }] }, 200);
  log.fail(first, { status: 409, error: { code: 'INSUFFICIENT_STOCK' } });
  assert.equal(log.activities.find(item => item.id === second).quantity, 8);
  assert.equal(log.activities.find(item => item.id === first).state, 'error');
  assert.equal(log.activities.find(item => item.id === first).cargo, 'none');
});

test('dos respuestas de la misma recepción no duplican el movimiento visual', () => {
  const log = new WarehouseActivityLog();
  const a = log.begin('POST', '/api/v1/purchase-orders/1/receipts', { lines: [{ quantity: 3 }] });
  const b = log.begin('POST', '/api/v1/purchase-orders/1/receipts', { lines: [{ quantity: 3 }] });
  log.succeed(b, { id: 22, lines: [{ quantity: 3 }] }, 200);
  log.succeed(a, { id: 22, lines: [{ quantity: 3 }] }, 200);
  const replay = log.activities.find(item => item.id === a);
  assert.equal(replay.replayed, true);
  assert.equal(replay.cargo, 'none');
  assert.equal(replay.quantity, null);
  assert.equal(replay.from, replay.to);
  assert.equal(log.activities.find(item => item.id === b).replayed, false);
});

test('deduplicación distingue pedidos y ejemplos de peticiones reales', () => {
  const log = new WarehouseActivityLog();
  for (const [order, demo] of [[1, false], [2, false], [1, true]]) {
    const id = log.begin('POST', `/api/v1/purchase-orders/${order}/receipts`, {}, demo);
    log.succeed(id, { id: 3, lines: [{ quantity: 1 }] }, 200);
    assert.equal(log.activities[0].replayed, false);
  }
});

test('limpiar el historial conserva la identidad de las recepciones ya registradas', () => {
  const log = new WarehouseActivityLog();
  const receive = () => {
    const id = log.begin('POST', '/api/v1/purchase-orders/1/receipts', { lines: [{ quantity: 3 }] });
    log.succeed(id, { id: 22, lines: [{ quantity: 3 }] }, 200);
    return log.activities[0];
  };
  assert.equal(receive().replayed, false);
  log.clearHistory();
  assert.equal(log.activities.length, 0);
  const replay = receive();
  assert.equal(replay.replayed, true);
  assert.equal(replay.cargo, 'none');
  assert.equal(replay.quantity, null);
});

test('cerrar sesión borra también las identidades usadas por la sesión anterior', () => {
  const log = new WarehouseActivityLog();
  const receive = () => {
    const id = log.begin('POST', '/api/v1/purchase-orders/1/receipts', {});
    log.succeed(id, { id: 22, lines: [{ quantity: 3 }] }, 200);
    return log.activities[0];
  };
  assert.equal(receive().replayed, false);
  log.clear();
  assert.equal(receive().replayed, false);
});

test('reintentar tras error puede producir el primer resultado válido', () => {
  const log = new WarehouseActivityLog();
  const failed = log.begin('POST', '/api/v1/purchase-orders/1/receipts', {});
  log.fail(failed, { status: 409 });
  const id = log.begin('POST', '/api/v1/purchase-orders/1/receipts', {});
  log.succeed(id, { id: 30, lines: [{ quantity: 4 }] }, 200);
  assert.equal(log.activities[0].replayed, false);
  assert.equal(log.activities[0].quantity, 4);
});

test('las devoluciones y asientos repetidos usan su identidad estable', () => {
  for (const [path, response] of [
    ['/api/v1/returns/3/receive', { receiptRequestId: '123e4567-e89b-12d3-a456-426614174000', lines: [{ restockQuantity: 1, damagedQuantity: 0 }] }],
    ['/api/v1/billing/invoices/3/payments', { id: 8, amount: 20 }],
  ]) {
    const log = new WarehouseActivityLog();
    for (let i = 0; i < 2; i++) {
      const id = log.begin('POST', path, {});
      log.succeed(id, response, 200);
      assert.equal(log.activities[0].replayed, i === 1);
    }
  }
});

test('no retiene credenciales, cuerpos, datos personales ni mensajes de error arbitrarios', () => {
  const secret = 'do-not-log-this-secret';
  const log = new WarehouseActivityLog();
  const id = log.begin('POST', '/api/v1/auth/login?password=' + secret, { username: secret, password: secret });
  log.succeed(id, { accessToken: secret, refreshToken: secret }, 200);
  const failed = log.begin('POST', '/api/v1/auth/' + secret, { password: secret });
  log.fail(failed, { status: 401, error: { detail: secret, trace: secret } });
  assert.equal(JSON.stringify(log).includes(secret), false);
  assert.equal(warehousePath('/api/v1/customers/5?search=' + secret), '/api/v1/customers/5');
});

test('un error de red no afirma que la operación no ocurrió en el servidor', () => {
  const log = new WarehouseActivityLog();
  const id = log.begin('POST', '/api/v1/orders/5/complete', {});
  log.fail(id, { status: 0 });
  assert.match(log.activities[0].error, /consulta el estado/);
  assert.equal(log.activities[0].cargo, 'none');
  assert.equal(log.activities[0].quantity, null);
});

test('las familias administrativas y de catálogo nunca transportan cajas', () => {
  for (const path of ['/api/v1/products', '/api/v1/customers/2', '/api/v1/suppliers', '/api/v1/users', '/api/v1/replenishment/policies/5']) {
    const { event } = run('PUT', path, { quantity: 50 }, { id: 7 });
    assert.notEqual(event.cargo, 'boxes');
    assert.equal(event.quantity, null);
  }
});

test('el historial tiene límite y una respuesta tardía no resucita eventos borrados', () => {
  const log = new WarehouseActivityLog();
  for (let i = 0; i < 75; i++) log.begin('GET', '/api/v1/orders/' + (i + 1), null);
  assert.equal(log.activities.length, 60);
  const id = log.activities[0].id;
  log.clear();
  log.succeed(id, {}, 200);
  assert.equal(log.activities.length, 0);
});

test('una notificación duplicada no sobreescribe el resultado ya finalizado', () => {
  const { log, id } = run('POST', '/api/v1/orders/5/confirm', {}, { lines: [{ quantity: 2 }] });
  log.fail(id, { status: 500 });
  log.succeed(id, { lines: [{ quantity: 99 }] }, 200);
  assert.equal(log.activities[0].state, 'success');
  assert.equal(log.activities[0].quantity, 2);
});

// Angular's actual HTTP testing backend verifies the opt-in interceptor and ApiService.
// Compile only these local classes in memory; all framework modules are the installed ones.
await import('@angular/compiler');
const { createEnvironmentInjector, Injector, PendingTasks, ɵINJECTOR_SCOPE } = await import('@angular/core');
const { HttpClient, HttpContext, HttpErrorResponse, provideHttpClient, withInterceptors, withNoXsrfProtection } = await import('@angular/common/http');
const { HttpTestingController, provideHttpClientTesting } = await import('@angular/common/http/testing');
const require = createRequire(import.meta.url);
async function localModule(name, dependencies) {
  const source = await readFile(new URL(`../src/app/${name}.ts`, import.meta.url), 'utf8');
  const result = ts.transpileModule(source, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ES2022, experimentalDecorators: true },
  }).outputText.replace(/from (['"])([^'"]+)\1/g, (_, quote, specifier) => {
    const target = dependencies[specifier] ?? pathToFileURL(require.resolve(specifier)).href;
    return `from ${quote}${target}${quote}`;
  });
  return `data:text/javascript;base64,${Buffer.from(result).toString('base64')}`;
}
const serviceModule = await localModule('warehouse-activity.service', { './warehouse-activity': modelModule });
const interceptorModule = await localModule('warehouse-activity.interceptor', { './warehouse-activity.service': serviceModule });
const apiModule = await localModule('api.service', { './warehouse-activity.service': serviceModule, './warehouse-activity.interceptor': interceptorModule });
const { WarehouseActivityService } = await import(serviceModule);
const { WAREHOUSE_ACTION, warehouseActivityInterceptor } = await import(interceptorModule);
const { ApiService } = await import(apiModule);
const storage = new Map();
globalThis.sessionStorage = { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, String(value)), removeItem: key => storage.delete(key) };

function httpFixture() {
  storage.clear();
  const injector = createEnvironmentInjector([
    { provide: ɵINJECTOR_SCOPE, useValue: 'root' },
    // No view is rendered in Node; only Angular's render-stability bookkeeping is replaced.
    { provide: PendingTasks, useValue: { add: () => () => {} } },
    WarehouseActivityService, ApiService,
    provideHttpClient(withInterceptors([warehouseActivityInterceptor]), withNoXsrfProtection()),
    provideHttpClientTesting(),
  ], Injector.NULL);
  return { injector, http: injector.get(HttpClient), control: injector.get(HttpTestingController), activities: injector.get(WarehouseActivityService), api: injector.get(ApiService) };
}

test('HTTP Angular: refrescos de fondo no se animan y una consulta explícita sí', () => {
  const f = httpFixture();
  try {
    f.http.get('/api/v1/inventory').subscribe();
    f.control.expectOne('/api/v1/inventory').flush({ content: [] });
    assert.equal(f.activities.activities().length, 0);
    f.http.get('/api/v1/inventory', { context: new HttpContext().set(WAREHOUSE_ACTION, true) }).subscribe();
    assert.equal(f.activities.pendingCount(), 1);
    f.control.expectOne('/api/v1/inventory').flush({ content: [] });
    assert.equal(f.activities.activities()[0].kind, 'read');
    assert.equal(f.activities.activities()[0].state, 'success');
    assert.equal(f.activities.pendingCount(), 0);
    f.control.verify();
  } finally { f.injector.destroy(); }
});

test('HTTP Angular: ApiService envía JSON anidado y registra el resultado aceptado', async () => {
  const f = httpFixture();
  try {
    const operation = f.api.execute({ id: 'receive', method: 'POST', path: '/api/v1/purchase-orders/{id}/receipts', fields: [], jsonBody: true, values: { id: '8', payload: JSON.stringify({ requestId: 'uuid', lines: [{ productId: 1, quantity: 4 }] }) } });
    const request = f.control.expectOne('/api/v1/purchase-orders/8/receipts');
    assert.deepEqual(request.request.body.lines, [{ productId: 1, quantity: 4 }]);
    assert.equal(f.activities.pendingCount(), 1);
    request.flush({ id: 12, lines: [{ productId: 1, quantity: 4 }] });
    assert.equal((await operation).status, 200);
    assert.equal(f.activities.activities()[0].quantity, 4);
    assert.equal(f.activities.activities()[0].cargo, 'boxes');
    f.control.verify();
  } finally { f.injector.destroy(); }
});

test('HTTP Angular: ApiService conserva el error de negocio y cancela el movimiento', async () => {
  const f = httpFixture();
  try {
    const operation = f.api.execute({ id: 'confirm', method: 'POST', path: '/api/v1/orders/{id}/confirm', fields: [], values: { id: '9' } });
    const problem = { code: 'INSUFFICIENT_STOCK', detail: 'No stock' };
    f.control.expectOne('/api/v1/orders/9/confirm').flush(problem, { status: 409, statusText: 'Conflict' });
    await assert.rejects(operation, error => error instanceof HttpErrorResponse && error.status === 409 && error.error === problem);
    assert.equal(f.activities.activities()[0].state, 'error');
    assert.equal(f.activities.activities()[0].cargo, 'none');
    assert.equal(f.activities.pendingCount(), 0);
    f.control.verify();
  } finally { f.injector.destroy(); }
});

test('HTTP Angular: JSON inválido y parámetros ausentes fallan antes de enviar', async () => {
  const f = httpFixture();
  try {
    const action = { id: 'receive', method: 'POST', path: '/api/v1/purchase-orders/{id}/receipts', fields: [], jsonBody: true, values: { id: '3', payload: '{broken' } };
    await assert.rejects(f.api.execute(action), /JSON no es válido/);
    await assert.rejects(f.api.execute({ ...action, values: { id: '', payload: '{}' } }), /Completa el campo id/);
    assert.equal(f.activities.activities().length, 0);
    f.control.expectNone(() => true);
    f.control.verify();
  } finally { f.injector.destroy(); }
});

test('HTTP Angular: respuestas paralelas y cancelación no dejan peticiones pendientes', () => {
  const f = httpFixture();
  try {
    const context = () => new HttpContext().set(WAREHOUSE_ACTION, true);
    const first = f.http.get('/api/v1/orders/1', { context: context() }).subscribe();
    f.http.get('/api/v1/orders/2', { context: context() }).subscribe();
    const cancelled = f.control.expectOne('/api/v1/orders/1');
    f.control.expectOne('/api/v1/orders/2').flush({ id: 2 });
    first.unsubscribe();
    assert.equal(cancelled.cancelled, true);
    assert.equal(f.activities.pendingCount(), 0);
    assert.equal(f.activities.activities().find(item => item.path.endsWith('/1')).state, 'error');
    assert.equal(f.activities.activities().find(item => item.path.endsWith('/2')).state, 'success');
    f.control.verify();
  } finally { f.injector.destroy(); }
});

test('HTTP Angular: cerrar sesión borra el historial y sus identificadores', () => {
  const f = httpFixture();
  try {
    const id = f.activities.begin('GET', '/api/v1/orders/1', null);
    f.activities.succeed(id, { id: 1 }, 200);
    assert.equal(f.activities.activities().length, 1);
    f.api.clearSession();
    assert.equal(f.activities.activities().length, 0);
    f.control.verify();
  } finally { f.injector.destroy(); }
});

test('HTTP Angular: iniciar otra sesión desde el formulario limpia las operaciones anteriores', async () => {
  const f = httpFixture();
  try {
    const id = f.activities.begin('GET', '/api/v1/orders/1', null);
    f.activities.succeed(id, { id: 1 }, 200);
    const login = f.api.login('test-user', 'test-password');
    f.control.expectOne('/api/v1/auth/login').flush({ accessToken: 'test-access', refreshToken: 'test-refresh' });
    await login;
    assert.equal(f.activities.activities().length, 0);
    assert.equal(f.api.accessToken(), 'test-access');
    f.control.verify();
  } finally { f.injector.destroy(); }
});

test('HTTP Angular: login del catálogo limpia la sesión; renovar token conserva su historial', async () => {
  const f = httpFixture();
  try {
    const id = f.activities.begin('GET', '/api/v1/orders/1', null);
    f.activities.succeed(id, { id: 1 }, 200);
    const action = { id: 'login', method: 'POST', path: '/api/v1/auth/login', fields: [], values: {} };
    const login = f.api.execute(action);
    f.control.expectOne(action.path).flush({ accessToken: 'test-access', refreshToken: 'test-refresh' });
    await login;
    assert.equal(f.activities.activities().length, 0);
    const next = f.activities.begin('GET', '/api/v1/orders/2', null);
    f.activities.succeed(next, { id: 2 }, 200);
    const refresh = f.api.execute({ ...action, id: 'refresh', path: '/api/v1/auth/refresh' });
    f.control.expectOne('/api/v1/auth/refresh').flush({ accessToken: 'renewed', refreshToken: 'renewed-refresh' });
    await refresh;
    assert.ok(f.activities.activities().some(event => event.id === next));
    assert.equal(f.api.accessToken(), 'renewed');
    f.control.verify();
  } finally { f.injector.destroy(); }
});
