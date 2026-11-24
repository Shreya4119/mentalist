import { DatePipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { AuthService } from '../auth/auth.service';
import { serverMessage } from '../core/hosting.service';
import { InfoTip } from '../reports/learn/info-tip';
import { CompaniesService, CompanyView, HistoryYear, Metric, money, pct, times } from './companies.service';
import { YearBars, YearSeries } from './year-bars';

/** One company over several years: trends, warning-sign scores and where every figure came from. */
@Component({
  selector: 'app-company-page',
  imports: [RouterLink, DatePipe, InfoTip, YearBars],
  templateUrl: './company-page.html',
  styleUrl: './companies.css',
})
export class CompanyPage implements OnInit {
  private readonly route = inject(ActivatedRoute);
  private readonly companies = inject(CompaniesService);
  protected readonly auth = inject(AuthService);
  protected readonly money = money;
  protected readonly pct = pct;
  protected readonly times = times;

  readonly view = signal<CompanyView | null>(null);
  readonly error = signal<string | null>(null);
  readonly refreshing = signal(false);

  readonly years = computed(() => this.view()?.history.years ?? []);
  readonly yearLabels = computed(() => this.years().map((y) => y.periodEnd.slice(0, 4)));
  readonly currency = computed(() => this.years().at(-1)?.currency ?? null);
  readonly latestPiotroski = computed(() => this.view()?.history.piotroski.at(-1) ?? null);
  readonly latestAltman = computed(() => this.view()?.history.altman.at(-1) ?? null);
  readonly latestBeneish = computed(() => this.view()?.history.beneish.at(-1) ?? null);
  readonly moneyFormat = computed(() => (v: number | null) => money(v, this.currency()));
  readonly pctFormat = (v: number | null) => pct(v);

  readonly tableRows: { metric: Metric; label: string }[] = [
    { metric: 'REVENUE', label: 'Revenue' },
    { metric: 'GROSS_PROFIT', label: 'Gross profit' },
    { metric: 'OPERATING_INCOME', label: 'Operating income' },
    { metric: 'NET_INCOME', label: 'Net income' },
    { metric: 'OPERATING_CASH_FLOW', label: 'Operating cash flow' },
    { metric: 'CAPEX', label: 'Capital expenditure' },
    { metric: 'TOTAL_ASSETS', label: 'Total assets' },
    { metric: 'TOTAL_LIABILITIES', label: 'Total liabilities' },
    { metric: 'NONCURRENT_BORROWINGS', label: 'Long-term borrowings' },
    { metric: 'EQUITY', label: 'Shareholders’ equity' },
    { metric: 'CASH', label: 'Cash and equivalents' },
  ];

  series(metric: Metric, name: string): YearSeries {
    return { name, values: this.years().map((y) => y.figures[metric] ?? null) };
  }

  ratioSeries(key: string, name: string): YearSeries {
    return { name, values: this.years().map((y) => y.ratios[key] ?? null) };
  }

  conceptOf(year: HistoryYear, metric: Metric): string {
    const src = this.view()?.sources.find((s) => s.periodEnd === year.periodEnd);
    return src?.concepts[metric] ?? '';
  }

  growthLine(metric: Metric, label: string): string {
    const ys = this.years();
    if (ys.length < 2) return '';
    const last = ys.at(-1)!.figures[metric];
    const prev = ys.at(-2)!.figures[metric];
    if (last == null) return '';
    const g = prev ? ` (${last >= prev ? '+' : ''}${pct((last - prev) / Math.abs(prev))} on the year before)` : '';
    return `${label} ${ys.at(-1)!.periodEnd.slice(0, 4)}: ${money(last, this.currency())}${g}`;
  }

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    const lei = this.route.snapshot.paramMap.get('lei') ?? '';
    this.companies.get(lei).subscribe({
      next: (v) => this.view.set(v),
      error: (err) => this.error.set(serverMessage(err) ?? 'This company could not be loaded.'),
    });
  }

  refresh(): void {
    const v = this.view();
    if (!v || this.refreshing()) return;
    this.refreshing.set(true);
    this.companies.import(v.company.lei, true).subscribe({
      next: (fresh) => {
        this.view.set(fresh);
        this.refreshing.set(false);
      },
      error: (err) => {
        this.refreshing.set(false);
        this.error.set(serverMessage(err) ?? 'Refreshing failed.');
      },
    });
  }
}
