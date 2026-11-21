import { CurrencyPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { CatalogApi } from '../../core/api';
import { Pagination } from '../../shared/pagination';
import { ProductImage } from '../../shared/product-image';
import { AddToCart } from './add-to-cart';

const PAGE_SIZE = 12;

@Component({
  selector: 'jf-catalog-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, CurrencyPipe, ProductImage, Pagination],
  template: `
    <section class="hero">
      <div class="container">
        <h1>Everything you need, delivered fast.</h1>
        <p>Browse the catalog, check out securely, and track every order from your account.</p>
      </div>
    </section>

    <section class="container page">
      <div class="toolbar">
        <div class="chips" role="tablist" aria-label="Categories">
          <button class="chip" [class.on]="!category()" (click)="go({ category: null })">All</button>
          @for (c of categories.value() ?? []; track c.id) {
            <button class="chip" [class.on]="category() === c.slug" (click)="go({ category: c.slug })">
              {{ c.name }}
            </button>
          }
        </div>
        <label class="sort">
          <span class="sr-only">Sort by</span>
          <select [value]="sort() ?? 'name'" (change)="go({ sort: $any($event.target).value })">
            <option value="name">Name</option>
            <option value="price">Price: low to high</option>
            <option value="-price">Price: high to low</option>
            <option value="newest">Newest</option>
          </select>
        </label>
      </div>

      @if (q()) {
        <p class="muted">
          Results for “{{ q() }}” · <button class="link-btn" (click)="go({ q: null })">clear search</button>
        </p>
      }

      @if (products.error()) {
        <div class="alert error">We couldn't load products. Please refresh the page.</div>
      } @else if (products.isLoading() && !products.value()) {
        <p class="muted">Loading products…</p>
      } @else if (products.value(); as result) {
        @if (result.items.length === 0) {
          <div class="empty card">No products match your filters.</div>
        } @else {
          <div class="product-grid">
            @for (p of result.items; track p.id) {
              <article class="card product">
                <a [routerLink]="['/products', p.id]" class="image-link">
                  <jf-product-image [imageUrl]="p.imageUrl" [name]="p.name" />
                </a>
                <div class="meta">
                  <span class="muted small">{{ p.category.name }}</span>
                  <a [routerLink]="['/products', p.id]" class="name">{{ p.name }}</a>
                  <div class="row price-row">
                    <strong class="price">{{ p.price | currency: p.currency }}</strong>
                    @if (p.inStock) {
                      <span class="badge green">In stock</span>
                    } @else {
                      <span class="badge red">Out of stock</span>
                    }
                  </div>
                </div>
                <button
                  class="btn block"
                  [disabled]="!p.inStock || cart.pendingProductId() === p.id"
                  (click)="cart.add(p.id)"
                >
                  {{ cart.pendingProductId() === p.id ? 'Adding…' : 'Add to cart' }}
                </button>
                @if (cart.message(); as m) {
                  @if (m.productId === p.id) {
                    <span class="small" [class.ok]="m.ok" [class.err]="!m.ok">{{ m.text }}</span>
                  }
                }
              </article>
            }
          </div>
          <jf-pagination [page]="result.page" [totalPages]="result.totalPages" (pageChange)="go({ page: $event })" />
        }
      }
    </section>
  `,
  styles: `
    .hero {
      background: linear-gradient(120deg, var(--jf-navy) 0%, #0a2f7a 55%, var(--jf-blue) 100%);
      color: #fff;
      padding: 40px 0;
    }
    .hero h1 {
      color: #fff;
    }
    .hero p {
      margin: 0;
      opacity: 0.85;
    }
    .toolbar {
      display: flex;
      gap: 12px;
      align-items: center;
      flex-wrap: wrap;
      margin-bottom: 20px;
    }
    .chips {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
      flex: 1;
    }
    .chip {
      border: 1px solid var(--border);
      background: #fff;
      border-radius: 999px;
      padding: 6px 14px;
      font: inherit;
      font-size: 0.9rem;
      cursor: pointer;
      color: var(--jf-navy);
    }
    .chip.on {
      background: var(--jf-navy);
      border-color: var(--jf-navy);
      color: #fff;
    }
    .sort select {
      width: auto;
    }
    .product {
      display: flex;
      flex-direction: column;
      gap: 12px;
      padding: 14px;
    }
    .meta {
      display: flex;
      flex-direction: column;
      gap: 4px;
      flex: 1;
    }
    .name {
      color: var(--jf-navy);
      font-weight: 700;
    }
    .price {
      font-size: 1.1rem;
      color: var(--jf-navy);
    }
    .price-row {
      justify-content: space-between;
    }
    .ok {
      color: var(--success);
    }
    .err {
      color: var(--danger);
    }
  `,
})
export class CatalogPage {
  private readonly api = inject(CatalogApi);
  private readonly router = inject(Router);
  protected readonly cart = inject(AddToCart);

  // Bound from query parameters (withComponentInputBinding).
  readonly q = input<string>();
  readonly category = input<string>();
  readonly sort = input<string>();
  readonly page = input<string>();

  private readonly pageIndex = computed(() => Math.max(Number(this.page() ?? 0) || 0, 0));

  protected readonly categories = rxResource({ stream: () => this.api.categories() });

  protected readonly products = rxResource({
    params: () => ({
      q: this.q(),
      category: this.category(),
      sort: this.sort(),
      page: this.pageIndex(),
      size: PAGE_SIZE,
    }),
    stream: ({ params }) => this.api.products(params),
  });

  protected go(change: { q?: string | null; category?: string | null; sort?: string; page?: number }): void {
    const resetPage = change.page === undefined;
    void this.router.navigate([], {
      queryParams: { ...change, page: resetPage ? null : change.page || null },
      queryParamsHandling: 'merge',
    });
  }
}
