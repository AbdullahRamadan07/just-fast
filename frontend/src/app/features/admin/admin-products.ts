import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AdminApi } from '../../core/api';
import { errorMessage } from '../../core/api-error';
import { AdminProduct } from '../../core/models';
import { StoreInfoService } from '../../core/store-info.service';
import { Pagination } from '../../shared/pagination';

@Component({
  selector: 'jf-admin-products',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, FormsModule, CurrencyPipe, Pagination],
  template: `
    <div class="row head">
      <h1>Products &amp; stock</h1>
      <span class="spacer"></span>
      <a class="btn" routerLink="/admin/products/new">Add product</a>
    </div>

    <form class="row filters" (ngSubmit)="search()">
      <input name="q" placeholder="Search by name or SKU" [(ngModel)]="query" />
      <button class="btn secondary" type="submit">Search</button>
    </form>

    @if (message(); as m) {
      <div class="alert" [class.error]="!m.ok" [class.success]="m.ok">{{ m.text }}</div>
    }

    <div class="card">
      @if (products.value(); as page) {
        <div class="table-wrap">
          <table class="table">
            <thead>
              <tr>
                <th>Product</th><th>Category</th><th>Price</th><th>Status</th>
                <th>On hand</th><th>Reserved</th><th>Available</th><th></th>
              </tr>
            </thead>
            <tbody>
              @for (p of page.items; track p.id) {
                <tr [class.archived]="p.status === 'ARCHIVED'">
                  <td><a [routerLink]="['/admin/products', p.id]"><strong>{{ p.name }}</strong></a><div class="muted small">{{ p.sku }}</div></td>
                  <td>{{ p.category.name }}</td>
                  <td>{{ p.price | currency: currency() }}</td>
                  <td>
                    <span class="badge" [class.green]="p.status === 'ACTIVE'">{{ p.status === 'ACTIVE' ? 'Active' : 'Archived' }}</span>
                  </td>
                  <td>
                    <form class="stock" (ngSubmit)="saveStock(p, stockInput.value)">
                      <input #stockInput type="number" min="0" [value]="p.onHand" [attr.aria-label]="'On-hand stock for ' + p.name" />
                      <button class="btn secondary small" type="submit" [disabled]="busyId() === p.id">Save</button>
                    </form>
                  </td>
                  <td>{{ p.reserved }}</td>
                  <td>
                    <span [class.low]="p.available <= 3">{{ p.available }}</span>
                  </td>
                  <td class="right">
                    @if (p.status === 'ACTIVE') {
                      <button class="btn danger small" [disabled]="busyId() === p.id" (click)="archive(p)">Archive</button>
                    } @else {
                      <button class="btn secondary small" [disabled]="busyId() === p.id" (click)="restore(p)">Restore</button>
                    }
                  </td>
                </tr>
              } @empty {
                <tr><td colspan="8" class="muted">No products found.</td></tr>
              }
            </tbody>
          </table>
        </div>
        <jf-pagination [page]="page.page" [totalPages]="page.totalPages" (pageChange)="page$.set($event)" />
      } @else {
        <p class="muted">Loading…</p>
      }
    </div>
  `,
  styles: `
    .head {
      margin-bottom: 12px;
    }
    .filters {
      margin-bottom: 16px;
      flex-wrap: nowrap;
    }
    .filters input {
      max-width: 360px;
    }
    .stock {
      display: flex;
      gap: 6px;
    }
    .stock input {
      width: 86px;
      min-height: 34px;
      padding: 4px 8px;
    }
    .archived td {
      opacity: 0.6;
    }
    .low {
      color: var(--danger);
      font-weight: 700;
    }
  `,
})
export class AdminProducts {
  private readonly api = inject(AdminApi);
  private readonly storeInfo = inject(StoreInfoService);
  protected readonly currency = computed(() => this.storeInfo.info().currency);

  protected query = '';
  private readonly q = signal('');
  protected readonly page$ = signal(0);
  protected readonly busyId = signal<number | null>(null);
  protected readonly message = signal<{ text: string; ok: boolean } | null>(null);

  protected readonly products = rxResource({
    params: () => ({ q: this.q(), page: this.page$(), size: 20 }),
    stream: ({ params }) => this.api.products(params),
  });

  protected search(): void {
    this.page$.set(0);
    this.q.set(this.query.trim());
  }

  protected async saveStock(product: AdminProduct, value: string): Promise<void> {
    const onHand = Number(value);
    if (!Number.isInteger(onHand) || onHand < 0) {
      this.message.set({ text: 'Stock must be a whole number of 0 or more.', ok: false });
      return;
    }
    await this.run(product, () => firstValueFrom(this.api.setStock(product.id, onHand)), `Stock for ${product.name} set to ${onHand}.`);
  }

  protected archive(product: AdminProduct): Promise<void> {
    return this.run(product, () => firstValueFrom(this.api.archiveProduct(product.id)), `${product.name} archived.`);
  }

  protected restore(product: AdminProduct): Promise<void> {
    return this.run(product, () => firstValueFrom(this.api.restoreProduct(product.id)), `${product.name} restored.`);
  }

  private async run(product: AdminProduct, action: () => Promise<unknown>, success: string): Promise<void> {
    this.busyId.set(product.id);
    this.message.set(null);
    try {
      await action();
      this.message.set({ text: success, ok: true });
      this.products.reload();
    } catch (e) {
      this.message.set({ text: errorMessage(e), ok: false });
    } finally {
      this.busyId.set(null);
    }
  }
}
