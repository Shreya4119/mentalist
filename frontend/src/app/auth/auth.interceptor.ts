import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Only 401 means "session invalid, go log in again" -- the backend returns
 * 401 for a missing/invalid/expired token and 403 for a wrong role, and lets
 * 400/404/413 through with their real status. Logging out on 403 (or on any
 * error) would kick the user out for an ordinary failed request.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const token = authService.token();

  const authorizedReq = token ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }) : req;

  return next(authorizedReq).pipe(
    catchError((error: unknown) => {
      const isAuthFailure = error instanceof HttpErrorResponse && error.status === 401;
      const isLoginRequest = req.url.includes('/api/auth/login');

      if (isAuthFailure && !isLoginRequest) {
        authService.logout();
        router.navigate(['/login']);
      }
      return throwError(() => error);
    }),
  );
};
