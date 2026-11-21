import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { OrdersApi } from '../../core/api';
import { errorMessage } from '../../core/api-error';
import { CartStore } from '../../core/cart.store';
import { OrderDetail } from '../../core/models';
import { OrderTimeline } from '../../shared/order-timeline';
import { StatusBadge } from '../../shared/status-badge';

@Component({
  selector: 'jf-order-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, CurrencyPipe, DatePipe, StatusBadge, OrderTimeline],
  template: `
    <section class="container page stack">
      <a routerLink="/account" class="small">← Your account</a>
      @if (order.error()) {
        <div class="card empty">Order not found.</div>
      } @else if (order.value(); as o) {
        <div class="row">
          <h1>Order {{ o.orderNumber }}</h1>
          <span class="spacer"></span>
          <jf-status-badge [status]="o.status" />
        </div>
        <p class="muted">Placed {{ o.createdAt | date: 'medium' }} · Payment: <jf-status-badge kind="payment" [status]="o.paymentStatus" /></p>

        @if (message()) {
          <div class="alert error">{{ message() }}</div>
        }

        @if (o.canResumePayment) {
          <div class="alert info row">
            <span>This order is awaiting payment. Items are reserved until {{ o.reservationExpiresAt | date: 'shortTime' }}.</span>
            <span class="spacer"></span>
            <button class="btn small" [disabled]="busy()" (click)="resumePayment(o)">Complete payment</button>
          </div>
        }

        <div class="card"><jf-order-timeline [order]="o" /></div>

        <div class="two-col">
          <div class="card">
            <h2>Items</h2>
            <div class="table-wrap">
              <table class="table">
                <thead><tr><th>Product</th><th>Price</th><th>Qty</th><th class="right">Total</th></tr></thead>
                <tbody>
                  @for (item of o.items; track item.productId) {
                    <tr>
                      <td>{{ item.productName }} <div class="muted small">{{ item.sku }}</div></td>
                      <td>{{ item.unitPrice | currency: o.currency }}</td>
                      <td>{{ item.quantity }}</td>
                      <td class="right">{{ item.lineTotal | currency: o.currency }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            @if (o.status === 'CANCELLED') {
              <button class="btn secondary" [disabled]="busy()" (click)="buyAgain(o)">Add these items to cart again</button>
            }
          </div>
          <aside class="stack">
            <div class="card summary">
              <div class="line"><span>Subtotal</span><span>{{ o.subtotal | currency: o.currency }}</span></div>
              <div class="line"><span>Shipping</span><span>{{ o.shippingFee | currency: o.currency }}</span></div>
              <div class="line total"><span>Total</span><span>{{ o.total | currency: o.currency }}</span></div>
            </div>
            <div class="card">
              <h3>Ship to</h3>
              <p class="small">
                {{ o.shippingAddress.fullName }}<br />
                {{ o.shippingAddress.line1 }}<br />
                @if (o.shippingAddress.line2) {
                  {{ o.shippingAddress.line2 }}<br />
                }
                {{ o.shippingAddress.city }}@if (o.shippingAddress.region) {, {{ o.shippingAddress.region }}}
                {{ o.shippingAddress.postalCode }}<br />
                {{ o.shippingAddress.country }}
              </p>
            </div>
          </aside>
        </div>
      } @else {
        <p class="muted">Loading…</p>
      }
    </section>
  `,
})
export class OrderPage {
  private readonly api = inject(OrdersApi);
  private readonly cart = inject(CartStore);
  private readonly router = inject(Router);

  readonly id = input.required<string>();
  protected readonly busy = signal(false);
  protected readonly message = signal<string | null>(null);

  protected readonly order = rxResource({
    params: () => Number(this.id()),
    stream: ({ params }) => this.api.myOrder(params),
  });

  protected async resumePayment(order: OrderDetail): Promise<void> {
    this.busy.set(true);
    this.message.set(null);
    try {
      const result = await firstValueFrom(this.api.resumePayment(order.id));
      if (result.checkoutUrl) {
        window.location.assign(result.checkoutUrl);
        return;
      }
      this.order.reload();
    } catch (e) {
      this.message.set(errorMessage(e));
      this.order.reload();
    } finally {
      this.busy.set(false);
    }
  }

  protected async buyAgain(order: OrderDetail): Promise<void> {
    this.busy.set(true);
    this.message.set(null);
    try {
      for (const item of order.items) {
        await this.cart.add(item.productId, item.quantity);
      }
      await this.router.navigate(['/cart']);
    } catch (e) {
      this.message.set(errorMessage(e));
    } finally {
      this.busy.set(false);
    }
  }
}
