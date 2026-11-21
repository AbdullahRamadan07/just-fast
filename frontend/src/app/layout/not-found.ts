import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'jf-not-found',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <section class="container page empty">
      <h1>Page not found</h1>
      <p>The page you're looking for doesn't exist.</p>
      <a class="btn" routerLink="/">Back to the store</a>
    </section>
  `,
})
export class NotFound {}
