import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth-guard';
import { roleGuard } from './core/guards/role-guard';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'login',
    loadComponent: () => import('./features/auth/login/login').then((m) => m.Login),
  },
  {
    path: 'register',
    loadComponent: () => import('./features/auth/register/register').then((m) => m.Register),
  },
  {
    path: 'sso/callback',
    loadComponent: () =>
      import('./features/auth/sso-callback/sso-callback').then((m) => m.SsoCallback),
  },
  {
    path: 'dashboard',
    canActivate: [authGuard],
    loadComponent: () => import('./features/dashboard/dashboard').then((m) => m.Dashboard),
  },
  {
    path: 'requisitions',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/requisitions/requisition-list/requisition-list').then((m) => m.RequisitionList),
  },
  {
    path: 'requisitions/new',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/requisitions/requisition-create/requisition-create').then((m) => m.RequisitionCreate),
  },
  {
    path: 'requisitions/:id',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/requisitions/requisition-detail/requisition-detail').then((m) => m.RequisitionDetail),
  },
  {
    path: 'departments',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ROLE_ADMIN'] },
    loadComponent: () =>
      import('./features/departments/department-list/department-list').then((m) => m.DepartmentList),
  },
  {
    path: 'vendors',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ROLE_ADMIN', 'ROLE_PROCUREMENT_OFFICER'] },
    loadComponent: () => import('./features/vendors/vendor-list/vendor-list').then((m) => m.VendorList),
  },
  {
    path: 'catalog',
    canActivate: [authGuard],
    loadComponent: () => import('./features/catalog/catalog-list/catalog-list').then((m) => m.CatalogList),
  },
  {
    path: 'purchase-orders',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ROLE_ADMIN', 'ROLE_PROCUREMENT_OFFICER'] },
    loadComponent: () =>
      import('./features/purchase-orders/purchase-order-list/purchase-order-list').then(
        (m) => m.PurchaseOrderList,
      ),
  },
  {
    path: 'invoices',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ROLE_ADMIN', 'ROLE_PROCUREMENT_OFFICER', 'ROLE_FINANCE_APPROVER'] },
    loadComponent: () => import('./features/invoices/invoice-list/invoice-list').then((m) => m.InvoiceList),
  },
  { path: '**', redirectTo: 'dashboard' },
];
