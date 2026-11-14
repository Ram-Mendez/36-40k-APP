import { Balance, Customer, Movement, Order, OrderStatus, Product, Reservation } from './operations.models';

// Sample data stays in the browser and is kept separate from API results.
export function createDemoData() {
  const products: Product[] = [
    { id: 1, sku: 'PKG-001', name: 'Premium kraft box', unitPrice: 4.8, active: true, version: 1 },
    { id: 2, sku: 'PAP-002', name: 'Natural tissue paper', unitPrice: 12.5, active: true, version: 0 },
    { id: 3, sku: 'BAG-003', name: 'Cotton bag', unitPrice: 8.9, active: true, version: 2 },
    { id: 4, sku: 'LBL-004', name: 'Custom labels', unitPrice: 2.4, active: true, version: 0 },
    { id: 5, sku: 'TAP-005', name: 'Recycled paper tape', unitPrice: 6.2, active: true, version: 1 },
    { id: 6, sku: 'ENV-006', name: 'Padded envelope', unitPrice: 3.5, active: true, version: 0 },
  ];
  const customers: Customer[] = ['Estudio Oliva', 'Casa Norte', 'Forma & Co.', 'Atelier Botánica', 'Marea Studio', 'Alma Market'].map((name, i) => ({
    id: i + 1, name, taxId: `DEMO-${i + 1}`, email: `hello@${['oliva', 'casanorte', 'forma', 'botanica', 'marea', 'alma'][i]}.example`,
    address: ['Madrid', 'Barcelona', 'Valencia', 'Sevilla', 'Bilbao', 'Málaga'][i], active: true, version: 0,
  }));
  const date = (days: number) => { const d = new Date(); d.setDate(d.getDate() - days); d.setHours(10, 20, 0, 0); return d.toISOString(); };
  const orders: Order[] = Array.from({ length: 32 }, (_, i) => {
    const c = customers[i % customers.length];
    const status: OrderStatus = i < 3 ? 'CONFIRMED' : i < 5 ? 'DRAFT' : i === 8 ? 'CANCELLED' : 'COMPLETED';
    const p = products[i % products.length];
    const q = 18 + (i % 7) * 12;
    const total = Math.round(p.unitPrice * q * 100) / 100;
    return { id: 1048 - i, customerId: c.id, customerName: c.name, status, total, version: status === 'DRAFT' ? 1 : 2,
      shippingReference: status === 'COMPLETED' ? `NX-${1048 - i}` : null, shippingCost: status === 'COMPLETED' ? 8.5 : null,
      createdAt: date(Math.floor(i * 0.85)), updatedAt: date(Math.floor(i * 0.85)),
      lines: [{ id: i + 1, productId: p.id, sku: p.sku, productName: p.name, quantity: q, unitPrice: p.unitPrice, lineTotal: total }] };
  });
  const reservations: Reservation[] = orders.filter(o => o.status === 'CONFIRMED').flatMap(o => o.lines.map(l => ({ orderId: o.id, productId: l.productId, sku: l.sku, quantity: l.quantity })));
  const balances: Balance[] = products.map((p, i) => {
    const reserved = reservations.filter(r => r.productId === p.id).reduce((a, r) => a + r.quantity, 0);
    const onHand = [420, 260, 180, 8, 6, 94][i];
    return { productId: p.id, sku: p.sku, productName: p.name, onHand, reserved, available: onHand - reserved };
  });
  const movements: Movement[] = orders.filter(o => o.status !== 'DRAFT').flatMap(o => o.lines.map(l => ({
    id: o.id, productId: l.productId, orderId: o.id, kind: o.status === 'COMPLETED' ? 'SHIP' : o.status === 'CANCELLED' ? 'RELEASE' : 'RESERVE',
    quantity: o.status === 'CONFIRMED' ? l.quantity : -l.quantity, reason: o.status === 'COMPLETED' ? 'Order completed' : o.status === 'CANCELLED' ? 'Order cancelled' : 'Order confirmed', createdAt: o.updatedAt,
  })));
  return { products, customers, orders, balances, movements, reservations };
}
