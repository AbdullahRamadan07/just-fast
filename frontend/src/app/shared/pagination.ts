import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';

@Component({
  selector: 'jf-pagination',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (totalPages() > 1) {
      <nav class="row" aria-label="Pagination">
        <button class="btn secondary small" [disabled]="page() === 0" (click)="pageChange.emit(page() - 1)">
          ← Previous
        </button>
        <span class="muted small">Page {{ page() + 1 }} of {{ totalPages() }}</span>
        <button
          class="btn secondary small"
          [disabled]="page() + 1 >= totalPages()"
          (click)="pageChange.emit(page() + 1)"
        >
          Next →
        </button>
      </nav>
    }
  `,
  styles: `
    nav {
      justify-content: center;
      margin-top: 24px;
    }
  `,
})
export class Pagination {
  readonly page = input.required<number>();
  readonly totalPages = input.required<number>();
  readonly pageChange = output<number>();
}
