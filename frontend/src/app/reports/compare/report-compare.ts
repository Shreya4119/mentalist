import { PercentPipe } from '@angular/common';
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { AssessmentChip } from '../charts/assessment-chip';
import { BarRow, MetricBars } from '../charts/metric-bars';
import { ScoreBand } from '../charts/score-band';
import { InfoTip } from '../learn/info-tip';
import { Comparison, ComparisonSide, Direction, ReportsService } from '../reports.service';

const CHART_KEYS = ['revenue', 'operatingIncome', 'netIncome', 'totalAssets', 'totalLiabilities', 'shareholdersEquity', 'operatingCashFlow'];

@Component({
  selector: 'app-report-compare',
  imports: [PercentPipe, RouterLink, AssessmentChip, MetricBars, ScoreBand, InfoTip],
  templateUrl: './report-compare.html',
  styleUrl: '../reports.css',
})
export class ReportComparePage implements OnInit {
  private readonly reports = inject(ReportsService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly data = signal<Comparison | null>(null);
  readonly error = signal<string | null>(null);

  readonly chartRows = computed<BarRow[]>(() => {
    const c = this.data();
    if (!c || c.warnings.some((w) => w.includes('currencies') || w.includes('units'))) {
      return []; // absolute amounts aren't comparable -- don't chart them side by side
    }
    return CHART_KEYS.map((k) => c.metrics.find((m) => m.key === k))
      .filter((m): m is NonNullable<typeof m> => !!m && (m.a !== null || m.b !== null))
      .map((m) => ({ label: m.label, current: m.b, prior: m.a, currentDisplay: m.bDisplay, priorDisplay: m.aDisplay }));
  });

  ngOnInit(): void {
    this.route.queryParamMap.subscribe((q) => {
      const a = q.get('a');
      const b = q.get('b');
      if (!a || !b) {
        this.error.set('Pick two analysed reports on the Financial reports page to compare them.');
        return;
      }
      this.data.set(null);
      this.error.set(null);
      this.reports.compare(a, b).subscribe({
        next: (c) => this.data.set(c),
        error: (err) =>
          this.error.set(err.error?.message ?? err.error?.detail ?? 'Could not compare these reports. Both must be fully analysed.'),
      });
    });
  }

  swap(): void {
    const c = this.data();
    if (c) {
      this.router.navigate([], { queryParams: { a: c.b.reportId, b: c.a.reportId } });
    }
  }

  name(s: ComparisonSide): string {
    return s.companyName || s.filename;
  }

  sideLabel(s: ComparisonSide, sameCompany: boolean): string {
    return sameCompany ? s.period || s.filename : this.name(s);
  }

  arrow(d: Direction): string {
    return d === 'IMPROVED' ? '▲ Better' : d === 'WORSENED' ? '▼ Worse' : d === 'UNCHANGED' ? '= Same band' : '—';
  }
}
