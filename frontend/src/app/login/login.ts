import { Component, ElementRef, afterNextRender, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { GITHUB_URL, HostingService, serverMessage } from '../core/hosting.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule, RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  protected readonly hosting = inject(HostingService);

  protected readonly githubUrl = GITHUB_URL;
  readonly mode = signal<'signin' | 'signup'>(this.route.snapshot.queryParamMap.get('mode') === 'signup' ? 'signup' : 'signin');
  readonly username = signal('');
  readonly password = signal('');
  readonly confirm = signal('');
  readonly accepted = signal(false);
  /** Hidden "website" field: people never see it, bots that fill every field do. */
  readonly website = signal('');
  private formToken: string | null = null;
  readonly formError = signal<string | null>(null);
  readonly loading = signal(false);
  readonly accountDeleted = signal(this.route.snapshot.queryParamMap.get('deleted') === '1');
  readonly sessionEnded = signal(this.route.snapshot.queryParamMap.get('session') === 'ended');
  readonly minPassword = computed(() => this.hosting.config()?.minPasswordLength ?? 10);

  private readonly usernameInput = viewChild<ElementRef<HTMLInputElement>>('usernameInput');
  private readonly signupName = viewChild<ElementRef<HTMLInputElement>>('signupName');

  constructor() {
    this.hosting.loadConfig();
    afterNextRender(() => this.usernameInput()?.nativeElement.focus());
    if (this.mode() === 'signup') this.loadFormToken();
  }

  setMode(mode: 'signin' | 'signup'): void {
    this.mode.set(mode);
    this.formError.set(null);
    this.password.set('');
    this.confirm.set('');
    setTimeout(() => (mode === 'signup' ? this.signupName() : this.usernameInput())?.nativeElement.focus());
    if (mode === 'signup') this.loadFormToken();
  }

  /** Fetched when the sign-up form opens; the server refuses forms submitted without one or within seconds. */
  private loadFormToken(): void {
    this.authService.signupFormToken().subscribe({
      next: (t) => (this.formToken = t),
      error: () => (this.formToken = null),
    });
  }

  submit(): void {
    if (this.loading() || !this.username().trim() || !this.password()) {
      return;
    }
    this.formError.set(null);
    this.loading.set(true);
    this.authService.login(this.username().trim(), this.password()).subscribe({
      next: () => this.done(),
      error: (err) => {
        this.loading.set(false);
        this.formError.set(
          err?.status === 401
            ? 'Invalid username or password.'
            : err?.status === 429
              ? (serverMessage(err) ?? 'Too many attempts. Try again later.')
              : 'Can’t reach the Mentalist server. Make sure the backend is running, then try again.',
        );
      },
    });
  }

  createAccount(): void {
    if (this.loading()) return;
    const problem = this.signupProblem();
    if (problem) {
      this.formError.set(problem);
      return;
    }
    this.formError.set(null);
    this.loading.set(true);
    if (!this.formToken) {
      // The token from opening the form hasn't arrived yet (slow network): fetch one, then send.
      this.authService.signupFormToken().subscribe({
        next: (t) => {
          this.formToken = t;
          this.sendSignup();
        },
        error: (err) => {
          this.loading.set(false);
          this.formError.set(serverMessage(err) ?? 'Could not create your account. Please try again.');
        },
      });
      return;
    }
    this.sendSignup();
  }

  private sendSignup(): void {
    this.authService.signup(this.username().trim(), this.password(), this.accepted(), this.formToken, this.website()).subscribe({
      next: () => this.done(),
      error: (err) => {
        this.loading.set(false);
        this.loadFormToken(); // a fresh one for the next attempt (e.g. after "form expired")
        this.formError.set(serverMessage(err) ?? 'Could not create your account. Please try again.');
      },
    });
  }

  /** Quick checks before asking the server (which checks everything again). */
  private signupProblem(): string | null {
    if (this.username().trim().length < 3) return 'Choose a username of at least 3 characters.';
    if (this.password().length < this.minPassword()) return `Use a password of at least ${this.minPassword()} characters.`;
    if (this.password() !== this.confirm()) return 'The two passwords don’t match.';
    if (!this.accepted()) return 'Please confirm that you’ve read how Mentalist works and what it isn’t.';
    return null;
  }

  private done(): void {
    this.loading.set(false);
    this.router.navigate(['/reports']);
  }
}
