import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import {
  Address,
  AdminProduct,
  Cart,
  Category,
  CheckoutResult,
  OrderDetail,
  OrderStatus,
  OrderSummary,
  Page,
  Product,
  StockLevel,
  StoreInfo,
} from './models';

function params(values: Record<string, string | number | null | undefined>): HttpParams {
  let result = new HttpParams();
  for (const [key, value] of Object.entries(values)) {
    if (value !== null && value !== undefined && value !== '') {
      result = result.set(key, String(value));
    }
  }
  return result;
}

export interface ProductQuery {
  q?: string;
  category?: string;
  sort?: string;
  page?: number;
  size?: number;
}

/** Public storefront endpoints. */
@Injectable({ providedIn: 'root' })
export class CatalogApi {
  private readonly http = inject(HttpClient);

  products(query: ProductQuery): Observable<Page<Product>> {
    return this.http.get<Page<Product>>('/api/products', { params: params({ ...query }) });
  }

  product(id: number): Observable<Product> {
    return this.http.get<Product>(`/api/products/${id}`);
  }

  categories(): Observable<Category[]> {
    return this.http.get<Category[]>('/api/categories');
  }

  storeInfo(): Observable<StoreInfo> {
    return this.http.get<StoreInfo>('/api/store');
  }
}

@Injectable({ providedIn: 'root' })
export class CartApi {
  private readonly http = inject(HttpClient);

  get(): Observable<Cart> {
    return this.http.get<Cart>('/api/cart');
  }

  setQuantity(productId: number, quantity: number): Observable<Cart> {
    return this.http.put<Cart>(`/api/cart/items/${productId}`, { quantity });
  }

  remove(productId: number): Observable<Cart> {
    return this.http.delete<Cart>(`/api/cart/items/${productId}`);
  }
}

@Injectable({ providedIn: 'root' })
export class AccountApi {
  private readonly http = inject(HttpClient);

  addresses(): Observable<Address[]> {
    return this.http.get<Address[]>('/api/account/addresses');
  }

  createAddress(address: Address): Observable<Address> {
    return this.http.post<Address>('/api/account/addresses', address);
  }

  updateAddress(id: number, address: Address): Observable<Address> {
    return this.http.put<Address>(`/api/account/addresses/${id}`, address);
  }

  deleteAddress(id: number): Observable<void> {
    return this.http.delete<void>(`/api/account/addresses/${id}`);
  }
}

@Injectable({ providedIn: 'root' })
export class OrdersApi {
  private readonly http = inject(HttpClient);

  myOrders(page = 0, size = 10): Observable<Page<OrderSummary>> {
    return this.http.get<Page<OrderSummary>>('/api/orders', { params: params({ page, size }) });
  }

  myOrder(id: number): Observable<OrderDetail> {
    return this.http.get<OrderDetail>(`/api/orders/${id}`);
  }

  checkout(addressId: number, idempotencyKey: string): Observable<CheckoutResult> {
    return this.http.post<CheckoutResult>('/api/checkout', { addressId, idempotencyKey });
  }

  resumePayment(orderId: number): Observable<CheckoutResult> {
    return this.http.post<CheckoutResult>(`/api/orders/${orderId}/payment-session`, {});
  }
}

export interface ProductForm {
  sku?: string;
  name: string;
  description?: string;
  price: number;
  imageUrl?: string;
  categoryId: number;
  initialStock?: number;
}

/** Admin dashboard endpoints (require the ADMIN role server-side). */
@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly http = inject(HttpClient);

  products(query: { q?: string; category?: string; page?: number; size?: number }): Observable<Page<AdminProduct>> {
    return this.http.get<Page<AdminProduct>>('/api/admin/products', { params: params({ ...query }) });
  }

  product(id: number): Observable<AdminProduct> {
    return this.http.get<AdminProduct>(`/api/admin/products/${id}`);
  }

  createProduct(form: ProductForm): Observable<AdminProduct> {
    return this.http.post<AdminProduct>('/api/admin/products', form);
  }

  updateProduct(id: number, form: ProductForm): Observable<AdminProduct> {
    return this.http.put<AdminProduct>(`/api/admin/products/${id}`, form);
  }

  archiveProduct(id: number): Observable<AdminProduct> {
    return this.http.post<AdminProduct>(`/api/admin/products/${id}/archive`, {});
  }

  restoreProduct(id: number): Observable<AdminProduct> {
    return this.http.post<AdminProduct>(`/api/admin/products/${id}/restore`, {});
  }

  setStock(productId: number, onHand: number): Observable<StockLevel> {
    return this.http.put<StockLevel>(`/api/admin/inventory/${productId}`, { onHand });
  }

  categories(): Observable<Category[]> {
    return this.http.get<Category[]>('/api/admin/categories');
  }

  createCategory(name: string): Observable<Category> {
    return this.http.post<Category>('/api/admin/categories', { name });
  }

  renameCategory(id: number, name: string): Observable<Category> {
    return this.http.put<Category>(`/api/admin/categories/${id}`, { name });
  }

  deleteCategory(id: number): Observable<void> {
    return this.http.delete<void>(`/api/admin/categories/${id}`);
  }

  orders(query: { status?: OrderStatus | ''; page?: number; size?: number }): Observable<Page<OrderSummary>> {
    return this.http.get<Page<OrderSummary>>('/api/admin/orders', { params: params({ ...query }) });
  }

  orderStats(): Observable<Record<OrderStatus, number>> {
    return this.http.get<Record<OrderStatus, number>>('/api/admin/orders/stats');
  }

  order(id: number): Observable<OrderDetail> {
    return this.http.get<OrderDetail>(`/api/admin/orders/${id}`);
  }

  ship(id: number): Observable<OrderDetail> {
    return this.http.post<OrderDetail>(`/api/admin/orders/${id}/ship`, {});
  }

  deliver(id: number): Observable<OrderDetail> {
    return this.http.post<OrderDetail>(`/api/admin/orders/${id}/deliver`, {});
  }
}
