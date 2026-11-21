import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { AdminApi, ProductForm } from '../../core/api';
import { errorMessage } from '../../core/api-error';
import { AdminProduct } from '../../core/models';
import { ProductImage } from '../../shared/product-image';

@Component({
  selector: 'jf-admin-product-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, ProductImage],
  template: `
    <a routerLink="/admin/products" class="small">← Products</a>
    <h1>{{ isNew() ? 'Add product' : 'Edit product' }}</h1>

    @if (error()) {
      <div class="alert error">{{ error() }}</div>
    }

    <div class="two-col">
      <form class="card form-grid" [formGroup]="form" (ngSubmit)="save()">
        <div class="field">
          <label for="sku">SKU</label>
          <input id="sku" formControlName="sku" [readonly]="!isNew()" />
          @if (!isNew()) {
            <span class="muted small">SKUs are permanent so past orders stay traceable.</span>
          }
        </div>
        <div class="field">
          <label for="categoryId">Category</label>
          <select id="categoryId" formControlName="categoryId">
            <option [ngValue]="0" disabled>Select a category</option>
            @for (c of categories.value() ?? []; track c.id) {
              <option [ngValue]="c.id">{{ c.name }}</option>
            }
          </select>
        </div>
        <div class="field full">
          <label for="name">Name</label>
          <input id="name" formControlName="name" />
        </div>
        <div class="field full">
          <label for="description">Description</label>
          <textarea id="description" formControlName="description"></textarea>
        </div>
        <div class="field">
          <label for="price">Price</label>
          <input id="price" type="number" min="0" step="0.01" formControlName="price" />
        </div>
        @if (isNew()) {
          <div class="field">
            <label for="initialStock">Initial stock</label>
            <input id="initialStock" type="number" min="0" step="1" formControlName="initialStock" />
          </div>
        }
        <div class="field full">
          <label for="imageUrl">Image URL <span class="muted">(optional)</span></label>
          <input id="imageUrl" formControlName="imageUrl" placeholder="https://…" />
        </div>
        <div class="full row">
          <button class="btn" type="submit" [disabled]="saving()">{{ saving() ? 'Saving…' : 'Save product' }}</button>
          <a class="btn secondary" routerLink="/admin/products">Cancel</a>
        </div>
      </form>

      <aside class="card stack">
        <h3>Preview</h3>
        <jf-product-image [imageUrl]="form.controls.imageUrl.value || null" [name]="form.controls.name.value || 'New product'" />
        @if (existing(); as p) {
          <p class="small muted">
            Stock: {{ p.onHand }} on hand · {{ p.reserved }} reserved · {{ p.available }} available.
            Adjust stock from the products list.
          </p>
        }
      </aside>
    </div>
  `,
})
export class AdminProductForm implements OnInit {
  private readonly api = inject(AdminApi);
  private readonly router = inject(Router);
  private readonly fb = inject(NonNullableFormBuilder);

  /** Route parameter; absent for /admin/products/new. */
  readonly id = input<string>();
  protected readonly isNew = computed(() => !this.id());
  protected readonly existing = signal<AdminProduct | null>(null);
  protected readonly saving = signal(false);
  protected readonly error = signal<string | null>(null);

  protected readonly categories = rxResource({ stream: () => this.api.categories() });

  protected readonly form = this.fb.group({
    sku: ['', [Validators.required, Validators.maxLength(64), Validators.pattern(/^[A-Za-z0-9._-]+$/)]],
    name: ['', [Validators.required, Validators.maxLength(160)]],
    description: ['', Validators.maxLength(5000)],
    price: [0, [Validators.required, Validators.min(0)]],
    categoryId: [0, [Validators.required, Validators.min(1)]],
    initialStock: [0, [Validators.required, Validators.min(0)]],
    imageUrl: ['', Validators.maxLength(500)],
  });

  async ngOnInit(): Promise<void> {
    const id = this.id();
    if (!id) return;
    try {
      const product = await firstValueFrom(this.api.product(Number(id)));
      this.existing.set(product);
      this.form.patchValue({
        sku: product.sku,
        name: product.name,
        description: product.description ?? '',
        price: product.price,
        categoryId: product.category.id,
        imageUrl: product.imageUrl ?? '',
      });
    } catch (e) {
      this.error.set(errorMessage(e));
    }
  }

  protected async save(): Promise<void> {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.error.set('Please complete the highlighted fields.');
      return;
    }
    const v = this.form.getRawValue();
    const payload: ProductForm = {
      name: v.name,
      description: v.description || undefined,
      price: Number(v.price),
      imageUrl: v.imageUrl || undefined,
      categoryId: v.categoryId,
    };
    this.saving.set(true);
    this.error.set(null);
    try {
      if (this.isNew()) {
        await firstValueFrom(this.api.createProduct({ ...payload, sku: v.sku, initialStock: Number(v.initialStock) }));
      } else {
        await firstValueFrom(this.api.updateProduct(Number(this.id()), payload));
      }
      await this.router.navigate(['/admin/products']);
    } catch (e) {
      this.error.set(errorMessage(e));
    } finally {
      this.saving.set(false);
    }
  }
}
