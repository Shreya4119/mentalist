import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Any 401/403 is treated as "session invalid, go log in again." The backend
 * also returns 403 for role-mismatched requests (Spring Security's default
 * entry point doesn't distinguish "unauthenticated" from "wrong role"), but
 * the route guards below already keep the UI from ever sending a
 * role-mismatched request in normal use -- so in practice any 403 reaching
 * here really does mean the token is missing/invalid/expired.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const token = authService.token();

  const authorizedReq = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(authorizedReq).pipe(
    catchError((error: unknown) => {
      const isAuthFailure = error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403);
      const isLoginRequest = req.url.includes('/api/auth/login');

      if (isAuthFailure && !isLoginRequest) {
        authService.logout();
        router.navigate(['/login']);
      }
      return throwError(() => error);
    }),
  );
};
