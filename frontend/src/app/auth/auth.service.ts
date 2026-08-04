import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

export type Role = 'HR_ADMIN' | 'EMPLOYEE';

interface LoginResponse {
  token: string;
  role: Role;
}

const TOKEN_KEY = 'hr_rag_token';
const ROLE_KEY = 'hr_rag_role';
const USERNAME_KEY = 'hr_rag_username';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);

  private readonly tokenSignal = signal<string | null>(localStorage.getItem(TOKEN_KEY));
  private readonly roleSignal = signal<Role | null>(localStorage.getItem(ROLE_KEY) as Role | null);
  private readonly usernameSignal = signal<string | null>(localStorage.getItem(USERNAME_KEY));

  readonly token = this.tokenSignal.asReadonly();
  readonly role = this.roleSignal.asReadonly();
  readonly username = this.usernameSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.tokenSignal() !== null);
  readonly isHrAdmin = computed(() => this.roleSignal() === 'HR_ADMIN');

  login(username: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>('/api/auth/login', { username, password }).pipe(
      tap((response) => {
        localStorage.setItem(TOKEN_KEY, response.token);
        localStorage.setItem(ROLE_KEY, response.role);
        localStorage.setItem(USERNAME_KEY, username);
        this.tokenSignal.set(response.token);
        this.roleSignal.set(response.role);
        this.usernameSignal.set(username);
      }),
    );
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(ROLE_KEY);
    localStorage.removeItem(USERNAME_KEY);
    this.tokenSignal.set(null);
    this.roleSignal.set(null);
    this.usernameSignal.set(null);
  }
}
