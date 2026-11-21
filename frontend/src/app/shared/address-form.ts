import { ChangeDetectionStrategy, Component, effect, inject, input, output } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Address } from '../core/models';
import { StoreInfoService } from '../core/store-info.service';

/** Shipping address form shared by the account page and checkout. */
@Component({
  selector: 'jf-address-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  template: `
    <form [formGroup]="form" (ngSubmit)="submit()" class="form-grid">
      <div class="field full">
        <label for="fullName">Full name</label>
        <input id="fullName" formControlName="fullName" autocomplete="name" />
      </div>
      <div class="field full">
        <label for="line1">Address line 1</label>
        <input id="line1" formControlName="line1" autocomplete="address-line1" />
      </div>
      <div class="field full">
        <label for="line2">Address line 2 <span class="muted">(optional)</span></label>
        <input id="line2" formControlName="line2" autocomplete="address-line2" />
      </div>
      <div class="field">
        <label for="city">City</label>
        <input id="city" formControlName="city" autocomplete="address-level2" />
      </div>
      <div class="field">
        <label for="region">State / region</label>
        <input id="region" formControlName="region" autocomplete="address-level1" />
      </div>
      <div class="field">
        <label for="postalCode">Postal code</label>
        <input id="postalCode" formControlName="postalCode" autocomplete="postal-code" />
      </div>
      <div class="field">
        <label for="country">Country</label>
        <input id="country" formControlName="country" readonly />
        <span class="muted small">We currently ship to {{ storeInfo.info().shippingCountry }} only.</span>
      </div>
      <div class="field full">
        <label for="phone">Phone <span class="muted">(optional)</span></label>
        <input id="phone" formControlName="phone" autocomplete="tel" />
      </div>
      <div class="full row">
        <button class="btn" type="submit" [disabled]="busy()">{{ submitLabel() }}</button>
        @if (cancellable()) {
          <button class="btn secondary" type="button" (click)="cancelled.emit()">Cancel</button>
        }
      </div>
    </form>
  `,
})
export class AddressForm {
  protected readonly storeInfo = inject(StoreInfoService);
  private readonly fb = inject(NonNullableFormBuilder);

  readonly value = input<Address | null>(null);
  readonly submitLabel = input('Save address');
  readonly busy = input(false);
  readonly cancellable = input(false);
  readonly saved = output<Address>();
  readonly cancelled = output<void>();

  protected readonly form = this.fb.group({
    fullName: ['', [Validators.required, Validators.maxLength(120)]],
    line1: ['', [Validators.required, Validators.maxLength(200)]],
    line2: ['', Validators.maxLength(200)],
    city: ['', [Validators.required, Validators.maxLength(100)]],
    region: ['', Validators.maxLength(100)],
    postalCode: ['', [Validators.required, Validators.maxLength(20)]],
    country: ['', [Validators.required, Validators.pattern(/^[A-Za-z]{2}$/)]],
    phone: ['', Validators.maxLength(30)],
  });

  constructor() {
    effect(() => {
      const v = this.value();
      this.form.reset({
        fullName: v?.fullName ?? '',
        line1: v?.line1 ?? '',
        line2: v?.line2 ?? '',
        city: v?.city ?? '',
        region: v?.region ?? '',
        postalCode: v?.postalCode ?? '',
        country: v?.country ?? this.storeInfo.info().shippingCountry,
        phone: v?.phone ?? '',
      });
    });
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saved.emit(this.form.getRawValue());
  }
}
