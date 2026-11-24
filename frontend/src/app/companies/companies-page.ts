import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { of, switchMap } from 'rxjs';
import { serverMessage } from '../core/hosting.service';
import { InfoTip } from '../reports/learn/info-tip';
import { CompaniesService, CompanySummary, Country, DirectoryItem, SearchHit } from './companies.service';

/** Legal forms and filler words that people leave out when they type a company name. */
const NOISE = new Set(['nv', 'n.v.', 'se', 'sa', 's.a.', 'ag', 'plc', 'spa', 's.p.a.', 'ab', 'asa', 'oyj', 'as', 'a/s', 'bv', 'b.v.',
  'gmbh', 'kgaa', 'co', 'kg', 'ltd', 'limited', 'holding', 'holdings', 'group', 'groep', 'the', 'koninklijke', 'royal', '&', 'and']);

/** Lower-case words of a company name without legal forms, e.g. "Koninklijke Philips N.V." -> ["philips"]. */
export function nameWords(name: string): string[] {
  return name.toLowerCase().replace(/[,()]/g, ' ').split(/\s+/).filter((w) => w && !NOISE.has(w));
}

/**
 * The company the user most likely meant, or null when the choice is unclear.
 * One result wins; otherwise a single result whose core name equals the query,
 * then a single result containing every typed word.
 */
export function bestMatch<T extends { name: string }>(query: string, hits: T[]): T | null {
  if (hits.length === 1) return hits[0];
  const q = nameWords(query);
  if (q.length === 0) return null;
  const exact = hits.filter((h) => nameWords(h.name).join(' ') === q.join(' '));
  if (exact.length === 1) return exact[0];
  const all = hits.filter((h) => { const w = nameWords(h.name); return q.every((x) => w.includes(x)); });
  return all.length === 1 ? all[0] : null;
}

/** Type a company name and get its analysis from the official annual filings. */
@Component({
  selector: 'app-companies-page',
  imports: [FormsModule, RouterLink, InfoTip],
  templateUrl: './companies-page.html',
  styleUrl: './companies.css',
})
export class CompaniesPage implements OnInit {
  private readonly companies = inject(CompaniesService);
  private readonly router = inject(Router);

  readonly query = signal('');
  readonly hits = signal<SearchHit[] | null>(null);
  readonly searching = signal(false);
  readonly importing = signal<string | null>(null);
  readonly error = signal<string | null>(null);
  readonly imported = signal<CompanySummary[]>([]);
  readonly loading = signal(true);

  // ---- browse by country
  private static readonly PAGE = 50;
  readonly countries = signal<Country[]>([]);
  readonly country = signal('');
  readonly directory = signal<DirectoryItem[] | null>(null);
  readonly directoryLoading = signal(false);
  readonly directoryError = signal<string | null>(null);
  readonly filter = signal('');
  readonly shown = signal(CompaniesPage.PAGE);
  readonly filtered = computed(() => {
    const words = this.filter().toLowerCase().split(/\s+/).filter(Boolean);
    const all = this.directory() ?? [];
    return words.length ? all.filter((c) => words.every((w) => c.name.toLowerCase().includes(w))) : all;
  });
  readonly visible = computed(() => this.filtered().slice(0, this.shown()));
  readonly countryName = computed(() => this.countries().find((c) => c.code === this.country())?.name ?? '');

  chooseCountry(code: string): void {
    this.country.set(code);
    this.filter.set('');
    this.shown.set(CompaniesPage.PAGE);
    this.directory.set(null);
    this.directoryError.set(null);
    if (!code) return;
    this.directoryLoading.set(true);
    this.companies.directory(code).subscribe({
      next: (list) => {
        if (this.country() !== code) return;
        this.directory.set(list);
        this.directoryLoading.set(false);
      },
      error: (err) => {
        if (this.country() !== code) return;
        this.directoryLoading.set(false);
        this.directoryError.set(serverMessage(err) ?? 'The company list could not be loaded. Please try again.');
      },
    });
  }

  setFilter(v: string): void {
    this.filter.set(v);
    this.shown.set(CompaniesPage.PAGE);
  }

  showMore(): void {
    this.shown.update((n) => n + CompaniesPage.PAGE);
  }

  ngOnInit(): void {
    this.companies.countries().subscribe({ next: (list) => this.countries.set(list), error: () => this.countries.set([]) });
    this.companies.list().subscribe({
      next: (list) => {
        this.imported.set(list);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  search(): void {
    const q = this.query().trim();
    if (q.length < 2 || this.searching()) {
      this.error.set(q.length < 2 ? 'Type at least 2 letters of the company name.' : null);
      return;
    }
    this.error.set(null);
    this.searching.set(true);
    this.hits.set(null);
    // The filings index matches the name as typed, so search for the core name
    // ("Philips", not "Philips N.V."); if that finds nothing, try its longest word.
    const words = nameWords(q);
    const core = words.join(' ').length >= 2 ? words.join(' ') : q;
    const longest = [...words].sort((a, b) => b.length - a.length)[0];
    this.companies.search(core).pipe(
      switchMap((hits) => (hits.length === 0 && words.length > 1 && longest.length >= 2 ? this.companies.search(longest) : of(hits))),
    ).subscribe({
      next: (hits) => {
        this.searching.set(false);
        const best = bestMatch(q, hits);
        if (best) {
          this.open(best);
        } else {
          this.hits.set(hits);
        }
      },
      error: (err) => {
        this.searching.set(false);
        this.error.set(serverMessage(err) ?? 'Search failed. Please try again.');
      },
    });
  }

  open(hit: SearchHit): void {
    if (hit.imported) {
      this.router.navigate(['/companies', hit.lei]);
      return;
    }
    if (this.importing()) return;
    this.error.set(null);
    this.importing.set(hit.lei);
    this.companies.import(hit.lei).subscribe({
      next: () => {
        this.importing.set(null);
        this.router.navigate(['/companies', hit.lei]);
      },
      error: (err) => {
        this.importing.set(null);
        this.error.set(serverMessage(err) ?? 'Importing the filings failed. Please try again.');
      },
    });
  }

  years(c: CompanySummary): string {
    if (!c.firstYear || !c.lastYear) return 'No years';
    const a = c.firstYear.slice(0, 4);
    const b = c.lastYear.slice(0, 4);
    return a === b ? a : `${a}–${b}`;
  }
}
