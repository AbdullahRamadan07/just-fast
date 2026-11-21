import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { CatalogApi } from '../../core/api';
import { ProductImage } from '../../shared/product-image';
import { AddToCart } from './add-to-cart';

@Component({
  selector: 'jf-product-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [CurrencyPipe, RouterLink, ProductImage],
  template: `
    <section class="container page">
      <a routerLink="/" class="small">← Back to products</a>

      @if (product.error()) {
        <div class="empty card">This product is not available.</div>
      } @else if (product.value(); as p) {
        <div class="layout">
          <jf-product-image [imageUrl]="p.imageUrl" [name]="p.name" />
          <div class="stack">
            <span class="muted">{{ p.category.name }} · SKU {{ p.sku }}</span>
            <h1>{{ p.name }}</h1>
            <div class="price">{{ p.price | currency: p.currency }}</div>
            @if (p.inStock) {
              <span class="badge green">{{ p.availableQuantity }} available</span>
            } @else {
              <span class="badge red">Out of stock</span>
            }
            <p>{{ p.description }}</p>

            @if (p.inStock) {
              <div class="row">
                <label class="qty">
                  <span class="sr-only">Quantity</span>
                  <select [value]="quantity()" (change)="quantity.set(+$any($event.target).value)">
                    @for (n of quantityOptions(); track n) {
                      <option [value]="n">{{ n }}</option>
                    }
                  </select>
                </label>
                <button class="btn" [disabled]="cart.pendingProductId() === p.id" (click)="cart.add(p.id, quantity())">
                  Add to cart
                </button>
                <a class="btn secondary" routerLink="/cart">View cart</a>
              </div>
            }
            @if (cart.message(); as m) {
              @if (m.productId === p.id) {
                <div class="alert" [class.success]="m.ok" [class.error]="!m.ok">{{ m.text }}</div>
              }
            }
          </div>
        </div>
      } @else {
        <p class="muted">Loading…</p>
      }
    </section>
  `,
  styles: `
    .layout {
      display: grid;
      grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
      gap: 32px;
      margin-top: 16px;
      align-items: start;
    }
    .price {
      font-size: 1.6rem;
      font-weight: 800;
      color: var(--jf-navy);
    }
    .qty select {
      width: 90px;
    }
    @media (max-width: 800px) {
      .layout {
        grid-template-columns: 1fr;
      }
    }
  `,
})
export class ProductPage {
  private readonly api = inject(CatalogApi);
  protected readonly cart = inject(AddToCart);

  /** Route parameter. */
  readonly id = input.required<string>();
  protected readonly quantity = signal(1);

  protected readonly product = rxResource({
    params: () => Number(this.id()),
    stream: ({ params }) => this.api.product(params),
  });

  protected readonly quantityOptions = computed(() => {
    const max = Math.min(this.product.value()?.availableQuantity ?? 1, 10);
    return Array.from({ length: Math.max(max, 1) }, (_, i) => i + 1);
  });
}
