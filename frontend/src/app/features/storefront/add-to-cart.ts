import { Injectable, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { errorMessage } from '../../core/api-error';
import { AuthService } from '../../core/auth.service';
import { CartStore } from '../../core/cart.store';

/** Shared "add to cart" behaviour: sends guests to sign in first, reports stock errors. */
@Injectable({ providedIn: 'root' })
export class AddToCart {
  private readonly auth = inject(AuthService);
  private readonly cart = inject(CartStore);
  private readonly router = inject(Router);

  readonly pendingProductId = signal<number | null>(null);
  readonly message = signal<{ productId: number; text: string; ok: boolean } | null>(null);

  async add(productId: number, quantity = 1): Promise<void> {
    if (!this.auth.isLoggedIn()) {
      await this.router.navigate(['/account/login'], { queryParams: { returnUrl: this.router.url } });
      return;
    }
    this.pendingProductId.set(productId);
    try {
      await this.cart.add(productId, quantity);
      this.message.set({ productId, text: 'Added to cart', ok: true });
    } catch (error) {
      this.message.set({ productId, text: errorMessage(error), ok: false });
    } finally {
      this.pendingProductId.set(null);
    }
  }
}
