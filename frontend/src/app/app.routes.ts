import { Routes } from '@angular/router';
import { adminGuard, authGuard } from './core/guards';

/** Feature-based, lazily loaded routes: storefront, checkout, account and admin dashboard. */
export const routes: Routes = [
  {
    path: '',
    loadChildren: () => import('./features/storefront/storefront.routes').then((m) => m.STOREFRONT_ROUTES),
  },
  {
    path: 'cart',
    canActivate: [authGuard],
    title: 'Cart · Just Fast',
    loadComponent: () => import('./features/checkout/cart-page').then((m) => m.CartPage),
  },
  {
    path: 'checkout',
    canActivate: [authGuard],
    loadChildren: () => import('./features/checkout/checkout.routes').then((m) => m.CHECKOUT_ROUTES),
  },
  {
    path: 'account',
    loadChildren: () => import('./features/account/account.routes').then((m) => m.ACCOUNT_ROUTES),
  },
  {
    path: 'admin',
    canActivate: [adminGuard],
    loadChildren: () => import('./features/admin/admin.routes').then((m) => m.ADMIN_ROUTES),
  },
  {
    path: '**',
    title: 'Not found · Just Fast',
    loadComponent: () => import('./layout/not-found').then((m) => m.NotFound),
  },
];
