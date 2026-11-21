import { Routes } from '@angular/router';
import { authGuard, guestGuard } from '../../core/guards';

export const ACCOUNT_ROUTES: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    title: 'Sign in · Just Fast',
    loadComponent: () => import('./login-page').then((m) => m.LoginPage),
  },
  {
    path: 'register',
    canActivate: [guestGuard],
    title: 'Create account · Just Fast',
    loadComponent: () => import('./register-page').then((m) => m.RegisterPage),
  },
  {
    path: '',
    canActivate: [authGuard],
    title: 'Your account · Just Fast',
    loadComponent: () => import('./account-page').then((m) => m.AccountPage),
  },
  {
    path: 'orders/:id',
    canActivate: [authGuard],
    title: 'Order · Just Fast',
    loadComponent: () => import('./order-page').then((m) => m.OrderPage),
  },
];
