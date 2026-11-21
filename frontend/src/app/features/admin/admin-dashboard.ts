import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { AdminApi } from '../../core/api';
import { OrderStatus } from '../../core/models';

const TILES: { status: OrderStatus; label: string; hint: string }[] = [
  { status: 'PAID', label: 'Ready to ship', hint: 'Paid orders awaiting shipment' },
  { status: 'SHIPPED', label: 'In transit', hint: 'Shipped, not yet delivered' },
  { status: 'PENDING_PAYMENT', label: 'Awaiting payment', hint: 'Stock reserved while customers pay' },
  { status: 'PAYMENT_REVIEW', label: 'Needs review', hint: 'Late or mismatched payments' },
  { status: 'DELIVERED', label: 'Delivered', hint: 'Completed orders' },
  { status: 'CANCELLED', label: 'Cancelled', hint: 'Failed or expired payments' },
];

@Component({
  selector: 'jf-admin-dashboard',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <h1>Overview</h1>
    <div class="tiles">
      @for (tile of tiles; track tile.status) {
        <a class="card tile" [routerLink]="['/admin/orders']" [queryParams]="{ status: tile.status }">
          <span class="value">{{ stats.value()?.[tile.status] ?? '—' }}</span>
          <strong>{{ tile.label }}</strong>
          <span class="muted small">{{ tile.hint }}</span>
        </a>
      }
    </div>
    <div class="row actions">
      <a class="btn" routerLink="/admin/products/new">Add product</a>
      <a class="btn secondary" routerLink="/admin/orders" [queryParams]="{ status: 'PAID' }">Ship paid orders</a>
    </div>
  `,
  styles: `
    .tiles {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
      gap: 16px;
      margin: 16px 0 24px;
    }
    .tile {
      display: flex;
      flex-direction: column;
      gap: 4px;
      color: var(--text);
    }
    .tile:hover {
      text-decoration: none;
      border-color: var(--jf-blue);
    }
    .value {
      font-size: 2rem;
      font-weight: 800;
      color: var(--jf-navy);
    }
  `,
})
export class AdminDashboard {
  private readonly api = inject(AdminApi);
  protected readonly tiles = TILES;
  protected readonly stats = rxResource({ stream: () => this.api.orderStats() });
}
