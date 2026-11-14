import { warehouseSections } from './warehouse-actions';

export interface ActionDefinition { id: string; title: string; method: string; path: string; fields: FieldTuple[]; body?: string[]; query?: string[]; jsonBody?: boolean; values: Record<string, string>; }
type FieldTuple = [key: string, label: string, value?: string, type?: string];
export interface Section { id: string; label: string; icon: string; actions: ActionDefinition[]; }


export function createSections(): Section[] {
  return [
    {
      id: 'auth', label: 'Authentication', icon: '↪', actions: [
        action('login', 'Sign in', 'POST /api/v1/auth/login', '/api/v1/auth/login', [
          ['username', 'Username', 'admin'], ['password', 'Password', '', 'password']], ['username', 'password']),
        action('refresh', 'Refresh token', 'POST /api/v1/auth/refresh', '/api/v1/auth/refresh', [
          ['refreshToken', 'Refresh token', '']], ['refreshToken']),
        action('logout', 'Sign out', 'POST /api/v1/auth/logout', '/api/v1/auth/logout'),
        action('password', 'Change password', 'PUT /api/v1/auth/password', '/api/v1/auth/password', [
          ['currentPassword', 'Current password', '', 'password'], ['newPassword', 'New password', '', 'password']], ['currentPassword', 'newPassword']),
      ],
    },
    {
      id: 'catalog', label: 'Catalog', icon: '▦', actions: [
        action('products', 'List products', 'GET /api/v1/products', '/api/v1/products', undefined, undefined, ['page', 'size', 'search', 'active'], { page: '0', size: '20' }),
        action('product-create', 'Create product', 'POST /api/v1/products', '/api/v1/products', [
          ['sku', 'SKU', 'DEMO-01'], ['name', 'Name', 'Demo product'], ['unitPrice', 'Price', '10.00'], ['version', 'Version', '0']], ['sku', 'name', 'unitPrice', 'version']),
        action('product-get', 'View product', 'GET /api/v1/products/{id}', '/api/v1/products/{id}', [['id', 'ID', '1']]),
        action('product-update', 'Update product', 'PUT /api/v1/products/{id}', '/api/v1/products/{id}', [
          ['id', 'ID', '1'], ['sku', 'SKU', 'DEMO-01'], ['name', 'Name', 'Demo product'], ['unitPrice', 'Price', '10.00'], ['version', 'Version', '0']], ['sku', 'name', 'unitPrice', 'version']),
        action('product-delete', 'Deactivate product', 'DELETE /api/v1/products/{id}', '/api/v1/products/{id}', [['id', 'ID', '1']]),
        action('customers', 'List customers', 'GET /api/v1/customers', '/api/v1/customers', undefined, undefined, ['page', 'size', 'search', 'active'], { page: '0', size: '20' }),
        action('customer-create', 'Create customer', 'POST /api/v1/customers', '/api/v1/customers', [
          ['name', 'Name', 'Demo customer'], ['taxId', 'Tax ID', 'B12345678'], ['email', 'Email', 'demo@example.com'], ['address', 'Address', 'Madrid'], ['version', 'Version', '0']], ['name', 'taxId', 'email', 'address', 'version']),
        action('customer-get', 'View customer', 'GET /api/v1/customers/{id}', '/api/v1/customers/{id}', [['id', 'ID', '1']]),
        action('customer-update', 'Update customer', 'PUT /api/v1/customers/{id}', '/api/v1/customers/{id}', [
          ['id', 'ID', '1'], ['name', 'Name', 'Demo customer'], ['taxId', 'Tax ID', 'B12345678'], ['email', 'Email', 'demo@example.com'], ['address', 'Address', 'Madrid'], ['version', 'Version', '0']], ['name', 'taxId', 'email', 'address', 'version']),
        action('customer-delete', 'Deactivate customer', 'DELETE /api/v1/customers/{id}', '/api/v1/customers/{id}', [['id', 'ID', '1']]),
      ],
    },
    {
      id: 'inventory', label: 'Inventory', icon: '◈', actions: [
        action('balances', 'View balances', 'GET /api/v1/inventory', '/api/v1/inventory', undefined, undefined, ['productId', 'page', 'size'], { page: '0', size: '20' }),
        action('adjust', 'Adjust stock', 'POST /api/v1/inventory/{productId}/adjustments', '/api/v1/inventory/{productId}/adjustments', [
          ['productId', 'Product', '1'], ['quantity', 'Quantity', '1'], ['reason', 'Reason', 'Test adjustment', 'textarea']], ['quantity', 'reason']),
        action('movements', 'View movements', 'GET /api/v1/inventory/movements', '/api/v1/inventory/movements', undefined, undefined, ['productId', 'page', 'size'], { page: '0', size: '20' }),
        action('reservations', 'View reservations', 'GET /api/v1/inventory/reservations', '/api/v1/inventory/reservations', undefined, undefined, ['page', 'size'], { page: '0', size: '20' }),
      ],
    },
    {
      id: 'orders', label: 'Orders', icon: '□', actions: [
        action('orders', 'List orders', 'GET /api/v1/orders', '/api/v1/orders', undefined, undefined, ['customerId', 'status', 'page', 'size'], { page: '0', size: '20' }),
        action('order-create', 'Create order', 'POST /api/v1/orders', '/api/v1/orders', [['customerId', 'Customer', '1']], ['customerId']),
        action('order-get', 'View order', 'GET /api/v1/orders/{id}', '/api/v1/orders/{id}', [['id', 'ID', '1']]),
        action('order-update', 'Change customer', 'PUT /api/v1/orders/{id}', '/api/v1/orders/{id}', [['id', 'Order', '1'], ['customerId', 'Customer', '1']], ['customerId']),
        action('line-set', 'Add line', 'PUT /api/v1/orders/{id}/lines/{productId}', '/api/v1/orders/{id}/lines/{productId}', [['id', 'Order', '1'], ['productId', 'Product', '1'], ['quantity', 'Quantity', '1']], ['quantity']),
        action('line-delete', 'Remove line', 'DELETE /api/v1/orders/{id}/lines/{productId}', '/api/v1/orders/{id}/lines/{productId}', [['id', 'Order', '1'], ['productId', 'Product', '1']]),
        ...(['confirm', 'cancel', 'complete'] as const).map((operation) => action(`order-${operation}`, `${operation[0].toUpperCase()}${operation.slice(1)} order`, `POST /api/v1/orders/{id}/${operation}`, `/api/v1/orders/{id}/${operation}`, [['id', 'Order', '1']])),
      ],
    },
    {
      id: 'admin', label: 'Reports and administration', icon: '◫', actions: [
        action('sales', 'Sales by customer', 'GET /api/v1/reports/sales', '/api/v1/reports/sales', undefined, undefined, ['from', 'to', 'page', 'size'], { page: '0', size: '20' }),
        action('low-stock', 'Low stock', 'GET /api/v1/reports/low-stock', '/api/v1/reports/low-stock', undefined, undefined, ['threshold', 'page', 'size'], { threshold: '10', page: '0', size: '20' }),
        action('audit', 'Audit log', 'GET /api/v1/audit', '/api/v1/audit', undefined, undefined, ['page', 'size', 'entityType', 'entityId', 'requestId'], { page: '0', size: '20' }),
        action('users', 'List users', 'GET /api/v1/users', '/api/v1/users', undefined, undefined, ['page', 'size', 'query', 'active'], { page: '0', size: '20' }),
        action('user-create', 'Create user', 'POST /api/v1/users', '/api/v1/users', [['username', 'Username', 'demo-user'], ['password', 'Password', '', 'password'], ['role', 'Role', 'READONLY']], ['username', 'password', 'role']),
        action('user-update', 'Update user', 'PUT /api/v1/users/{id}', '/api/v1/users/{id}', [['id', 'ID', '1'], ['role', 'Role', 'READONLY'], ['active', 'Active', 'true']], ['role', 'active']),
        action('user-delete', 'Deactivate user', 'DELETE /api/v1/users/{id}', '/api/v1/users/{id}', [['id', 'ID', '1']]),
      ],
    },
    ...warehouseSections(),
  ];
}

function action(id: string, title: string, method: string, path: string, fields?: FieldTuple[], body?: string[], query?: string[], defaults?: Record<string, string>): ActionDefinition {
    const values: Record<string, string> = { ...defaults };
    for (const field of fields ?? []) values[field[0]] = field[2] ?? '';
    for (const name of query ?? []) values[name] ??= '';
    return { id, title, method, path, fields: fields ?? (query ?? []).map((name) => [name, name, values[name]]), body, query, values };
  }
