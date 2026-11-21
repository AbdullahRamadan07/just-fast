import { Routes } from '@angular/router';
import { AdminShell } from './admin-shell';

export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    component: AdminShell,
    children: [
      {
        path: '',
        title: 'Dashboard · Just Fast Admin',
        loadComponent: () => import('./admin-dashboard').then((m) => m.AdminDashboard),
      },
      {
        path: 'products',
        title: 'Products · Just Fast Admin',
        loadComponent: () => import('./admin-products').then((m) => m.AdminProducts),
      },
      {
        path: 'products/new',
        title: 'New product · Just Fast Admin',
        loadComponent: () => import('./admin-product-form').then((m) => m.AdminProductForm),
      },
      {
        path: 'products/:id',
        title: 'Edit product · Just Fast Admin',
        loadComponent: () => import('./admin-product-form').then((m) => m.AdminProductForm),
      },
      {
        path: 'categories',
        title: 'Categories · Just Fast Admin',
        loadComponent: () => import('./admin-categories').then((m) => m.AdminCategories),
      },
      {
        path: 'orders',
        title: 'Orders · Just Fast Admin',
        loadComponent: () => import('./admin-orders').then((m) => m.AdminOrders),
      },
      {
        path: 'orders/:id',
        title: 'Order · Just Fast Admin',
        loadComponent: () => import('./admin-order').then((m) => m.AdminOrder),
      },
    ],
  },
];
