import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Just Fast brand logo.
 * - `lockup`: horizontal shopping bag + wordmark, used on the light storefront header and at checkout.
 * - `mark`: shopping bag alone, for tight spaces (mobile header, favicon-sized spots).
 */
@Component({
  selector: 'jf-logo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (variant() === 'mark') {
      <img
        src="brand/just-fast-mark.webp"
        alt="Just Fast"
        [attr.width]="height()"
        [attr.height]="height()"
        [style.height.px]="height()"
        [style.width.px]="height()"
        decoding="async"
      />
    } @else {
      <img
        src="brand/just-fast-logo.webp"
        alt="Just Fast"
        [attr.width]="lockupWidth()"
        [attr.height]="height()"
        [style.height.px]="height()"
        [style.width.px]="lockupWidth()"
        decoding="async"
      />
    }
  `,
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
    }
    img {
      object-fit: contain;
    }
  `,
})
export class Logo {
  readonly variant = input<'lockup' | 'mark'>('lockup');
  readonly height = input(40);

  /** The trimmed lockup artwork is 1813 × 436 px. */
  protected readonly lockupWidth = computed(() => Math.round((this.height() * 1813) / 436));
}
