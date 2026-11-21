import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CartApi } from './api';
import { AuthService } from './auth.service';
import { Cart } from './models';

/** Shared cart state: the header badge, product pages and the cart page all read from here. */
@Injectable({ providedIn: 'root' })
export class CartStore {
  private readonly api = inject(CartApi);
  private readonly auth = inject(AuthService);

  readonly cart = signal<Cart | null>(null);
  readonly count = computed(() => this.cart()?.itemCount ?? 0);

  constructor() {
    effect(() => {
      if (this.auth.isLoggedIn()) {
        void this.refresh();
      } else {
        this.cart.set(null);
      }
    });
  }

  async refresh(): Promise<Cart | null> {
    try {
      const cart = await firstValueFrom(this.api.get());
      this.cart.set(cart);
      return cart;
    } catch {
      return this.cart();
    }
  }

  quantityOf(productId: number): number {
    return this.cart()?.items.find((i) => i.productId === productId)?.quantity ?? 0;
  }

  async add(productId: number, quantity = 1): Promise<Cart> {
    return this.setQuantity(productId, this.quantityOf(productId) + quantity);
  }

  async setQuantity(productId: number, quantity: number): Promise<Cart> {
    const cart = await firstValueFrom(this.api.setQuantity(productId, quantity));
    this.cart.set(cart);
    return cart;
  }

  async remove(productId: number): Promise<Cart> {
    const cart = await firstValueFrom(this.api.remove(productId));
    this.cart.set(cart);
    return cart;
  }

  /** The server empties the cart when an order is placed. */
  clearLocal(): void {
    const cart = this.cart();
    if (cart) {
      this.cart.set({ ...cart, items: [], itemCount: 0, subtotal: 0, shippingFee: 0, total: 0, checkoutReady: false });
    }
  }
}
