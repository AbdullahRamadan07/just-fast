import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'jf-admin-shell',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="admin-bar">
      <div class="container row">
        <strong>Store admin</strong>
        <nav class="row">
          <a routerLink="/admin" routerLinkActive="on" [routerLinkActiveOptions]="{ exact: true }">Overview</a>
          <a routerLink="/admin/orders" routerLinkActive="on">Orders</a>
          <a routerLink="/admin/products" routerLinkActive="on">Products &amp; stock</a>
          <a routerLink="/admin/categories" routerLinkActive="on">Categories</a>
        </nav>
      </div>
    </div>
    <section class="container page">
      <router-outlet />
    </section>
  `,
  styles: `
    .admin-bar {
      background: var(--jf-navy);
      color: #fff;
      padding: 10px 0;
    }
    nav {
      gap: 4px;
    }
    nav a {
      color: rgba(255, 255, 255, 0.8);
      padding: 6px 12px;
      border-radius: 8px;
      font-weight: 600;
      font-size: 0.925rem;
    }
    nav a:hover {
      text-decoration: none;
      color: #fff;
    }
    nav a.on {
      background: rgba(255, 255, 255, 0.14);
      color: #fff;
    }
  `,
})
export class AdminShell {}
