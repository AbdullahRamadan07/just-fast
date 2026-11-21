import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { AdminApi } from '../../core/api';
import { OrderStatus } from '../../core/models';
import { Pagination } from '../../shared/pagination';
import { StatusBadge } from '../../shared/status-badge';

const FILTERS: { value: OrderStatus | ''; label: string }[] = [
  { value: '', label: 'All' },
  { value: 'PAID', label: 'Ready to ship' },
  { value: 'SHIPPED', label: 'Shipped' },
  { value: 'DELIVERED', label: 'Delivered' },
  { value: 'PENDING_PAYMENT', label: 'Awaiting payment' },
  { value: 'PAYMENT_REVIEW', label: 'Needs review' },
  { value: 'CANCELLED', label: 'Cancelled' },
];

@Component({
  selector: 'jf-admin-orders',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, CurrencyPipe, DatePipe, StatusBadge, Pagination],
  template: `
    <h1>Orders</h1>
    <div class="row filters">
      @for (f of filters; track f.value) {
        <button class="btn small" [class.secondary]="(status() ?? '') !== f.value" (click)="go({ status: f.value || null })">
          {{ f.label }}
        </button>
      }
    </div>
    <div class="card">
      @if (orders.value(); as page) {
        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr><th>Order</th><th>Placed</th><th>Items</th><th>Total</th><th>Order status</th><th>Payment</th><th></th></tr>
            </thead>
            <tbody>
              @for (o of page.items; track o.id) {
                <tr>
                  <td><strong>{{ o.orderNumber }}</strong></td>
                  <td>{{ o.createdAt | date: 'short' }}</td>
                  <td>{{ o.itemCount }}</td>
                  <td>{{ o.total | currency: o.currency }}</td>
                  <td><jf-status-badge [status]="o.status" /></td>
                  <td><jf-status-badge kind="payment" [status]="o.paymentStatus" /></td>
                  <td class="right"><a [routerLink]="['/admin/orders', o.id]">Open</a></td>
                </tr>
              } @empty {
                <tr><td colspan="7" class="muted">No orders.</td></tr>
              }
            </tbody>
          </table>
        </div>
        <jf-pagination [page]="page.page" [totalPages]="page.totalPages" (pageChange)="go({ page: $event })" />
      } @else {
        <p class="muted">Loading…</p>
      }
    </div>
  `,
  styles: `
    .filters {
      margin-bottom: 16px;
      gap: 8px;
    }
  `,
})
export class AdminOrders {
  private readonly api = inject(AdminApi);
  private readonly router = inject(Router);
  protected readonly filters = FILTERS;

  readonly status = input<OrderStatus>();
  readonly page = input<string>();
  private readonly pageIndex = computed(() => Math.max(Number(this.page() ?? 0) || 0, 0));

  protected readonly orders = rxResource({
    params: () => ({ status: this.status() ?? ('' as const), page: this.pageIndex(), size: 20 }),
    stream: ({ params }) => this.api.orders(params),
  });

  protected go(change: { status?: string | null; page?: number }): void {
    void this.router.navigate([], {
      queryParams: { ...change, page: change.page ?? null },
      queryParamsHandling: 'merge',
    });
  }
}
