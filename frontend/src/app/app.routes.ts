import { Routes } from '@angular/router';
import { authGuard, hrAdminGuard } from './auth/auth.guard';
import { Chat } from './chat/chat';
import { Documents } from './documents/documents';
import { Login } from './login/login';

export const routes: Routes = [
  { path: 'login', component: Login },
  { path: 'chat', component: Chat, canActivate: [authGuard] },
  { path: 'documents', component: Documents, canActivate: [authGuard, hrAdminGuard] },
  { path: '', pathMatch: 'full', redirectTo: 'chat' },
  { path: '**', redirectTo: 'chat' },
];
