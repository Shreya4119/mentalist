import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

export type Role = 'ADMIN' | 'USER';

interface LoginResponse {
  token: string;
  role: Role;
}

const TOKEN_KEY = 'mentalist_token';
const ROLE_KEY = 'mentalist_role';
const USERNAME_KEY = 'mentalist_username';

// Sessions saved under the old keys carry role names that no longer exist: drop them.
for (const legacyKey of ['hr_rag_token', 'hr_rag_role', 'hr_rag_username']) {
  localStorage.removeItem(legacyKey);
}

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
  readonly isAdmin = computed(() => this.roleSignal() === 'ADMIN');

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
