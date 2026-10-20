import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';

export interface PublicConfig {
  publicMode: boolean;
  signupEnabled: boolean;
  minPasswordLength: number;
}

/** Today's AI allowance for the signed-in person. -1 means unlimited. */
export interface Limits {
  analysesLeft: number;
  analysesPerDay: number;
  questionsLeft: number;
  questionsPerDay: number;
}

export const GITHUB_URL = 'https://github.com/Shreya4119/mentalist';

/**
 * How this copy of Mentalist is hosted. Locally: no sign-up, no limits, and
 * nothing in the UI changes. On the public server: people sign up, and each
 * has a small daily allowance of report analyses and questions.
 */
@Injectable({ providedIn: 'root' })
export class HostingService {
  private readonly http = inject(HttpClient);

  readonly config = signal<PublicConfig | null>(null);
  readonly limits = signal<Limits | null>(null);

  readonly publicMode = computed(() => this.config()?.publicMode ?? false);
  readonly signupEnabled = computed(() => this.config()?.signupEnabled ?? false);
  /** True when the person has a daily analysis allowance (not unlimited). */
  readonly analysesLimited = computed(() => (this.limits()?.analysesPerDay ?? -1) > 0);
  readonly questionsLimited = computed(() => (this.limits()?.questionsPerDay ?? -1) > 0);

  private configRequested = false;

  loadConfig(): void {
    if (this.configRequested) return;
    this.configRequested = true;
    this.http.get<PublicConfig>('/api/auth/config').subscribe({
      next: (c) => this.config.set(c),
      error: () => this.config.set({ publicMode: false, signupEnabled: false, minPasswordLength: 10 }),
    });
  }

  refreshLimits(): void {
    this.http.get<Limits>('/api/limits').subscribe({
      next: (l) => this.limits.set(l),
      error: () => {
        /* keep the last known limits */
      },
    });
  }

  clearLimits(): void {
    this.limits.set(null);
  }
}

/** The server's own explanation for a refused request, when it gave one. */
export function serverMessage(err: unknown): string | null {
  const body = (err as { error?: { message?: unknown } } | null)?.error;
  return typeof body?.message === 'string' && body.message.trim() ? body.message : null;
}

/** "1 analysis" / "3 analyses" style counts. */
export function plural(n: number, one: string, many: string): string {
  return `${n} ${n === 1 ? one : many}`;
}
