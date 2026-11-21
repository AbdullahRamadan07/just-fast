import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';

/** Product photo, or a branded placeholder when no image is set (or it fails to load). */
@Component({
  selector: 'jf-product-image',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (imageUrl() && !failed()) {
      <img [src]="imageUrl()" [alt]="name()" loading="lazy" (error)="failed.set(true)" />
    } @else {
      <div class="placeholder" [style.background]="gradient()" role="img" [attr.aria-label]="name()">
        <img class="mark" src="brand/just-fast-mark.webp" alt="" aria-hidden="true" />
        <span>{{ initials() }}</span>
      </div>
    }
  `,
  styles: `
    :host {
      display: block;
      container-type: inline-size;
      aspect-ratio: 4 / 3;
      border-radius: 10px;
      overflow: hidden;
      background: var(--jf-blue-50);
    }
    img {
      width: 100%;
      height: 100%;
      object-fit: cover;
    }
    .placeholder {
      position: relative;
      width: 100%;
      height: 100%;
      display: grid;
      place-items: center;
    }
    .placeholder span {
      font-size: clamp(0.9rem, 22cqw, 2.4rem);
      font-weight: 800;
      color: var(--jf-navy);
      letter-spacing: -0.02em;
    }
    .mark {
      position: absolute;
      right: 6%;
      bottom: 6%;
      width: clamp(14px, 14cqw, 28px);
      height: clamp(14px, 14cqw, 28px);
      opacity: 0.85;
    }
  `,
})
export class ProductImage {
  readonly imageUrl = input<string | undefined | null>();
  readonly name = input.required<string>();
  protected readonly failed = signal(false);

  protected readonly initials = computed(() =>
    this.name()
      .split(/\s+/)
      .filter((w) => /^[A-Za-z0-9]/.test(w))
      .slice(0, 2)
      .map((w) => w[0].toUpperCase())
      .join(''),
  );

  protected readonly gradient = computed(() => {
    let hash = 0;
    for (const ch of this.name()) {
      hash = (hash * 31 + ch.charCodeAt(0)) | 0;
    }
    const hue = 200 + (Math.abs(hash) % 40); // stay within the brand's blue family
    return `linear-gradient(135deg, hsl(${hue} 100% 95%), hsl(${hue} 90% 86%))`;
  });
}
