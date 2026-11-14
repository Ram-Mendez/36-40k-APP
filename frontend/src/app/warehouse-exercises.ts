export interface WarehouseStep {
  title: string;
  method: 'GET' | 'POST' | 'PUT' | 'DELETE';
  path: string;
  body?: object;
  capture?: Record<string, string>;
  concurrentPayments?: boolean;
}

export interface WarehouseExercise {
  title: string;
  scenario: string;
  expected: string;
  steps: WarehouseStep[];
}

const get = (title: string, path: string): WarehouseStep => ({ title, path, method: 'GET' });
const post = (title: string, path: string, body?: object, capture?: Record<string, string>): WarehouseStep =>
  ({ title, path, body, capture, method: 'POST' });
const put = (title: string, path: string, body: object, capture?: Record<string, string>): WarehouseStep =>
  ({ title, path, body, capture, method: 'PUT' });
const deactivate = (title: string, path: string): WarehouseStep => ({ title, path, method: 'DELETE' });

function product(key = 'productId'): WarehouseStep {
  return post('Create product ' + (key === 'productId' ? 'P' : 'Q'), '/products',
    { sku: '{productSku}', name: '{productName}', unitPrice: '{price}' }, { id: key });
}
function supplier(key = 'supplierId'): WarehouseStep {
  return post('Create supplier ' + (key === 'supplierId' ? 'A' : 'B'), '/suppliers',
    { name: '{supplierName}', taxId: '{taxId}', email: '{email}', leadTimeDays: 2 }, { id: key });
}
const customer = post('Create the customer', '/customers',
  { name: '{customerName}', taxId: '{taxId}', email: '{email}', address: '{address}' }, { id: 'customerId' });
const stock = get('View stock for this product', '/inventory?productId={productId}');
const movements = get('View stock movements', '/inventory/movements?productId={productId}');
const reservations = get('Look up the order that reserved each product', '/inventory/reservations?page={page}&size={size}');
const purchase = get('View the purchase order and its outstanding units', '/purchase-orders/{purchaseId}');
const newPurchase = post('Prepare the purchase order', '/purchase-orders', { supplierId: '{supplierId}' }, { id: 'purchaseId' });
const policy = { ...get('View the policy and its version', '/replenishment/policies/{productId}'), capture: { version: 'policyVersion' } };
const proposals = get('View purchase proposals', '/replenishment/proposals?preferredSupplierId={supplierId}&page={page}&size={size}');
const invoice = get('Check the outstanding balance', '/billing/invoices/{invoiceId}');
const entries = get('View invoice payments and refunds', '/billing/invoices/{invoiceId}/entries');
const report = get('View the low-stock report', '/reports/low-stock?threshold={threshold}&page={page}&size={size}');
const adjust = (quantity: number, key = 'productId') => post('Add ' + quantity + ' units to stock', '/inventory/{' + key + '}/adjustments', { quantity, reason: 'Opening stock for this exercise' });
const order = post('Open the customer order', '/orders', { customerId: '{customerId}' }, { id: 'orderId' });
const confirm = post('Confirm the order and reserve the stock', '/orders/{orderId}/confirm');
const complete = post('Complete the sale and record the stock movement', '/orders/{orderId}/complete');
const orderLine = (quantity: number, key = 'productId') => put('Add ' + quantity + ' units to the order', '/orders/{orderId}/lines/{' + key + '}', { quantity });
const issue = post('Issue the invoice for the order', '/billing/invoices',
  { orderId: '{orderId}', dueDate: '{date}' }, { id: 'invoiceId' });
const requestReturn = (quantity: number, key: string) => post('Request a return for ' + quantity + ' units', '/returns',
  { orderId: '{orderId}', requestId: '{uuid}', reason: '{reason}', lines: [{ productId: '{productId}', quantity }] },
  key === 'returnId' ? { id: key, version: 'returnVersion' } : { id: key });
function configurePolicy(productKey: string, supplierKey: string, update: boolean): WarehouseStep {
  const version = productKey === 'secondProductId' ? 'secondPolicyVersion' : 'policyVersion';
  return put(update ? 'Save changes to the policy' : 'Set the minimum stock, target stock and preferred supplier', '/replenishment/policies/{' + productKey + '}',
    { minimumStock: '{minimum}', targetStock: '{target}', preferredSupplierId: '{' + supplierKey + '}', active: true,
      ...(update ? { version: '{' + version + '}' } : {}) }, { version });
}
const payment = post('Record a €70 payment', '/billing/invoices/{invoiceId}/payments',
  { requestId: '{uuid}', amount: 70, reference: '{paymentReference}' });

// Each step runs on demand and captures IDs from the server response.
export const warehouseExercises: WarehouseExercise[] = [
  {
    title: '01 · Cancel a partially received purchase order',
    scenario: 'Purchasing orders 10 units. The warehouse receives 3 and signs the delivery note. Purchasing tries to cancel the order while 7 units are outstanding.',
    expected: 'Cancellation should return 409. Compare the purchase order, receipt, stock and movements before and after the attempt. The draft order in the final step can be cancelled.',
    steps: [supplier(), product(), newPurchase,
      put('Order 10 units at €8 each', '/purchase-orders/{purchaseId}/lines/{productId}', { quantity: 10, unitCost: 8 }),
      post('Submit the purchase order to the supplier', '/purchase-orders/{purchaseId}/submit'),
      post('Receive the 3 units delivered', '/purchase-orders/{purchaseId}/receipts',
        { requestId: '{uuid}', deliveryReference: '{deliveryReference}', lines: [{ productId: '{productId}', quantity: 3 }] }, { id: 'receiptId' }),
      purchase, get('Read the delivery note', '/purchase-orders/{purchaseId}/receipts/{receiptId}'), stock, movements,
      post('Attempt to cancel the purchase order', '/purchase-orders/{purchaseId}/cancel'),
      { ...purchase, title: 'View the purchase order after the cancellation attempt' }, get('Read the delivery note again', '/purchase-orders/{purchaseId}/receipts/{receiptId}'), stock, movements,
      post('Create another draft for comparison', '/purchase-orders', { supplierId: '{supplierId}' }, { id: 'draftId' }),
      post('Cancel the draft only', '/purchase-orders/{draftId}/cancel')]
  },
  {
    title: '02 · Request another return',
    scenario: 'A customer bought 5 units and requested a return for 4. The reviewer rejects the request before the goods reach the warehouse.',
    expected: 'Requesting 5 units while 4 are under review should return 409. Reject the first request, then request all 5 again. Check both requests, stock and return credits.',
    steps: [customer, product(), adjust(5), order, orderLine(5), confirm, complete, issue, stock,
      requestReturn(4, 'returnId'),
      requestReturn(5, 'newReturnId'),
      post('Reject the first request', '/returns/{returnId}/reject', { version: '{returnVersion}', reason: 'Request rejected after review' }),
      { ...requestReturn(5, 'newReturnId'), title: 'Request all 5 units again after rejection' },
      get('View the previous request', '/returns/{returnId}'), get('View the new request', '/returns/{newReturnId}'),
      get('View credits for received returns', '/billing/invoices/{invoiceId}/credits'), stock, movements]
  },
  {
    title: '03 · Change the preferred supplier',
    scenario: 'Supplier A currently supplies this product. Purchasing wants to switch to supplier B, who has just stopped supplying goods.',
    expected: 'Switching to B should return 409. Read the policy again to check its supplier, version and quantities. Finally, change only the thresholds while keeping supplier A.',
    steps: [product(), supplier(), supplier('secondSupplierId'), configurePolicy('productId', 'supplierId', false),
      deactivate('Deactivate supplier B', '/suppliers/{secondSupplierId}'), configurePolicy('productId', 'secondSupplierId', true), policy, proposals,
      product('secondProductId'), configurePolicy('secondProductId', 'supplierId', false),
      deactivate('Deactivate the original supplier A', '/suppliers/{supplierId}'),
      { ...configurePolicy('secondProductId', 'supplierId', true), title: 'Change the stock thresholds for Q and keep supplier A' }]
  },
  {
    title: '04 · Count orders with reservations',
    scenario: 'P starts with 5 units and Q has none. Sales confirms an order for 2 units of P. Only P should have one confirmed order in the report.',
    expected: 'Find both products in the report. P should have one confirmed order and Q should have none. Add stock for Q, then confirm a second order containing only Q.',
    steps: [customer, product(), product('secondProductId'), adjust(5), order, orderLine(2), confirm,
      reservations, stock, report, adjust(2, 'secondProductId'), { ...order, title: 'Open a second order for Q' }, orderLine(1, 'secondProductId'), confirm, reservations, report]
  },
  {
    title: '05 · Record two payments against one invoice',
    scenario: 'Two people each try to collect €70 on a €100 invoice. Check the total including shipping before submitting both payments.',
    expected: 'One payment should succeed and the other should return 409. Check that one payment was recorded and €30 is outstanding. The browser submits both requests together. A server-side concurrency test must verify that the transactions actually overlap.',
    steps: [customer, product(), adjust(2), order, orderLine(1), confirm, complete, issue, invoice,
      { ...payment, title: 'Submit both payments at once', concurrentPayments: true }, entries,
      { ...order, title: 'Open another order for the sequential test' }, orderLine(1), confirm, complete, issue, invoice,
      { ...payment, title: 'Record the first sequential payment' }, { ...payment, title: 'Attempt the second sequential payment' }, invoice, entries]
  },
  {
    title: 'Proposals · Prepare a draft purchase order',
    scenario: 'Purchasing selects two products that need replenishment from the same supplier. The draft uses the suggested quantity for each product.',
    expected: 'Draft creation is pending implementation. You can set up both products and inspect their proposals now. Once the server supports this operation, you can create and inspect the draft.',
    steps: [supplier(), product(), product('secondProductId'), configurePolicy('productId', 'supplierId', false),
      configurePolicy('secondProductId', 'supplierId', false), proposals,
      post('Generate the draft once the server supports it', '/api/v1/replenishment/purchase-drafts',
        { supplierId: '{supplierId}', lines: [{ productId: '{productId}', unitCost: '{firstCost}' }, { productId: '{secondProductId}', unitCost: '{secondCost}' }] }, { id: 'purchaseId' }),
      purchase, stock]
  },
  {
    title: 'Integration · Sort returns and proposals',
    scenario: 'After merging changes A and B into your branch, compare the same queries on consecutive pages.',
    expected: 'Returns are sorted by status, then by ID. Proposals with the largest suggested quantities should appear first. Change the page and page size to verify the order and totals.',
    steps: [get('List returns by status', '/returns?sort={returnSort}&page={page}&size={size}'),
      get('Compare proposals for all suppliers', '/replenishment/proposals?page={page}&size={size}')]
  }
];
