import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AccountApi, OrdersApi } from '../../core/api';
import { errorCode, errorMessage } from '../../core/api-error';
import { CartStore } from '../../core/cart.store';
import { Address } from '../../core/models';
import { AddressForm } from '../../shared/address-form';
import { Logo } from '../../shared/logo';

@Component({
  selector: 'jf-checkout-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, RouterLink, AddressForm, Logo],
  template: `
    <section class="container page">
      <div class="checkout-head">
        <jf-logo [height]="44" />
        <span class="badge blue">Secure checkout</span>
      </div>

      @if (error()) {
        <div class="alert error">
          {{ error() }}
          @if (needsCartReview()) {
            <a routerLink="/cart">Review your cart</a>
          }
        </div>
      }

      @if (cart.cart(); as c) {
        @if (c.items.length === 0) {
          <div class="card empty">
            <p>Your cart is empty.</p>
            <a class="btn" routerLink="/">Continue shopping</a>
          </div>
        } @else {
          <div class="two-col">
            <div class="stack">
              <div class="card stack">
                <h2>1. Shipping address</h2>
                @if (addresses().length > 0 && !addingAddress()) {
                  <div class="addresses" role="radiogroup" aria-label="Shipping address">
                    @for (a of addresses(); track a.id) {
                      <label class="address" [class.on]="selectedId() === a.id">
                        <input type="radio" name="address" [checked]="selectedId() === a.id" (change)="selectedId.set(a.id!)" />
                        <span>
                          <strong>{{ a.fullName }}</strong><br />
                          {{ a.line1 }}@if (a.line2) {, {{ a.line2 }}}<br />
                          {{ a.city }}@if (a.region) {, {{ a.region }}} {{ a.postalCode }} · {{ a.country }}
                        </span>
                      </label>
                    }
                  </div>
                  <button class="link-btn" (click)="addingAddress.set(true)">+ Use a new address</button>
                } @else {
                  <jf-address-form
                    submitLabel="Save and use this address"
                    [busy]="savingAddress()"
                    [cancellable]="addresses().length > 0"
                    (saved)="saveAddress($event)"
                    (cancelled)="addingAddress.set(false)"
                  />
                }
              </div>

              <div class="card stack">
                <h2>2. Review items</h2>
                @for (line of c.items; track line.productId) {
                  <div class="item">
                    <span>{{ line.quantity }} × {{ line.name }}</span>
                    <span>{{ line.lineTotal | currency: c.currency }}</span>
                  </div>
                }
              </div>
            </div>

            <aside class="card summary">
              <h2>3. Pay</h2>
              <div class="line"><span>Subtotal</span><span>{{ c.subtotal | currency: c.currency }}</span></div>
              <div class="line"><span>Shipping</span><span>{{ c.shippingFee | currency: c.currency }}</span></div>
              <div class="line total"><span>Total</span><span>{{ c.total | currency: c.currency }}</span></div>
              <button class="btn block" [disabled]="!canPay(c.checkoutReady)" (click)="pay()">
                {{ paying() ? 'Redirecting to payment…' : 'Pay ' + (c.total | currency: c.currency) }}
              </button>
              <p class="muted small">
                You'll be redirected to our payment provider to pay securely. Card details are never shared with Just Fast.
                Items are reserved for you while you pay.
              </p>
            </aside>
          </div>
        }
      } @else {
        <p class="muted">Loading…</p>
      }
    </section>
  `,
  styles: `
    .checkout-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      margin-bottom: 24px;
      padding-bottom: 16px;
      border-bottom: 1px solid var(--border);
    }
    .addresses {
      display: grid;
      gap: 10px;
    }
    .address {
      display: flex;
      gap: 12px;
      align-items: flex-start;
      padding: 12px 14px;
      border: 1px solid var(--border);
      border-radius: var(--radius-sm);
      cursor: pointer;
      font-size: 0.95rem;
    }
    .address.on {
      border-color: var(--jf-blue);
      background: var(--jf-blue-50);
    }
    .address input {
      width: auto;
      min-height: 0;
      margin-top: 4px;
    }
    .item {
      display: flex;
      justify-content: space-between;
      gap: 12px;
    }
  `,
})
export class CheckoutPage implements OnInit {
  protected readonly cart = inject(CartStore);
  private readonly accountApi = inject(AccountApi);
  private readonly ordersApi = inject(OrdersApi);
  private readonly router = inject(Router);

  protected readonly addresses = signal<Address[]>([]);
  protected readonly selectedId = signal<number | null>(null);
  protected readonly addingAddress = signal(false);
  protected readonly savingAddress = signal(false);
  protected readonly paying = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly needsCartReview = signal(false);

  /** One key per checkout attempt: retries (double clicks, network errors) can never create a second order. */
  private readonly idempotencyKey = crypto.randomUUID();

  async ngOnInit(): Promise<void> {
    void this.cart.refresh();
    try {
      const list = await firstValueFrom(this.accountApi.addresses());
      this.addresses.set(list);
      this.selectedId.set(list[0]?.id ?? null);
      this.addingAddress.set(list.length === 0);
    } catch (e) {
      this.error.set(errorMessage(e));
    }
  }

  protected canPay(checkoutReady: boolean): boolean {
    return checkoutReady && this.selectedId() !== null && !this.addingAddress() && !this.paying();
  }

  protected async saveAddress(address: Address): Promise<void> {
    this.savingAddress.set(true);
    this.error.set(null);
    try {
      const saved = await firstValueFrom(this.accountApi.createAddress(address));
      this.addresses.update((list) => [saved, ...list]);
      this.selectedId.set(saved.id!);
      this.addingAddress.set(false);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.savingAddress.set(false);
    }
  }

  protected async pay(): Promise<void> {
    const addressId = this.selectedId();
    if (addressId === null) return;
    this.paying.set(true);
    this.error.set(null);
    this.needsCartReview.set(false);
    try {
      const result = await firstValueFrom(this.ordersApi.checkout(addressId, this.idempotencyKey));
      this.cart.clearLocal();
      if (result.checkoutUrl) {
        window.location.assign(result.checkoutUrl);
        return;
      }
      await this.router.navigate(['/checkout/return'], { queryParams: { orderId: result.orderId } });
    } catch (e) {
      const code = errorCode(e);
      this.needsCartReview.set(code === 'INSUFFICIENT_STOCK' || code === 'PRODUCT_UNAVAILABLE');
      this.error.set(errorMessage(e));
      void this.cart.refresh();
      this.paying.set(false);
    }
  }
}
