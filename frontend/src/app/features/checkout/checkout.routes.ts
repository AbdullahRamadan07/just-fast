import { Routes } from '@angular/router';

export const CHECKOUT_ROUTES: Routes = [
  {
    path: '',
    title: 'Checkout · Just Fast',
    loadComponent: () => import('./checkout-page').then((m) => m.CheckoutPage),
  },
  {
    path: 'return',
    title: 'Payment status · Just Fast',
    loadComponent: () => import('./checkout-return').then((m) => m.CheckoutReturn),
  },
];
