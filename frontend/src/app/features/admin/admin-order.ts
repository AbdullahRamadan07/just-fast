import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Observable, firstValueFrom } from 'rxjs';
import { AdminApi } from '../../core/api';
import { errorMessage } from '../../core/api-error';
import { OrderDetail } from '../../core/models';
import { OrderTimeline } from '../../shared/order-timeline';
import { StatusBadge } from '../../shared/status-badge';

@Component({
  selector: 'jf-admin-order',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, CurrencyPipe, DatePipe, StatusBadge, OrderTimeline],
  template: `
    <a routerLink="/admin/orders" class="small">← Orders</a>
    @if (order.value(); as o) {
      <div class="row">
        <h1>Order {{ o.orderNumber }}</h1>
        <span class="spacer"></span>
        <jf-status-badge [status]="o.status" />
        <jf-status-badge kind="payment" [status]="o.paymentStatus" />
      </div>
      <p class="muted">Placed {{ o.createdAt | date: 'medium' }} · Customer #{{ o.customerId }}</p>

      @if (error()) {
        <div class="alert error">{{ error() }}</div>
      }

      <div class="card stack">
        <jf-order-timeline [order]="o" />
        <div class="row">
          @if (o.status === 'PAID') {
            <button class="btn" [disabled]="busy()" (click)="act(api.ship(o.id))">Mark as shipped</button>
          }
          @if (o.status === 'SHIPPED') {
            <button class="btn" [disabled]="busy()" (click)="act(api.deliver(o.id))">Mark as delivered</button>
          }
          @if (o.status === 'PENDING_PAYMENT') {
            <span class="muted small">Waiting for payment. Unpaid orders cannot be shipped.</span>
          }
          @if (o.status === 'PAYMENT_REVIEW') {
            <span class="alert warning small">
              A payment arrived that could not be applied automatically (late or mismatched). Reconcile it in the
              payment provider's dashboard (refund or re-stock) before any fulfillment.
            </span>
          }
        </div>
      </div>

      <div class="two-col" style="margin-top: 20px">
        <div class="card">
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
        <aside class="stack">
          <div class="card summary">
            <div class="line"><span>Subtotal</span><span>{{ o.subtotal | currency: o.currency }}</span></div>
            <div class="line"><span>Shipping</span><span>{{ o.shippingFee | currency: o.currency }}</span></div>
            <div class="line total"><span>Total</span><span>{{ o.total | currency: o.currency }}</span></div>
          </div>
          <div class="card small">
            <h3>Ship to</h3>
            {{ o.shippingAddress.fullName }}<br />
            {{ o.shippingAddress.line1 }}<br />
            @if (o.shippingAddress.line2) {
              {{ o.shippingAddress.line2 }}<br />
            }
            {{ o.shippingAddress.city }}@if (o.shippingAddress.region) {, {{ o.shippingAddress.region }}}
            {{ o.shippingAddress.postalCode }} · {{ o.shippingAddress.country }}
            @if (o.shippingAddress.phone) {
              <br />{{ o.shippingAddress.phone }}
            }
          </div>
        </aside>
      </div>
    } @else if (order.error()) {
      <div class="card empty">Order not found.</div>
    } @else {
      <p class="muted">Loading…</p>
    }
  `,
})
export class AdminOrder {
  protected readonly api = inject(AdminApi);

  readonly id = input.required<string>();
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly order = rxResource({
    params: () => Number(this.id()),
    stream: ({ params }) => this.api.order(params),
  });

  protected async act(request: Observable<OrderDetail>): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    try {
      this.order.set(await firstValueFrom(request));
    } catch (e) {
      this.error.set(errorMessage(e));
      this.order.reload();
    } finally {
      this.busy.set(false);
    }
  }
}
