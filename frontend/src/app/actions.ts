export interface ActionDefinition { id: string; title: string; method: string; path: string; fields: FieldTuple[]; body?: string[]; query?: string[]; values: Record<string, string>; }
type FieldTuple = [key: string, label: string, value?: string, type?: string];
export interface Section { id: string; label: string; icon: string; actions: ActionDefinition[]; }


export function createSections(): Section[] {
  return [
    {
      id: 'auth', label: 'Autenticación', icon: '↪', actions: [
        action('login', 'Iniciar sesión', 'POST /api/v1/auth/login', '/api/v1/auth/login', [
          ['username', 'Usuario', 'admin'], ['password', 'Contraseña', '', 'password']], ['username', 'password']),
        action('refresh', 'Renovar token', 'POST /api/v1/auth/refresh', '/api/v1/auth/refresh', [
          ['refreshToken', 'Refresh token', '']], ['refreshToken']),
        action('logout', 'Cerrar sesión', 'POST /api/v1/auth/logout', '/api/v1/auth/logout'),
        action('password', 'Cambiar contraseña', 'PUT /api/v1/auth/password', '/api/v1/auth/password', [
          ['currentPassword', 'Contraseña actual', '', 'password'], ['newPassword', 'Nueva contraseña', '', 'password']], ['currentPassword', 'newPassword']),
      ],
    },
    {
      id: 'catalog', label: 'Catálogo', icon: '▦', actions: [
        action('products', 'Listar productos', 'GET /api/v1/products', '/api/v1/products', undefined, undefined, ['page', 'size', 'search', 'active'], { page: '0', size: '20' }),
        action('product-create', 'Crear producto', 'POST /api/v1/products', '/api/v1/products', [
          ['sku', 'SKU', 'DEMO-01'], ['name', 'Nombre', 'Producto demo'], ['unitPrice', 'Precio', '10.00'], ['version', 'Versión', '0']], ['sku', 'name', 'unitPrice', 'version']),
        action('product-get', 'Consultar producto', 'GET /api/v1/products/{id}', '/api/v1/products/{id}', [['id', 'ID', '1']]),
        action('product-update', 'Actualizar producto', 'PUT /api/v1/products/{id}', '/api/v1/products/{id}', [
          ['id', 'ID', '1'], ['sku', 'SKU', 'DEMO-01'], ['name', 'Nombre', 'Producto demo'], ['unitPrice', 'Precio', '10.00'], ['version', 'Versión', '0']], ['sku', 'name', 'unitPrice', 'version']),
        action('product-delete', 'Desactivar producto', 'DELETE /api/v1/products/{id}', '/api/v1/products/{id}', [['id', 'ID', '1']]),
        action('customers', 'Listar clientes', 'GET /api/v1/customers', '/api/v1/customers', undefined, undefined, ['page', 'size', 'search', 'active'], { page: '0', size: '20' }),
        action('customer-create', 'Crear cliente', 'POST /api/v1/customers', '/api/v1/customers', [
          ['name', 'Nombre', 'Cliente demo'], ['taxId', 'NIF', 'B12345678'], ['email', 'Email', 'demo@example.com'], ['address', 'Dirección', 'Madrid'], ['version', 'Versión', '0']], ['name', 'taxId', 'email', 'address', 'version']),
        action('customer-get', 'Consultar cliente', 'GET /api/v1/customers/{id}', '/api/v1/customers/{id}', [['id', 'ID', '1']]),
        action('customer-update', 'Actualizar cliente', 'PUT /api/v1/customers/{id}', '/api/v1/customers/{id}', [
          ['id', 'ID', '1'], ['name', 'Nombre', 'Cliente demo'], ['taxId', 'NIF', 'B12345678'], ['email', 'Email', 'demo@example.com'], ['address', 'Dirección', 'Madrid'], ['version', 'Versión', '0']], ['name', 'taxId', 'email', 'address', 'version']),
        action('customer-delete', 'Desactivar cliente', 'DELETE /api/v1/customers/{id}', '/api/v1/customers/{id}', [['id', 'ID', '1']]),
      ],
    },
    {
      id: 'inventory', label: 'Inventario', icon: '◈', actions: [
        action('balances', 'Ver balances', 'GET /api/v1/inventory', '/api/v1/inventory', undefined, undefined, ['productId', 'page', 'size'], { page: '0', size: '20' }),
        action('adjust', 'Ajustar existencias', 'POST /api/v1/inventory/{productId}/adjustments', '/api/v1/inventory/{productId}/adjustments', [
          ['productId', 'Producto', '1'], ['quantity', 'Cantidad', '1'], ['reason', 'Motivo', 'Ajuste de prueba', 'textarea']], ['quantity', 'reason']),
        action('movements', 'Ver movimientos', 'GET /api/v1/inventory/movements', '/api/v1/inventory/movements', undefined, undefined, ['productId', 'page', 'size'], { page: '0', size: '20' }),
        action('reservations', 'Ver reservas', 'GET /api/v1/inventory/reservations', '/api/v1/inventory/reservations', undefined, undefined, ['page', 'size'], { page: '0', size: '20' }),
      ],
    },
    {
      id: 'orders', label: 'Pedidos', icon: '□', actions: [
        action('orders', 'Listar pedidos', 'GET /api/v1/orders', '/api/v1/orders', undefined, undefined, ['customerId', 'status', 'page', 'size'], { page: '0', size: '20' }),
        action('order-create', 'Crear pedido', 'POST /api/v1/orders', '/api/v1/orders', [['customerId', 'Cliente', '1']], ['customerId']),
        action('order-get', 'Consultar pedido', 'GET /api/v1/orders/{id}', '/api/v1/orders/{id}', [['id', 'ID', '1']]),
        action('order-update', 'Cambiar cliente', 'PUT /api/v1/orders/{id}', '/api/v1/orders/{id}', [['id', 'Pedido', '1'], ['customerId', 'Cliente', '1']], ['customerId']),
        action('line-set', 'Añadir línea', 'PUT /api/v1/orders/{id}/lines/{productId}', '/api/v1/orders/{id}/lines/{productId}', [['id', 'Pedido', '1'], ['productId', 'Producto', '1'], ['quantity', 'Cantidad', '1']], ['quantity']),
        action('line-delete', 'Eliminar línea', 'DELETE /api/v1/orders/{id}/lines/{productId}', '/api/v1/orders/{id}/lines/{productId}', [['id', 'Pedido', '1'], ['productId', 'Producto', '1']]),
        ...(['confirm', 'cancel', 'complete'] as const).map((operation) => action(`order-${operation}`, `${operation[0].toUpperCase()}${operation.slice(1)} pedido`, `POST /api/v1/orders/{id}/${operation}`, `/api/v1/orders/{id}/${operation}`, [['id', 'Pedido', '1']])),
      ],
    },
    {
      id: 'admin', label: 'Informes y administración', icon: '◫', actions: [
        action('sales', 'Ventas por cliente', 'GET /api/v1/reports/sales', '/api/v1/reports/sales', undefined, undefined, ['from', 'to', 'page', 'size'], { page: '0', size: '20' }),
        action('low-stock', 'Stock bajo', 'GET /api/v1/reports/low-stock', '/api/v1/reports/low-stock', undefined, undefined, ['threshold', 'page', 'size'], { threshold: '10', page: '0', size: '20' }),
        action('audit', 'Auditoría', 'GET /api/v1/audit', '/api/v1/audit', undefined, undefined, ['page', 'size', 'entityType', 'entityId', 'requestId'], { page: '0', size: '20' }),
        action('users', 'Listar usuarios', 'GET /api/v1/users', '/api/v1/users', undefined, undefined, ['page', 'size', 'query', 'active'], { page: '0', size: '20' }),
        action('user-create', 'Crear usuario', 'POST /api/v1/users', '/api/v1/users', [['username', 'Usuario', 'demo-user'], ['password', 'Contraseña', '', 'password'], ['role', 'Rol', 'READONLY']], ['username', 'password', 'role']),
        action('user-update', 'Actualizar usuario', 'PUT /api/v1/users/{id}', '/api/v1/users/{id}', [['id', 'ID', '1'], ['role', 'Rol', 'READONLY'], ['active', 'Activo', 'true']], ['role', 'active']),
        action('user-delete', 'Desactivar usuario', 'DELETE /api/v1/users/{id}', '/api/v1/users/{id}', [['id', 'ID', '1']]),
      ],
    },
  ];
}

function action(id: string, title: string, method: string, path: string, fields?: FieldTuple[], body?: string[], query?: string[], defaults?: Record<string, string>): ActionDefinition {
    const values: Record<string, string> = { ...defaults };
    for (const field of fields ?? []) values[field[0]] = field[2] ?? '';
    for (const name of query ?? []) values[name] ??= '';
    return { id, title, method, path, fields: fields ?? (query ?? []).map((name) => [name, name, values[name]]), body, query, values };
  }
