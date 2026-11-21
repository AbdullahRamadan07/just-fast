import { CurrencyPipe, DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AccountApi, OrdersApi } from '../../core/api';
import { errorMessage } from '../../core/api-error';
import { AuthService } from '../../core/auth.service';
import { Address } from '../../core/models';
import { AddressForm } from '../../shared/address-form';
import { Pagination } from '../../shared/pagination';
import { StatusBadge } from '../../shared/status-badge';

@Component({
  selector: 'jf-account-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, CurrencyPipe, DatePipe, StatusBadge, Pagination, AddressForm],
  template: `
    <section class="container page stack">
      <div>
        <h1>Hello, {{ auth.user()?.fullName }}</h1>
        <p class="muted">{{ auth.user()?.email }}</p>
      </div>

      <div class="card">
        <h2>Your orders</h2>
        @if (orders.value(); as page) {
          @if (page.items.length === 0) {
            <p class="muted">No orders yet. <a routerLink="/">Start shopping</a></p>
          } @else {
            <div class="table-wrap">
              <table class="table">
                <thead>
                  <tr><th>Order</th><th>Placed</th><th>Items</th><th>Total</th><th>Status</th><th></th></tr>
                </thead>
                <tbody>
                  @for (o of page.items; track o.id) {
                    <tr>
                      <td><strong>{{ o.orderNumber }}</strong></td>
                      <td>{{ o.createdAt | date: 'medium' }}</td>
                      <td>{{ o.itemCount }}</td>
                      <td>{{ o.total | currency: o.currency }}</td>
                      <td><jf-status-badge [status]="o.status" /></td>
                      <td class="right"><a [routerLink]="['/account/orders', o.id]">Track</a></td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            <jf-pagination [page]="page.page" [totalPages]="page.totalPages" (pageChange)="ordersPage.set($event)" />
          }
        } @else if (orders.error()) {
          <div class="alert error">Could not load your orders.</div>
        } @else {
          <p class="muted">Loading…</p>
        }
      </div>

      <div class="card stack">
        <div class="row">
          <h2>Shipping addresses</h2>
          <span class="spacer"></span>
          @if (!editing()) {
            <button class="btn secondary small" (click)="startEdit(null)">Add address</button>
          }
        </div>
        @if (addressError()) {
          <div class="alert error">{{ addressError() }}</div>
        }
        @if (editing(); as e) {
          <jf-address-form
            [value]="e.address"
            [busy]="saving()"
            [cancellable]="true"
            (saved)="save($event)"
            (cancelled)="editing.set(null)"
          />
        }
        @for (a of addresses.value() ?? []; track a.id) {
          <div class="address">
            <div>
              <strong>{{ a.fullName }}</strong><br />
              <span class="muted small">
                {{ a.line1 }}@if (a.line2) {, {{ a.line2 }}}, {{ a.city }}@if (a.region) {, {{ a.region }}}
                {{ a.postalCode }} · {{ a.country }}
              </span>
            </div>
            <div class="row">
              <button class="btn secondary small" (click)="startEdit(a)">Edit</button>
              <button class="btn danger small" (click)="remove(a)">Delete</button>
            </div>
          </div>
        } @empty {
          @if (!editing()) {
            <p class="muted">No saved addresses yet.</p>
          }
        }
      </div>
    </section>
  `,
  styles: `
    .address {
      display: flex;
      justify-content: space-between;
      align-items: center;
      gap: 12px;
      padding: 12px 0;
      border-top: 1px solid var(--border);
      flex-wrap: wrap;
    }
  `,
})
export class AccountPage {
  protected readonly auth = inject(AuthService);
  private readonly ordersApi = inject(OrdersApi);
  private readonly accountApi = inject(AccountApi);

  protected readonly ordersPage = signal(0);
  protected readonly orders = rxResource({
    params: () => this.ordersPage(),
    stream: ({ params }) => this.ordersApi.myOrders(params, 10),
  });
  protected readonly addresses = rxResource({ stream: () => this.accountApi.addresses() });

  protected readonly editing = signal<{ address: Address | null } | null>(null);
  protected readonly saving = signal(false);
  protected readonly addressError = signal<string | null>(null);

  protected startEdit(address: Address | null): void {
    this.addressError.set(null);
    this.editing.set({ address });
  }

  protected async save(value: Address): Promise<void> {
    const current = this.editing()?.address;
    this.saving.set(true);
    this.addressError.set(null);
    try {
      await firstValueFrom(
        current?.id ? this.accountApi.updateAddress(current.id, value) : this.accountApi.createAddress(value),
      );
      this.editing.set(null);
      this.addresses.reload();
    } catch (e) {
      this.addressError.set(errorMessage(e));
    } finally {
      this.saving.set(false);
    }
  }

  protected async remove(address: Address): Promise<void> {
    if (!address.id || !confirm('Delete this address?')) return;
    try {
      await firstValueFrom(this.accountApi.deleteAddress(address.id));
      this.addresses.reload();
    } catch (e) {
      this.addressError.set(errorMessage(e));
    }
  }
}
