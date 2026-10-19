import { DatePipe, PercentPipe } from '@angular/common';
import { Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription, interval } from 'rxjs';
import { AssessmentChip } from '../charts/assessment-chip';
import { CompositionBar } from '../charts/composition-bar';
import { BarRow, MetricBars } from '../charts/metric-bars';
import { ScoreBand } from '../charts/score-band';
import { ReportChat } from './report-chat';
import { InfoTip } from '../learn/info-tip';
import { explainForCompany } from '../learn/glossary';
import {
  CATEGORY_LABELS,
  KeyMetric,
  ProviderInfo,
  Ratio,
  RatioCategory,
  ReportDetail as Detail,
  ReportsService,
  isInFlight,
} from '../reports.service';

const PERFORMANCE_KEYS = ['revenue', 'operatingIncome', 'netIncome', 'operatingCashFlow'];
const POSITION_KEYS = ['totalAssets', 'totalLiabilities', 'shareholdersEquity', 'totalDebt', 'cashAndEquivalents'];

@Component({
  selector: 'app-report-detail',
  imports: [DatePipe, PercentPipe, FormsModule, RouterLink, AssessmentChip, CompositionBar, MetricBars, ScoreBand, ReportChat, InfoTip],
  templateUrl: './report-detail.html',
  styleUrl: '../reports.css',
})
export class ReportDetailPage implements OnInit, OnDestroy {
  private readonly reports = inject(ReportsService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly detail = signal<Detail | null>(null);
  readonly loadError = signal<string | null>(null);
  readonly actionError = signal<string | null>(null);
  readonly providers = signal<ProviderInfo[]>([]);
  readonly rerunProvider = signal<string | null>(null);
  readonly confirmingDelete = signal(false);
  readonly busy = signal(false);
  readonly duplicate = signal(false);
  readonly now = signal(Date.now());

  /** First-time guide; per-viewer convenience, so browser storage is fine (and optional). */
  readonly showGuide = signal(ReportDetailPage.readGuidePref());

  private static readGuidePref(): boolean {
    try {
      return localStorage.getItem('mentalist_guide_hidden') !== '1';
    } catch {
      return true;
    }
  }

  dismissGuide(): void {
    this.showGuide.set(false);
    try {
      localStorage.setItem('mentalist_guide_hidden', '1');
    } catch {
      /* storage unavailable: hide for this visit only */
    }
  }

  /** Plain-English sentence about this company's value for a ratio (null when not computed). */
  meaning(r: Ratio): string | null {
    const a = this.analysis();
    return explainForCompany(r.key, r.value, r.display, r.assessment,
      a?.extraction.companyName ?? '', a?.extraction.currency ?? null);
  }

  private id = '';
  private poller: Subscription | null = null;
  private scrollToAskPending = false;

  /** Where "back" goes: the comparison this page was opened from, or the reports list. */
  readonly backLink = signal<{ label: string; link: string[]; query: Record<string, string> | null }>({
    label: 'All reports',
    link: ['/reports'],
    query: null,
  });

  readonly report = computed(() => this.detail()?.report ?? null);
  readonly analysis = computed(() => this.detail()?.analysis ?? null);
  readonly inFlight = computed(() => {
    const r = this.report();
    return !!r && isInFlight(r);
  });
  readonly configuredProviders = computed(() => this.providers().filter((p) => p.configured));
  readonly elapsed = computed(() => {
    const r = this.report();
    if (!r?.analysisRequestedAt) {
      return '';
    }
    const s = Math.max(0, Math.round((this.now() - Date.parse(r.analysisRequestedAt)) / 1000));
    return s < 60 ? `${s}s` : `${Math.floor(s / 60)}m ${s % 60}s`;
  });

  readonly ratioGroups = computed(() => {
    const a = this.analysis();
    if (!a) {
      return [];
    }
    const order: RatioCategory[] = ['PROFITABILITY', 'LIQUIDITY', 'LEVERAGE', 'CASH_FLOW', 'EFFICIENCY', 'GROWTH'];
    return order
      .map((c) => ({ category: c, label: CATEGORY_LABELS[c], ratios: a.ratios.filter((r) => r.category === c) }))
      .filter((g) => g.ratios.length > 0);
  });

  readonly performanceRows = computed(() => this.rows(PERFORMANCE_KEYS));
  readonly positionRows = computed(() => this.rows(POSITION_KEYS));
  readonly currentLabel = computed(() => this.analysis()?.extraction.reportingPeriod || 'Current period');
  readonly priorLabel = computed(() => this.analysis()?.extraction.priorPeriod || 'Prior period');
  readonly title = computed(() => this.analysis()?.extraction.companyName || this.report()?.filename || 'Report');

  ngOnInit(): void {
    this.id = this.route.snapshot.paramMap.get('id') ?? '';
    const q = this.route.snapshot.queryParamMap;
    this.duplicate.set(q.get('duplicate') === '1');
    if (q.get('from') === 'compare' && q.get('a') && q.get('b')) {
      this.backLink.set({ label: 'Back to comparison', link: ['/reports/compare'], query: { a: q.get('a')!, b: q.get('b')! } });
    }
    this.scrollToAskPending = this.route.snapshot.fragment === 'ask';
    this.reports.providers().subscribe((p) => {
      this.providers.set(p);
      this.rerunProvider.set(p.find((x) => x.isDefault && x.configured)?.id ?? p.find((x) => x.configured)?.id ?? null);
    });
    this.load();
    this.poller = interval(3000).subscribe(() => {
      this.now.set(Date.now());
      const r = this.report();
      if (r && (isInFlight(r) || r.documentStatus === 'QUEUED' || r.documentStatus === 'PROCESSING')) {
        this.load();
      }
    });
  }

  ngOnDestroy(): void {
    this.poller?.unsubscribe();
  }

  load(): void {
    this.reports.get(this.id).subscribe({
      next: (d) => {
        this.detail.set(d);
        this.loadError.set(null);
        if (this.scrollToAskPending && d.report.chatReady) {
          this.scrollToAskPending = false;
          setTimeout(() => this.scrollToAsk(), 50);
        }
      },
      error: (err) => this.loadError.set(err.status === 404 ? 'This report no longer exists.' : 'Could not load this report.'),
    });
  }

  reanalyze(): void {
    this.actionError.set(null);
    this.busy.set(true);
    this.reports.reanalyze(this.id, this.rerunProvider()).subscribe({
      next: () => {
        this.busy.set(false);
        this.load();
      },
      error: (err) => {
        this.busy.set(false);
        this.actionError.set(err.error?.message ?? err.error?.detail ?? 'Could not start the analysis.');
      },
    });
  }

  remove(): void {
    this.busy.set(true);
    this.reports.delete(this.id).subscribe({
      next: () => this.router.navigate(['/reports']),
      error: () => {
        this.busy.set(false);
        this.confirmingDelete.set(false);
        this.actionError.set('Could not delete this report.');
      },
    });
  }

  /** Jumps to the inline "Ask about this report" panel and focuses its question box. */
  scrollToAsk(): void {
    const panel = document.getElementById('ask');
    panel?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    (panel?.querySelector('input') as HTMLInputElement | null)?.focus({ preventScroll: true });
  }

  trackRatio(_: number, r: Ratio): string {
    return r.key;
  }

  changeClass(m: KeyMetric): string {
    if (m.changePct === null) {
      return '';
    }
    const upIsBad = ['totalLiabilities', 'currentLiabilities', 'totalDebt', 'interestExpense'].includes(m.key);
    return (m.changePct > 0) !== upIsBad ? 'up-good' : 'up-bad';
  }

  private rows(keys: string[]): BarRow[] {
    const metrics = this.analysis()?.metrics ?? [];
    return keys
      .map((k) => metrics.find((m) => m.key === k))
      .filter((m): m is KeyMetric => !!m && m.current !== null)
      .map((m) => ({ label: m.label, current: m.current, prior: m.prior, currentDisplay: m.currentDisplay, priorDisplay: m.priorDisplay }));
  }
}
