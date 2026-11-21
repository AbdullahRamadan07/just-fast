import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { errorMessage } from '../../core/api-error';
import { CartStore } from '../../core/cart.store';
import { ProductImage } from '../../shared/product-image';

@Component({
  selector: 'jf-cart-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, RouterLink, ProductImage],
  template: `
    <section class="container page">
      <h1>Your cart</h1>
      @if (error()) {
        <div class="alert error">{{ error() }}</div>
      }

      @if (store.cart(); as cart) {
        @if (cart.items.length === 0) {
          <div class="card empty">
            <p>Your cart is empty.</p>
            <a class="btn" routerLink="/">Start shopping</a>
          </div>
        } @else {
          <div class="two-col">
            <div class="card">
              @for (line of cart.items; track line.productId) {
                <div class="cart-line">
                  <jf-product-image class="thumb" [imageUrl]="line.imageUrl" [name]="line.name" />
                  <div class="info">
                    <a [routerLink]="['/products', line.productId]" class="name">{{ line.name }}</a>
                    <span class="muted small">{{ line.unitPrice | currency: cart.currency }} each</span>
                    @if (!line.purchasable) {
                      <span class="badge red">
                        {{ line.availableQuantity === 0 ? 'Out of stock' : 'Only ' + line.availableQuantity + ' available' }}
                      </span>
                    }
                  </div>
                  <div class="qty">
                    <button class="btn secondary small" aria-label="Decrease quantity" [disabled]="busy()"
                            (click)="setQuantity(line.productId, line.quantity - 1)">−</button>
                    <span aria-live="polite">{{ line.quantity }}</span>
                    <button class="btn secondary small" aria-label="Increase quantity"
                            [disabled]="busy() || line.quantity >= line.availableQuantity"
                            (click)="setQuantity(line.productId, line.quantity + 1)">+</button>
                  </div>
                  <strong class="total">{{ line.lineTotal | currency: cart.currency }}</strong>
                  <button class="link-btn small" [disabled]="busy()" (click)="setQuantity(line.productId, 0)">Remove</button>
                </div>
              }
            </div>

            <aside class="card summary">
              <h2>Summary</h2>
              <div class="line"><span>Subtotal</span><span>{{ cart.subtotal | currency: cart.currency }}</span></div>
              <div class="line"><span>Shipping</span><span>{{ cart.shippingFee | currency: cart.currency }}</span></div>
              <div class="line total"><span>Total</span><span>{{ cart.total | currency: cart.currency }}</span></div>
              @if (!cart.checkoutReady) {
                <div class="alert warning small">Adjust the highlighted items before checking out.</div>
              }
              <a class="btn block" routerLink="/checkout" [class.disabled]="!cart.checkoutReady"
                 [attr.aria-disabled]="!cart.checkoutReady" (click)="!cart.checkoutReady && $event.preventDefault()">
                Proceed to checkout
              </a>
            </aside>
          </div>
        }
      } @else {
        <p class="muted">Loading your cart…</p>
      }
    </section>
  `,
  styles: `
    .cart-line {
      display: grid;
      grid-template-columns: 72px minmax(0, 1fr) auto auto auto;
      gap: 16px;
      align-items: center;
      padding: 14px 0;
      border-bottom: 1px solid var(--border);
    }
    .cart-line:last-child {
      border-bottom: 0;
    }
    .thumb {
      width: 72px;
    }
    .info {
      display: flex;
      flex-direction: column;
      gap: 2px;
      align-items: flex-start;
    }
    .name {
      font-weight: 700;
      color: var(--jf-navy);
    }
    .qty {
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .total {
      min-width: 80px;
      text-align: right;
    }
    a.disabled {
      opacity: 0.55;
      pointer-events: none;
    }
    @media (max-width: 640px) {
      .cart-line {
        grid-template-columns: 56px minmax(0, 1fr) auto;
      }
      .thumb {
        width: 56px;
      }
    }
  `,
})
export class CartPage implements OnInit {
  protected readonly store = inject(CartStore);
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    void this.store.refresh();
  }

  protected async setQuantity(productId: number, quantity: number): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      await this.store.setQuantity(productId, Math.max(quantity, 0));
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}
