import { ChangeDetectionStrategy, Component } from '@angular/core';
import { Logo } from '../shared/logo';

@Component({
  selector: 'jf-footer',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Logo],
  template: `
    <footer>
      <div class="container row">
        <jf-logo variant="mark" [height]="28" />
        <span class="muted small">© {{ year }} Just Fast. Secure payments by our payment provider.</span>
      </div>
    </footer>
  `,
  styles: `
    footer {
      border-top: 1px solid var(--border);
      background: #fff;
      padding: 20px 0;
    }
  `,
})
export class Footer {
  protected readonly year = new Date().getFullYear();
}
