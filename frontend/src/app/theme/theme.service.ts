import { Injectable, signal } from '@angular/core';

export type Theme = 'warm' | 'ocean' | 'forest' | 'midnight';

export interface ThemeOption {
  id: Theme;
  name: string;
  swatch: string;
}

const STORAGE_KEY = 'mentalist_theme';

export const THEME_OPTIONS: ThemeOption[] = [
  { id: 'warm', name: 'Warm Coral', swatch: '#ff6b57' },
  { id: 'ocean', name: 'Ocean', swatch: '#2b8ab0' },
  { id: 'forest', name: 'Forest', swatch: '#4f8a5b' },
  { id: 'midnight', name: 'Midnight', swatch: '#7c9cff' },
];

const VALID_THEMES = new Set(THEME_OPTIONS.map((t) => t.id));

@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly theme = signal<Theme>(this.loadTheme());

  constructor() {
    this.applyTheme(this.theme());
  }

  setTheme(theme: Theme): void {
    this.theme.set(theme);
    localStorage.setItem(STORAGE_KEY, theme);
    this.applyTheme(theme);
  }

  private loadTheme(): Theme {
    const stored = localStorage.getItem(STORAGE_KEY) ?? localStorage.getItem('hr_rag_theme');
    return stored && VALID_THEMES.has(stored as Theme) ? (stored as Theme) : 'warm';
  }

  private applyTheme(theme: Theme): void {
    if (theme === 'warm') {
      document.documentElement.removeAttribute('data-theme');
    } else {
      document.documentElement.setAttribute('data-theme', theme);
    }
  }
}
