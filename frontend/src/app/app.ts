import { Component, ElementRef, HostListener, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { filter, map } from 'rxjs';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { AuthService } from './auth/auth.service';
import { THEME_OPTIONS, Theme, ThemeService } from './theme/theme.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  templateUrl: './app.html',
  styleUrl: './app.css',
})
export class App {
  protected readonly auth = inject(AuthService);
  protected readonly themeService = inject(ThemeService);
  private readonly router = inject(Router);
  private readonly host = inject(ElementRef<HTMLElement>);

  protected readonly themeOptions = THEME_OPTIONS;
  protected readonly themePickerOpen = signal(false);

  private readonly url = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects),
    ),
    { initialValue: this.router.url },
  );

  /** "Financial reports" is active on /reports/** except the Learn page, which has its own tab. */
  protected onReports(): boolean {
    const path = this.url().split(/[?#]/)[0];
    return path.startsWith('/reports') && !path.startsWith('/reports/learn');
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }

  toggleThemePicker(): void {
    this.themePickerOpen.update((open) => !open);
  }

  @HostListener('document:keydown.escape')
  closeThemePicker(): void {
    this.themePickerOpen.set(false);
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    const picker = (this.host.nativeElement as HTMLElement).querySelector('.theme-picker');
    if (this.themePickerOpen() && picker && !picker.contains(event.target as Node)) {
      this.themePickerOpen.set(false);
    }
  }

  selectTheme(theme: Theme): void {
    this.themeService.setTheme(theme);
    this.themePickerOpen.set(false);
  }
}
