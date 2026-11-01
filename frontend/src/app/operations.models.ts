export interface Product { id: number; sku: string; name: string; unitPrice: number; active: boolean; version: number; }
export interface Customer { id: number; name: string; taxId: string; email: string; address: string; active: boolean; version: number; }
export interface OrderLine { id: number; productId: number; sku: string; productName: string; quantity: number; unitPrice: number; lineTotal: number; }
export type OrderStatus = 'DRAFT' | 'CONFIRMED' | 'COMPLETED' | 'CANCELLED';
export interface Order { id: number; customerId: number; customerName: string; status: OrderStatus; total: number; version: number; shippingReference: string | null; shippingCost: number | null; createdAt: string; updatedAt: string; lines: OrderLine[]; }
export interface Balance { productId: number; sku: string; productName: string; onHand: number; reserved: number; available: number; }
export interface Movement { id: number; productId: number; orderId: number | null; kind: string; quantity: number; reason: string; createdAt: string; }
export interface Reservation { orderId: number; productId: number; sku: string; quantity: number; }
export interface Sale { customerId: number; customerName: string; completedOrders: number; netSales: number; shippingCost: number; }
export interface Audit { id: number; actor: string; action: string; entityType: string; entityId: string; details: string; requestId: string; createdAt: string; }
export interface User { id: number; username: string; role: string; active: boolean; createdAt: string; }
