import { Routes } from '@angular/router';

export const STOREFRONT_ROUTES: Routes = [
  {
    path: '',
    title: 'Just Fast — Shop',
    loadComponent: () => import('./catalog-page').then((m) => m.CatalogPage),
  },
  {
    path: 'products/:id',
    title: 'Product · Just Fast',
    loadComponent: () => import('./product-page').then((m) => m.ProductPage),
  },
];
