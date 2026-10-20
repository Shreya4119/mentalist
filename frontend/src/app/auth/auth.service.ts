import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

export type Role = 'ADMIN' | 'USER';

interface LoginResponse {
  token: string;
  role: Role;
  /** The account name as stored (sign-up names are lower-case). */
  username?: string;
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
  /** How the signed-in person is shown in the top bar. */
  readonly displayName = computed(() => `${this.usernameSignal() ?? ''} (${this.roleSignal() ?? ''})`);

  login(username: string, password: string): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>('/api/auth/login', { username, password })
      .pipe(tap((response) => this.store(response, username)));
  }

  /** Creates an account and signs in (only when the server has sign-up enabled). */
  signup(username: string, password: string, acceptedTerms: boolean): Observable<LoginResponse> {
    return this.http
      .post<LoginResponse>('/api/auth/signup', { username, password, acceptedTerms })
      .pipe(tap((response) => this.store(response, username.trim().toLowerCase())));
  }

  /** Deletes the account and everything it owns, then signs out. */
  deleteAccount(): Observable<void> {
    return this.http.delete<void>('/api/account').pipe(tap(() => this.logout()));
  }

  private store(response: LoginResponse, typedName: string): void {
    const username = response.username ?? typedName;
    localStorage.setItem(TOKEN_KEY, response.token);
    localStorage.setItem(ROLE_KEY, response.role);
    localStorage.setItem(USERNAME_KEY, username);
    this.tokenSignal.set(response.token);
    this.roleSignal.set(response.role);
    this.usernameSignal.set(username);
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
