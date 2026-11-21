export type Role = 'CUSTOMER' | 'ADMIN';

export interface CurrentUser {
  id: number;
  email: string;
  fullName: string;
  role: Role;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export interface ApiError {
  status: number;
  code: string;
  message: string;
  fieldErrors?: Record<string, string>;
}

export interface StoreInfo {
  currency: string;
  shippingCountry: string;
  flatShippingFee: number;
}

export interface Category {
  id: number;
  name: string;
  slug: string;
}

export interface Product {
  id: number;
  sku: string;
  name: string;
  description?: string;
  price: number;
  currency: string;
  imageUrl?: string;
  category: Category;
  availableQuantity: number;
  inStock: boolean;
}

export interface CartLine {
  productId: number;
  sku: string;
  name: string;
  imageUrl?: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
  availableQuantity: number;
  purchasable: boolean;
}

export interface Cart {
  items: CartLine[];
  currency: string;
  subtotal: number;
  shippingFee: number;
  total: number;
  itemCount: number;
  checkoutReady: boolean;
}

export interface Address {
  id?: number;
  fullName: string;
  line1: string;
  line2?: string | null;
  city: string;
  region?: string | null;
  postalCode: string;
  country: string;
  phone?: string | null;
}

export type OrderStatus =
  | 'PENDING_PAYMENT'
  | 'PAID'
  | 'SHIPPED'
  | 'DELIVERED'
  | 'CANCELLED'
  | 'PAYMENT_REVIEW';

export type PaymentStatus = 'CREATED' | 'PENDING' | 'SUCCEEDED' | 'FAILED' | 'EXPIRED';

export interface OrderSummary {
  id: number;
  orderNumber: string;
  status: OrderStatus;
  paymentStatus?: PaymentStatus;
  total: number;
  currency: string;
  itemCount: number;
  createdAt: string;
}

export interface OrderLine {
  productId: number;
  sku: string;
  productName: string;
  unitPrice: number;
  quantity: number;
  lineTotal: number;
}

export interface OrderDetail {
  id: number;
  orderNumber: string;
  customerId: number;
  status: OrderStatus;
  paymentStatus?: PaymentStatus;
  canResumePayment: boolean;
  currency: string;
  subtotal: number;
  shippingFee: number;
  total: number;
  shippingAddress: Address;
  items: OrderLine[];
  createdAt: string;
  reservationExpiresAt: string;
  paidAt?: string;
  shippedAt?: string;
  deliveredAt?: string;
  cancelledAt?: string;
}

export interface CheckoutResult {
  orderId: number;
  orderNumber: string;
  status: OrderStatus;
  checkoutUrl?: string;
}

export type ProductStatus = 'ACTIVE' | 'ARCHIVED';

export interface AdminProduct {
  id: number;
  sku: string;
  name: string;
  description?: string;
  price: number;
  imageUrl?: string;
  category: Category;
  status: ProductStatus;
  onHand: number;
  reserved: number;
  available: number;
}

export interface StockLevel {
  onHand: number;
  reserved: number;
  available: number;
}
