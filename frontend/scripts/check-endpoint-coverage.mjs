import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { dirname, resolve, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import ts from 'typescript';

const frontend = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const modules = new Map();
function load(name) {
  const filename = resolve(frontend, 'src/app', `${name}.ts`);
  if (modules.has(filename)) return modules.get(filename).exports;
  const module = { exports: {} };
  modules.set(filename, module);
  const compiled = ts.transpileModule(readFileSync(filename, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
  }).outputText;
  new Function('module', 'exports', 'require', compiled)(module, module.exports, requested => {
    assert(requested.startsWith('./'), `Importación inesperada: ${requested}`);
    return load(requested.slice(2));
  });
  return module.exports;
}

function javaFiles(directory) {
  return readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const path = join(directory, entry.name);
    return entry.isDirectory() ? javaFiles(path) : entry.name.endsWith('Controller.java') ? [path] : [];
  });
}

const expected = new Set();
for (const filename of javaFiles(resolve(frontend, '../src/main/java/com/acme/operations'))) {
  const source = readFileSync(filename, 'utf8');
  const base = source.match(/@RequestMapping\("([^"]+)"\)/)?.[1];
  assert(base, `Controlador sin prefijo reconocible: ${filename}`);
  for (const match of source.matchAll(/@(Get|Post|Put|Delete|Patch)Mapping(?:\("([^"]*)"\))?/g)) {
    expected.add(`${match[1].toUpperCase()} ${base}${match[2] ?? ''}`);
  }
}

const actions = load('actions').createSections().flatMap(section => section.actions);
const actual = new Set(actions.map(action => action.method));
assert.equal(actual.size, actions.length, 'Hay endpoints repetidos en los formularios');
assert.equal(new Set(actions.map(action => action.id)).size, actions.length, 'Hay IDs de acción repetidos');
assert.deepEqual([...expected].filter(endpoint => !actual.has(endpoint)), [], 'Faltan operaciones del backend');
assert.deepEqual([...actual].filter(endpoint => !expected.has(endpoint)), [], 'Se ofrecen operaciones inexistentes');

for (const action of actions) {
  const fields = new Set(action.fields.map(field => field[0]));
  for (const match of action.path.matchAll(/\{(\w+)\}/g)) {
    assert(fields.has(match[1]), `${action.id}: falta el campo ${match[1]} de la ruta`);
    assert(!action.query?.includes(match[1]), `${action.id}: un campo de ruta se envía como consulta`);
  }
  for (const name of [...(action.body ?? []), ...(action.query ?? [])]) {
    assert(fields.has(name), `${action.id}: falta el campo ${name} del cuerpo o filtro`);
  }
  if (action.jsonBody) {
    const body = JSON.parse(action.values.payload);
    assert(body && !Array.isArray(body) && typeof body === 'object', `${action.id}: plantilla JSON inválida`);
    if (body.requestId !== undefined) assert.equal(body.requestId, 'GENERAR_UUID', `${action.id}: UUID fijo reutilizable entre operaciones`);
  }
}

const get = id => actions.find(action => action.id === id);
const payload = id => JSON.parse(get(id).values.payload);
assert.deepEqual(Object.keys(payload('purchase-receive')).sort(), ['deliveryReference', 'lines', 'requestId']);
assert.deepEqual(Object.keys(payload('return-create')).sort(), ['lines', 'orderId', 'reason', 'requestId']);
assert.deepEqual(Object.keys(payload('return-receive')).sort(), ['lines', 'requestId', 'version']);
assert.deepEqual(Object.keys(payload('return-receive').lines[0]).sort(), ['damagedQuantity', 'productId', 'restockQuantity']);
assert.equal(payload('policy-set').active, true);
assert.equal(payload('policy-set').preferredSupplierId, null);
assert.equal(payload('policy-set').version, undefined, 'Crear política debe omitir la versión');
assert.equal(get('invoice-payment').values.requestId, '', 'El formulario debe generar una identidad nueva al abrirse');
assert.equal(get('invoice-refund').values.requestId, '');
assert(get('supplier-create').body.includes('leadTimeDays'));
assert(get('supplier-update').body.includes('version'));
console.log(`${actual.size}/${expected.size} endpoints con formulario; rutas, filtros y plantillas JSON correctos.`);
