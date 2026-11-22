import { randomUUID } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { isDeepStrictEqual } from 'node:util';
import { whileStockIsLocked } from './lab-22-lock.mjs';

const root = fileURLToPath(new URL('../', import.meta.url));
const base = 'http://127.0.0.1:38088/api/v1';
const login = { username: 'admin', password: 'DevPassword123!' };
const prepareOnly = process.argv.includes('--preparar');
const run = { ticket: '', token: '', errors: [] };
const unique = () => 'L22-' + randomUUID().replaceAll('-', '').slice(0, 20);

class Prepared extends Error {}

async function http(method, path, body) {
  const headers = { Accept: 'application/json' };
  if (run.token) headers.Authorization = 'Bearer ' + run.token;
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  const res = await fetch(base + path, {
    method, headers, body: JSON.stringify(body), signal: AbortSignal.timeout(30_000)
  });
  const text = await res.text();
  return { status: res.status, data: text ? JSON.parse(text) : null };
}

async function expectHttp(method, path, body, status = 200) {
  const reply = await http(method, path, body);
  if (reply.status !== status) {
    throw new Error(`${method} ${path} esperaba HTTP ${status}, obtuvo ${reply.status} ${reply.data?.code ?? ''}`);
  }
  return reply.data;
}

const get = path => expectHttp('GET', path);
const put = (path, body) => expectHttp('PUT', path, body);
const post = (path, body) => expectHttp('POST', path, body);
const create = (path, body) => expectHttp('POST', path, body, 201);
const draft = (method, path, body) => ({ method, path, body });
const read = path => draft('GET', path);
const send = (path, body) => draft('POST', path, body);

function check(label, actual, expected) {
  if (!isDeepStrictEqual(actual, expected)) {
    run.errors.push(`${label} → esperado ${JSON.stringify(expected)}, obtenido ${JSON.stringify(actual)}`);
  }
}

// Guarda las peticiones con IDs reales y detente justo antes de reproducir el síntoma.
async function breakpoint(requests, note = '') {
  if (!prepareOnly) return;
  const parts = [
    '@base = ' + base,
    '### Inicia sesión una vez',
    'POST {{base}}/auth/login\nContent-Type: application/json\n\n' + JSON.stringify(login),
    '> {% client.global.set("lab22Token", response.body.accessToken); %}'
  ];
  for (const { method, path, body } of requests) {
    let request = method + ' {{base}}' + path + '\nAuthorization: Bearer {{lab22Token}}';
    if (body !== undefined) request += '\nContent-Type: application/json\n\n' + JSON.stringify(body, null, 2);
    parts.push('### ' + method + ' ' + path, request);
  }
  const folder = join(root, 'target', 'lab-22');
  await mkdir(folder, { recursive: true });
  const file = join(folder, run.ticket + '.http');
  const noteLines = note ? note.split('\n').map(s => '# ' + s).join('\n') + '\n\n' : '';
  await writeFile(file, noteLines + parts.join('\n\n') + '\n');
  console.log(`PREPARADO ${run.ticket} · ${file}`);
  if (note) console.log(note);
  throw new Prepared();
}

async function product(price = 10, stock = 0) {
  const sku = unique();
  const { id } = await create('/products', { sku, name: sku, unitPrice: price });
  if (stock) await expectHttp('POST', `/inventory/${id}/adjustments`, { quantity: stock, reason: 'Entrada del laboratorio 22' }, 204);
  return id;
}

async function customer() {
  const taxId = unique();
  return create('/customers', { taxId, name: taxId, email: 'ventas@example.test', address: 'Calle Olmo 4' });
}

async function supplier() {
  const taxId = unique();
  return (await create('/suppliers', { taxId, name: taxId, email: 'compras@example.test', leadTimeDays: 2 })).id;
}

async function order(customerId, lines, complete = false) {
  const { id } = await create('/orders', { customerId });
  for (const [productId, quantity] of lines) await put(`/orders/${id}/lines/${productId}`, { quantity });
  if (complete) {
    await post(`/orders/${id}/confirm`);
    await post(`/orders/${id}/complete`);
  }
  return id;
}

async function purchase(supplierId, lines, submit = true) {
  const { id } = await create('/purchase-orders', { supplierId });
  for (const [productId, quantity] of lines) await put(`/purchase-orders/${id}/lines/${productId}`, { quantity, unitCost: 5 });
  if (submit) await post(`/purchase-orders/${id}/submit`);
  return id;
}

const balance = async id => (await get('/inventory?productId=' + id)).content[0];
const proposalPath = id => '/replenishment/proposals?productId=' + id;
const policyPath = id => '/replenishment/policies/' + id;
const policy = (productId, preferredSupplierId) => put(policyPath(productId), {
  minimumStock: 20, targetStock: 25, preferredSupplierId, active: true
});
const delivery = (productId, quantity) => ({
  requestId: randomUUID(), deliveryReference: unique(), lines: [{ productId, quantity }]
});
function datePlus(date, days) {
  const value = new Date(date);
  value.setUTCDate(value.getUTCDate() + days);
  return value.toISOString().slice(0, 10);
}
const dueDate = () => datePlus(new Date(), 2);
const invoice = (orderId, date = dueDate()) => create('/billing/invoices', { orderId, dueDate: date });

async function orderPage() {
  const { id: client } = await customer();
  const p = await product(10);
  const q = await product(5);
  const first = await order(client, [[p, 1], [q, 2]]);
  const empty = await order(client, []);
  const page = `/orders?customerId=${client}&sort=createdAt,asc`;
  await breakpoint([read('/orders/' + first), read(page + '&page=0&size=1'), read(page + '&page=1&size=1'), read(page + '&page=0&size=3')]);
  const detail = await get('/orders/' + first);
  const one = await get(page + '&page=0&size=1');
  const two = await get(page + '&page=1&size=1');
  const wide = await get(page + '&page=0&size=3');
  check('detalle: total y productos', [detail.total, detail.lines.map(l => l.productId)], [20, [p, q]]);
  check('página 0: pedidos, páginas e ID', [one.totalElements, one.totalPages, one.content.map(o => o.id)], [2, 2, [first]]);
  const lineItems = order => order?.lines.map(line => [line.productId, line.quantity, line.unitPrice]);
  check('productos, cantidades y precios con size=1', lineItems(one.content[0]), lineItems(detail));
  check('página 1: pedido vacío', two.content.map(o => [o.id, o.lines]), [[empty, []]]);
  check('mismas líneas con size=3', lineItems(wide.content[0]), lineItems(detail));
}

async function partialReturn() {
  const { id: client } = await customer();
  const p = await product(12.50, 8);
  const q = await product(3, 5);
  const sale = await order(client, [[p, 4], [q, 2]], true);
  const body = { orderId: sale, requestId: randomUUID(), reason: 'Dos referencias devueltas',
    lines: [{ productId: p, quantity: 1 }, { productId: q, quantity: 2 }] };
  const excess = { ...body, requestId: randomUUID(), lines: [{ productId: p, quantity: 4 }] };
  await breakpoint([read('/orders/' + sale), send('/returns', body), read('/returns?orderId=' + sale), send('/returns', excess)]);
  const result = await create('/returns', body);
  check('importes de las líneas', result.lines.map(l => l.creditAmount), [12.5, 6]);
  check('abono registrado en cabecera', (await get('/returns/' + result.id)).creditAmount, 18.5);
  const denied = await http('POST', '/returns', excess);
  check('exceso de unidades', [denied.status, denied.data?.code], [409, 'RETURN_QUANTITY_EXCEEDED']);
}

async function incomingStock() {
  const p = await product();
  const s = await supplier();
  await policy(p, s);
  const po = await purchase(s, [[p, 10]]);
  await purchase(s, [[p, 20]], false);
  const path = '/purchase-orders/' + po;
  const receipt = delivery(p, 4);
  const proposal = proposalPath(p);
  await breakpoint([read(proposal), send(path + '/receipts', receipt), read(path), read('/inventory?productId=' + p), read(proposal)]);
  const position = async () => {
    const row = (await get(proposal)).content[0];
    return row && [row.available, row.incoming, row.projected, row.suggestedQuantity];
  };
  check('antes de recibir', await position(), [0, 10, 10, 15]);
  await post(path + '/receipts', receipt);
  const received = await get(path);
  check('compra y stock físico', [received.status, received.lines[0].outstandingQuantity, (await balance(p)).onHand], ['PARTIALLY_RECEIVED', 6, 4]);
  check('disponible, pendiente, proyectado, sugerido', await position(), [4, 6, 10, 15]);
}

async function paymentReplay() {
  const { id: client } = await customer();
  const p = await product(20, 6);
  const bill = await invoice(await order(client, [[p, 2]], true));
  const path = '/billing/invoices/' + bill.id;
  const payment = { requestId: randomUUID(), amount: 12.5, reference: 'Transferencia L22' };
  const first = await post(path + '/payments', payment);
  const changed = { ...payment, amount: 12.51 };
  await breakpoint([read(path + '/entries'), send(path + '/payments', payment), send(path + '/payments', changed), read(path), read(path + '/entries')]);
  const replay = await http('POST', path + '/payments', payment);
  check('reenvío del pago', [replay.status, replay.data?.id ?? replay.data?.code], [200, first.id]);
  const conflict = await http('POST', path + '/payments', changed);
  check('mismo UUID con un céntimo más', [conflict.status, conflict.data?.code], [409, 'IDEMPOTENCY_CONFLICT']);
  const current = await get(path);
  check('apuntes, neto cobrado y saldo', [(await get(path + '/entries')).totalElements, current.netPaid, current.balance], [1, 12.5, 35]);
}

async function aging() {
  const { id: client } = await customer();
  const p = await product(10, 4);
  const bill = await invoice(await order(client, [[p, 1]], true));
  const cuts = [[0, 'NOT_DUE'], [1, '1_30'], [30, '1_30'], [31, '31_60'], [60, '31_60'], [61, '61_90'], [90, '61_90'], [91, 'OVER_90']];
  const report = days => `/billing/receivables?customerId=${client}&asOf=${datePlus(bill.dueDate, days)}`;
  await breakpoint([read('/billing/invoices/' + bill.id), ...cuts.map(([days]) => read(report(days)))]);
  for (const [days, name] of cuts) {
    const data = await get(report(days));
    check('día +' + days, data.buckets.filter(b => b.invoiceCount).map(b => [b.name, b.invoiceCount, b.amount]), [[name, 1, 17.5]]);
    check('pendiente y vencido +' + days, [data.outstanding, data.overdue], [17.5, days === 0 ? 0 : 17.5]);
  }
}

async function rejectedConfirmation() {
  const { id: client } = await customer();
  const p = await product(10, 5);
  const q = await product(10, 5);
  const sale = await order(client, [[p, 2], [q, 1]]);
  await expectHttp('DELETE', '/products/' + q, undefined, 204);
  const path = '/orders/' + sale;
  const stock = '/inventory?productId=' + p;
  const movements = '/inventory/movements?productId=' + p;
  await breakpoint([send(path + '/confirm'), read(path), read(stock), read(movements),
    draft('DELETE', path + '/lines/' + q), send(path + '/confirm'), read(stock)]);
  const failed = await http('POST', path + '/confirm');
  check('rechazo por producto inactivo', [failed.status, failed.data?.code], [409, 'PRODUCT_UNAVAILABLE']);
  check('estado tras el rechazo', (await get(path)).status, 'DRAFT');
  check('reserva tras el rechazo', (await balance(p)).reserved, 0);
  check('movimientos del pedido rechazado', (await get(movements)).content.filter(m => m.orderId === sale).length, 0);
  await expectHttp('DELETE', path + '/lines/' + q);
  const retry = await http('POST', path + '/confirm');
  check('reintento tras retirar la línea inactiva', [retry.status, retry.data?.status === 'CONFIRMED'], [200, true]);
  check('reserva final', (await balance(p)).reserved, 2);
}

async function splitDelivery() {
  const p = await product();
  const q = await product();
  const po = await purchase(await supplier(), [[p, 2], [q, 3]]);
  const path = '/purchase-orders/' + po;
  const a = delivery(p, 2);
  const b = delivery(q, 3);
  await breakpoint([send(path + '/receipts', a), read(path), send(path + '/receipts', b), read(path), read(path + '/receipts')]);
  await post(path + '/receipts', a);
  const halfway = await get(path);
  check('pendiente por referencia', halfway.lines.map(l => l.outstandingQuantity), [0, 3]);
  check('estado tras la primera caja', halfway.status, 'PARTIALLY_RECEIVED');
  const next = await http('POST', path + '/receipts', b);
  check('segunda entrega', [next.status, next.data?.code ?? null], [200, null]);
  const final = await get(path);
  check('estado y unidades recibidas al terminar', [final.status, final.lines.map(l => l.receivedQuantity)], ['RECEIVED', [2, 3]]);
  check('recepciones y stock', [(await get(path + '/receipts')).totalElements, (await balance(p)).onHand, (await balance(q)).onHand], [2, 2, 3]);
}

async function billingAddress() {
  const client = await customer();
  const p = await product(10, 5);
  const original = await invoice(await order(client.id, [[p, 1]], true));
  const nextOrder = await order(client.id, [[p, 1]], true);
  const { name, taxId, email, version } = client;
  const changed = { name, taxId, email, version, address: 'Calle Roble 9' };
  const terms = { orderId: nextOrder, dueDate: dueDate() };
  const bills = '/billing/invoices';
  const old = bills + '/' + original.id;
  const list = bills + '?customerId=' + client.id;
  const contact = '/customers/' + client.id;
  await breakpoint([read(old), draft('PUT', contact, changed), read(contact), read(old), send(bills, terms), read(list)]);
  check('dirección al emitir', original.customerAddress, 'Calle Olmo 4');
  await put(contact, changed);
  check('ficha actual del cliente', (await get(contact)).address, 'Calle Roble 9');
  check('dirección del documento anterior', (await get(old)).customerAddress, 'Calle Olmo 4');
  const next = await create(bills, terms);
  check('dirección al emitir el nuevo documento', next.customerAddress, 'Calle Roble 9');
  const rows = (await get(list)).content;
  check('direcciones del listado', [original, next].map(bill => rows.find(i => i.id === bill.id)?.customerAddress),
    ['Calle Olmo 4', 'Calle Roble 9']);
}

async function optionalSupplier() {
  const active = await supplier();
  const inactive = await supplier();
  const p = await product();
  const q = await product();
  const r = await product();
  await policy(p, null);
  await policy(q, active);
  await policy(r, inactive);
  await expectHttp('DELETE', '/suppliers/' + inactive, undefined, 204);
  await breakpoint([read(policyPath(p)), ...[p, q, r].map(id => read(proposalPath(id)))]);
  check('proveedor en la política', (await get(policyPath(p))).preferredSupplierId, null);
  const state = async id => {
    const { content } = await get(proposalPath(id));
    const row = content[0];
    return row && [row.preferredSupplierId, row.supplierActive, row.purchasable, row.blockingReason];
  };
  check('sin asignar', await state(p), [null, null, false, 'NO_PREFERRED_SUPPLIER']);
  check('proveedor activo', await state(q), [active, true, true, null]);
  check('proveedor desactivado', await state(r), [inactive, false, false, 'SUPPLIER_INACTIVE']);
}

async function occupiedStock() {
  const { id: client } = await customer();
  const p = await product(10, 5);
  const first = await order(client, [[p, 4]]);
  const second = await order(client, [[p, 2]]);
  const confirm = '/orders/' + first + '/confirm';
  const other = '/orders/' + second + '/confirm';
  const lockSql = `SELECT product_id FROM inventory_balance WHERE product_id=${p} FOR UPDATE;`;
  const note = [
    'Después del login, abre una sesión SQL en la base del laboratorio.',
    'Ejecuta BEGIN; y luego ' + lockSql,
    'Envía la primera confirmación mientras esa sesión sigue abierta. Ejecuta COMMIT; en SQL.',
    'Si la primera confirmación falló, repítela tras el COMMIT antes de probar el segundo pedido.'
  ].join('\n');
  await breakpoint([send(confirm), read('/orders/' + first), send(other), read('/inventory?productId=' + p)], note);
  const { waited, reply } = await whileStockIsLocked(p, () => http('POST', confirm));
  check('PostgreSQL vio la confirmación esperar', waited, true);
  check('resultado del primer intento', [reply.status, reply.data?.code ?? null], [200, null]);
  if (reply.status !== 200) {
    const retry = await http('POST', confirm);
    check('confirmación con la fila ya libre', retry.status, 200);
  }
  const insufficient = await http('POST', other);
  check('dos unidades cuando queda una', [insufficient.status, insufficient.data?.code], [409, 'INSUFFICIENT_STOCK']);
  const stock = await balance(p);
  check('físico, reservado, disponible', [stock.onHand, stock.reserved, stock.available], [5, 4, 1]);
  const history = (await get('/inventory/movements?productId=' + p)).content;
  check('reservas registradas', history.filter(m => m.kind === 'RESERVE').map(m => [m.orderId, m.quantity]), [[first, 4]]);
}

const tickets = {
  '01': orderPage, '02': partialReturn, '03': incomingStock, '04': paymentReplay, '05': aging,
  '06': rejectedConfirmation, '07': splitDelivery, '08': billingAddress, '09': optionalSupplier, '10': occupiedStock
};

async function main() {
  const [selected, flag, ...extra] = process.argv.slice(2);
  if (!(selected === 'todos' || Object.hasOwn(tickets, selected)) || (flag && flag !== '--preparar') || extra.length) {
    console.error('Uso: node scripts/lab-22.mjs 01..10|todos [--preparar]');
    return 2;
  }
  run.token = (await post('/auth/login', login)).accessToken;
  const selectedTickets = selected === 'todos' ? Object.keys(tickets).sort() : [selected];
  let result = 0;
  for (const id of selectedTickets) {
    run.ticket = id;
    run.errors = [];
    try {
      await tickets[id]();
      if (run.errors.length) {
        console.log('FAIL ' + id + '\n  ' + run.errors.join('\n  '));
        result = Math.max(result, 1);
      } else {
        console.log('PASS ' + id);
      }
    } catch (error) {
      if (error instanceof Prepared) continue;
      console.error('ERROR ' + id + ' · ' + error.message);
      result = 2;
    }
  }
  return result;
}

try {
  process.exitCode = await main();
} catch (error) {
  console.error('ERROR · ' + error.message + '. Comprueba que Backend APP y Shipping APP estén arrancadas.');
  process.exitCode = 2;
}
