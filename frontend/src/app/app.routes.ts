import { Routes } from '@angular/router';
import { authGuard } from './auth/auth.guard';
import { Login } from './login/login';

/**
 * Workflow: log in -> Financial reports -> upload -> AI analysis page ->
 * ask questions about that report (inline or full screen) or compare two
 * reports. My plan is the family financial health check. Learn explains
 * every term. Chat is always scoped to one report.
 *
 * Pages are lazy-loaded so the first screen downloads only what it needs.
 */
export const routes: Routes = [
  { path: 'login', component: Login },
  { path: 'reports', canActivate: [authGuard], loadComponent: () => import('./reports/list/reports-list').then((m) => m.ReportsList) },
  { path: 'reports/compare', canActivate: [authGuard], loadComponent: () => import('./reports/compare/report-compare').then((m) => m.ReportComparePage) },
  { path: 'reports/learn', canActivate: [authGuard], loadComponent: () => import('./reports/learn/learn').then((m) => m.LearnPage) },
  { path: 'reports/:id/chat', canActivate: [authGuard], loadComponent: () => import('./chat/chat').then((m) => m.Chat) },
  { path: 'reports/:id', canActivate: [authGuard], loadComponent: () => import('./reports/detail/report-detail').then((m) => m.ReportDetailPage) },
  { path: 'plan', canActivate: [authGuard], loadComponent: () => import('./planning/plan-home').then((m) => m.PlanHome) },
  { path: 'plan/check', canActivate: [authGuard], loadComponent: () => import('./planning/plan-check').then((m) => m.PlanCheck) },
  { path: 'plan/:id', canActivate: [authGuard], loadComponent: () => import('./planning/plan-detail').then((m) => m.PlanDetail) },
  // Old full-screen chat links keep working.
  { path: 'chat/:id', redirectTo: 'reports/:id/chat' },
  { path: '', pathMatch: 'full', redirectTo: 'reports' },
  { path: '**', redirectTo: 'reports' },
];
