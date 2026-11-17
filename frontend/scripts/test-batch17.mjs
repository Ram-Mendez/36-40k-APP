import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { createRequire } from 'node:module';
import { test } from 'node:test';
import ts from 'typescript';
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
const modelModule = await localModule('warehouse-activity', {});
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


const demoModule = await localModule('demo-data', {});
const workspaceModule = await localModule('workspace.service', { './api.service': apiModule, './demo-data': demoModule });
const { WorkspaceService } = await import(workspaceModule);

test('batch17: late live responses cannot replace demo data after logout', async () => {
  const f = httpFixture();
  const child = createEnvironmentInjector([WorkspaceService], f.injector);
  try {
    const workspace = child.get(WorkspaceService);
    const loading = workspace.load();
    const requests = f.control.match(() => true);
    assert.equal(requests.length, 6);
    workspace.loadDemo();
    const demoProducts = workspace.products();
    const demoOrders = workspace.orders();
    for (const req of requests) req.flush({ content: [{ id: 99001 }], totalPages: 1 });
    await loading;
    assert.deepEqual(workspace.products(), demoProducts);
    assert.deepEqual(workspace.orders(), demoOrders);
    assert.equal(workspace.demo(), true);
    assert.equal(workspace.loading(), false);
    f.control.verify();
  } finally { child.destroy(); f.injector.destroy(); }
});

test('batch17: current live responses still populate the workspace', async () => {
  const f = httpFixture();
  const child = createEnvironmentInjector([WorkspaceService], f.injector);
  try {
    const workspace = child.get(WorkspaceService);
    const loading = workspace.load();
    for (const req of f.control.match(() => true)) req.flush({ content: [{ id: 99002 }], totalPages: 1 });
    await loading;
    assert.deepEqual(workspace.products(), [{ id: 99002 }]);
    assert.equal(workspace.demo(), false);
    f.control.verify();
  } finally { child.destroy(); f.injector.destroy(); }
});