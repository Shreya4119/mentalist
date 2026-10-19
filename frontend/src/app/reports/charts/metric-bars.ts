import { Component, computed, input, signal } from '@angular/core';

export interface BarRow {
  label: string;
  current: number | null;
  prior: number | null;
  currentDisplay: string;
  priorDisplay: string;
}

/**
 * Grouped horizontal bars: current period vs prior period per line item,
 * on ONE shared zero-based scale (negative values extend left of zero).
 * Built from HTML/CSS rather than SVG so the labels stay readable at every
 * width -- an SVG viewBox shrinks its text along with the chart on phones.
 * Hover dims the other bars; a visually hidden table serves screen readers.
 */
@Component({
  selector: 'app-metric-bars',
  template: `
    <figure class="chart">
      <figcaption>
        <span class="chart-title">{{ title() }}</span>
        @if (unit()) {
          <span class="chart-unit">{{ unit() }}</span>
        }
      </figcaption>
      <div class="legend" aria-hidden="true">
        <span><i class="swatch s1"></i>{{ currentLabel() }}</span>
        @if (hasPrior()) {
          <span><i class="swatch s2"></i>{{ priorLabel() }}</span>
        }
      </div>
      <div class="hbars" aria-hidden="true" [class.dimming]="hovered() !== null">
        @for (row of layout(); track row.label; let i = $index) {
          <div class="hbar-row">
            <span class="hbar-label">{{ row.label }}</span>
            <div class="hbar-track">
              @if (zeroPct() > 0) {
                <span class="hbar-zero" [style.left.%]="zeroPct()"></span>
              }
              @for (bar of row.bars; track bar.series) {
                <div class="hbar-line" (mouseenter)="hovered.set(i + bar.series)" (mouseleave)="hovered.set(null)"
                     [class.hot]="hovered() === i + bar.series" [title]="row.label + ' · ' + bar.name + ': ' + bar.display">
                  <span class="hbar" [class]="'hbar ' + bar.series" [style.left.%]="bar.left" [style.width.%]="bar.width"></span>
                  <span class="hbar-value" [class.neg]="bar.negative"
                        [style.left.%]="bar.negative ? null : bar.left + bar.width"
                        [style.right.%]="bar.negative ? 100 - bar.left : null">{{ bar.display }}</span>
                </div>
              }
            </div>
          </div>
        }
      </div>
      <div class="sr-only">
        <table>
          <caption>{{ title() }}</caption>
          <tr><th>Item</th><th>{{ currentLabel() }}</th><th>{{ priorLabel() }}</th></tr>
          @for (r of rows(); track r.label) {
            <tr><td>{{ r.label }}</td><td>{{ r.currentDisplay }}</td><td>{{ r.priorDisplay }}</td></tr>
          }
        </table>
      </div>
    </figure>
  `,
  styleUrl: './charts.css',
})
export class MetricBars {
  readonly title = input.required<string>();
  readonly unit = input<string | null>(null);
  readonly rows = input.required<BarRow[]>();
  readonly currentLabel = input('Current period');
  readonly priorLabel = input('Prior period');

  protected readonly hovered = signal<string | null>(null);
  protected readonly hasPrior = computed(() => this.rows().some((r) => r.prior !== null));

  /** Bars use the middle 70% of the track; the rest leaves room for value labels on either side. */
  private readonly extent = computed(() => {
    const values = this.rows().flatMap((r) => [r.current, r.prior]).filter((v): v is number => v !== null);
    return { min: Math.min(0, ...values), max: Math.max(0, ...values) };
  });

  private pct(v: number): number {
    const { min, max } = this.extent();
    const left = min < 0 ? 15 : 0;
    const span = max - min || 1;
    return left + ((v - min) / span) * (min < 0 ? 62 : 66);
  }

  /** The caption names the unit, so bar labels drop it to stay short enough for phones. */
  private short(display: string): string {
    const unit = this.unit();
    return unit && display.endsWith(' ' + unit) ? display.slice(0, -unit.length - 1) : display;
  }

  protected readonly zeroPct = computed(() => (this.extent().min < 0 ? this.pct(0) : 0));

  protected readonly layout = computed(() =>
    this.rows().map((r) => {
      const series = [
        { series: 's1', name: this.currentLabel(), v: r.current, display: r.currentDisplay },
        ...(this.hasPrior() ? [{ series: 's2', name: this.priorLabel(), v: r.prior, display: r.priorDisplay }] : []),
      ];
      return {
        label: r.label,
        bars: series.map((s) => {
          const v = s.v ?? 0;
          const a = this.pct(0);
          const b = this.pct(v);
          return {
            series: s.series,
            name: s.name,
            display: s.v === null ? 'n/a' : this.short(s.display),
            left: Math.min(a, b),
            width: s.v === null ? 0 : Math.max(Math.abs(b - a), 0.6),
            negative: v < 0,
          };
        }),
      };
    }),
  );
}
