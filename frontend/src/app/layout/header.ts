import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';
import { AuthService } from '../core/auth.service';
import { CartStore } from '../core/cart.store';
import { Logo } from '../shared/logo';

@Component({
  selector: 'jf-header',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, RouterLinkActive, FormsModule, Logo],
  template: `
    <header>
      <div class="container bar">
        <a routerLink="/" class="brand" aria-label="Just Fast home">
          <jf-logo class="lockup" [height]="38" />
          <jf-logo class="mark" variant="mark" [height]="36" />
        </a>

        <form class="search" role="search" (ngSubmit)="search()">
          <label for="site-search" class="sr-only">Search products</label>
          <input id="site-search" name="q" type="search" placeholder="Search products" [(ngModel)]="query" />
        </form>

        <nav class="nav">
          @if (auth.isAdmin()) {
            <a routerLink="/admin" routerLinkActive="active">Admin</a>
          }
          @if (auth.user(); as user) {
            <a routerLink="/account" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }">
              {{ firstName(user.fullName) }}
            </a>
            <button class="link-btn" (click)="logout()">Sign out</button>
          } @else {
            <a routerLink="/account/login">Sign in</a>
          }
          <a routerLink="/cart" class="cart" aria-label="Cart">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M6 7h12l-1 13H7L6 7Z" stroke="currentColor" stroke-width="2" stroke-linejoin="round" />
              <path d="M9 7a3 3 0 0 1 6 0" stroke="currentColor" stroke-width="2" />
            </svg>
            @if (cart.count() > 0) {
              <span class="count">{{ cart.count() }}</span>
            }
          </a>
        </nav>
      </div>
    </header>
  `,
  styles: `
    header {
      background: #fff;
      border-bottom: 1px solid var(--border);
      position: sticky;
      top: 0;
      z-index: 10;
    }
    .bar {
      display: flex;
      align-items: center;
      gap: 20px;
      min-height: 68px;
    }
    .brand {
      display: inline-flex;
      flex-shrink: 0;
    }
    .mark {
      display: none;
    }
    .search {
      flex: 1;
      max-width: 460px;
    }
    .search input {
      background: var(--bg);
    }
    .nav {
      display: flex;
      align-items: center;
      gap: 18px;
      margin-left: auto;
      font-weight: 600;
    }
    .nav a {
      color: var(--jf-navy);
    }
    .nav a.active {
      color: var(--jf-blue);
    }
    .cart {
      position: relative;
      display: inline-flex;
    }
    .count {
      position: absolute;
      top: -8px;
      right: -10px;
      background: var(--jf-blue);
      color: #fff;
      border-radius: 999px;
      font-size: 0.7rem;
      min-width: 18px;
      height: 18px;
      display: grid;
      place-items: center;
      padding: 0 4px;
    }
    @media (max-width: 720px) {
      .lockup {
        display: none;
      }
      .mark {
        display: inline-flex;
      }
      .bar {
        gap: 12px;
      }
      .nav {
        gap: 12px;
        font-size: 0.9rem;
      }
    }
  `,
})
export class Header {
  protected readonly auth = inject(AuthService);
  protected readonly cart = inject(CartStore);
  private readonly router = inject(Router);
  protected query = '';


  protected search(): void {
    void this.router.navigate(['/'], { queryParams: { q: this.query.trim() || null } });
  }

  protected firstName(fullName: string): string {
    return fullName.split(' ')[0] || 'Account';
  }

  protected async logout(): Promise<void> {
    await this.auth.logout();
    void this.router.navigate(['/']);
  }
}
