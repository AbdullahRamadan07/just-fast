import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { OrdersApi } from '../../core/api';
import { errorMessage } from '../../core/api-error';
import { CartStore } from '../../core/cart.store';
import { OrderDetail } from '../../core/models';
import { Logo } from '../../shared/logo';

const POLL_INTERVAL_MS = 2000;
const MAX_POLLS = 15;

/**
 * Landing page after the payment provider redirects back. The redirect itself proves nothing, so the page reads the
 * authoritative order status from the API (updated by the signed webhook) and polls briefly while it is pending.
 */
@Component({
  selector: 'jf-checkout-return',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, CurrencyPipe, DatePipe, Logo],
  template: `
    <section class="container page narrow">
      <div class="card stack center">
        <jf-logo [height]="40" />

        @if (error()) {
          <div class="alert error">{{ error() }}</div>
        } @else if (order(); as o) {
          @switch (o.status) {
            @case ('PAID') {
              <div class="icon ok" aria-hidden="true">✓</div>
              <h1>Payment confirmed</h1>
              <p>Thanks! Order <strong>{{ o.orderNumber }}</strong> is paid and being prepared for shipping.</p>
            }
            @case ('SHIPPED') {
              <h1>Order shipped</h1>
              <p>Order <strong>{{ o.orderNumber }}</strong> is on its way.</p>
            }
            @case ('DELIVERED') {
              <h1>Order delivered</h1>
            }
            @case ('PENDING_PAYMENT') {
              @if (cancelled() || pollsExhausted()) {
                <h1>Payment not completed</h1>
                <p>
                  Your items are reserved until {{ o.reservationExpiresAt | date: 'shortTime' }}.
                  You can finish paying from your order page.
                </p>
              } @else {
                <div class="spinner" aria-hidden="true"></div>
                <h1>Confirming your payment…</h1>
                <p class="muted">This usually takes a few seconds.</p>
              }
            }
            @case ('CANCELLED') {
              <div class="icon err" aria-hidden="true">!</div>
              <h1>Payment unsuccessful</h1>
              <p>The payment for order <strong>{{ o.orderNumber }}</strong> failed or expired, so the order was cancelled and nothing was charged.</p>
            }
            @case ('PAYMENT_REVIEW') {
              <h1>We're reviewing your payment</h1>
              <p>We received a payment for order <strong>{{ o.orderNumber }}</strong> that needs a quick manual check. We'll be in touch.</p>
            }
          }
          <p class="muted">Total {{ o.total | currency: o.currency }}</p>
          <div class="row actions">
            <a class="btn" [routerLink]="['/account/orders', o.id]">View order</a>
            <a class="btn secondary" routerLink="/">Continue shopping</a>
          </div>
        } @else {
          <p class="muted">Loading your order…</p>
        }
      </div>
    </section>
  `,
  styles: `
    .narrow {
      max-width: 620px;
    }
    .center {
      align-items: center;
      text-align: center;
    }
    .actions {
      justify-content: center;
    }
    .icon {
      width: 56px;
      height: 56px;
      border-radius: 50%;
      display: grid;
      place-items: center;
      font-size: 1.6rem;
      font-weight: 800;
      color: #fff;
    }
    .icon.ok {
      background: var(--success);
    }
    .icon.err {
      background: var(--danger);
    }
    .spinner {
      width: 40px;
      height: 40px;
      border-radius: 50%;
      border: 4px solid var(--jf-blue-50);
      border-top-color: var(--jf-blue);
      animation: spin 0.9s linear infinite;
    }
    @keyframes spin {
      to {
        transform: rotate(360deg);
      }
    }
  `,
})
export class CheckoutReturn implements OnInit {
  private readonly api = inject(OrdersApi);
  private readonly cartStore = inject(CartStore);
  private readonly destroyRef = inject(DestroyRef);

  readonly orderId = input.required<string>();
  readonly cancelled = input(false, { transform: (v: string | boolean | undefined) => v === true || v === 'true' });

  protected readonly order = signal<OrderDetail | null>(null);
  protected readonly error = signal<string | null>(null);
  protected readonly pollsExhausted = signal(false);

  private timer?: ReturnType<typeof setTimeout>;

  ngOnInit(): void {
    this.destroyRef.onDestroy(() => clearTimeout(this.timer));
    void this.cartStore.refresh();
    void this.poll(0);
  }

  private async poll(attempt: number): Promise<void> {
    try {
      const order = await firstValueFrom(this.api.myOrder(Number(this.orderId())));
      this.order.set(order);
      const waiting = order.status === 'PENDING_PAYMENT' && !this.cancelled();
      if (waiting && attempt < MAX_POLLS) {
        this.timer = setTimeout(() => void this.poll(attempt + 1), POLL_INTERVAL_MS);
      } else if (waiting) {
        this.pollsExhausted.set(true);
      }
    } catch (e) {
      this.error.set(errorMessage(e, 'We could not load this order.'));
    }
  }
}
