import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { HostingService, plural, serverMessage } from '../core/hosting.service';

/** The signed-in person's account: today's allowance, and deleting the account with all its data. */
@Component({
  selector: 'app-account',
  imports: [FormsModule, RouterLink],
  templateUrl: './account.html',
  styleUrl: '../about/about.css',
})
export class AccountPage {
  protected readonly auth = inject(AuthService);
  protected readonly hosting = inject(HostingService);
  private readonly router = inject(Router);
  protected readonly plural = plural;

  readonly confirming = signal(false);
  readonly typed = signal('');
  readonly busy = signal(false);
  readonly error = signal<string | null>(null);

  constructor() {
    this.hosting.refreshLimits();
  }

  deleteAccount(): void {
    if (this.busy() || this.typed().trim().toLowerCase() !== (this.auth.username() ?? '').toLowerCase()) return;
    this.busy.set(true);
    this.error.set(null);
    this.auth.deleteAccount().subscribe({
      next: () => this.router.navigate(['/login'], { queryParams: { deleted: 1 } }),
      error: (err) => {
        this.busy.set(false);
        this.error.set(serverMessage(err) ?? 'Could not delete your account. Please try again.');
      },
    });
  }
}
