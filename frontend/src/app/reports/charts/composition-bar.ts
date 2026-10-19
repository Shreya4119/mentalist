import { Component, computed, input } from '@angular/core';
import { KeyMetric } from '../reports.service';
import { InfoTip } from '../learn/info-tip';

interface Segment {
  label: string;
  value: number;
  display: string;
  css: string;
}

/**
 * "What it owns vs what it owes": two stacked bars on one scale --
 * assets split current / non-current, and the funding side split
 * current liabilities / non-current liabilities / equity. Drawn only when
 * the report gives the figures needed (totals must reconcile within 5%).
 */
@Component({
  selector: 'app-composition-bar',
  imports: [InfoTip],
  template: `
    @if (model(); as m) {
      <figure class="chart">
        <figcaption>
          <span class="chart-title">What it owns vs. how it's funded<app-info-tip term="composition" /></span>
          @if (unit()) {
            <span class="chart-unit">{{ unit() }}</span>
          }
        </figcaption>
        @for (side of m.sides; track side.title) {
          <div class="stack-row">
            <span class="stack-title">{{ side.title }}</span>
            <div class="stack" role="img" [attr.aria-label]="side.title + ': ' + side.aria">
              @for (seg of side.segments; track seg.label) {
                <span class="seg" [class]="'seg ' + seg.css" [style.flex-grow]="seg.value" [title]="seg.label + ': ' + seg.display">
                  @if (seg.value / m.max > 0.14) {
                    <span class="seg-label">{{ seg.label }}</span>
                  }
                </span>
              }
              <span class="seg-spacer" [style.flex-grow]="m.max - side.total"></span>
            </div>
          </div>
        }
        <div class="legend">
          @for (seg of m.legend; track seg.label) {
            <span><i class="swatch" [class]="'swatch ' + seg.css"></i>{{ seg.label }} · {{ seg.display }}</span>
          }
        </div>
      </figure>
    }
  `,
  styleUrl: './charts.css',
})
export class CompositionBar {
  readonly metrics = input.required<KeyMetric[]>();
  readonly unit = input<string | null>(null);

  protected readonly model = computed(() => {
    const get = (k: string) => this.metrics().find((m) => m.key === k);
    const ta = get('totalAssets')?.current;
    const ca = get('currentAssets')?.current;
    const tl = get('totalLiabilities')?.current;
    const cl = get('currentLiabilities')?.current;
    const eq = get('shareholdersEquity')?.current;
    if ([ta, ca, tl, cl, eq].some((v) => v === null || v === undefined) || ta! <= 0 || eq! < 0) {
      return null;
    }
    if (Math.abs(tl! + eq! - ta!) / ta! > 0.05) {
      return null; // figures don't reconcile (e.g. minority interests) -- don't draw a misleading picture
    }
    const fmt = (v: number) => v.toLocaleString('en', { maximumFractionDigits: 0 });
    const assets: Segment[] = [
      { label: 'Current assets', value: ca!, display: fmt(ca!), css: 's1' },
      { label: 'Non-current assets', value: ta! - ca!, display: fmt(ta! - ca!), css: 's3' },
    ];
    const funding: Segment[] = [
      { label: 'Current liabilities', value: cl!, display: fmt(cl!), css: 's2' },
      { label: 'Non-current liabilities', value: tl! - cl!, display: fmt(tl! - cl!), css: 's4' },
      { label: 'Equity', value: eq!, display: fmt(eq!), css: 's5' },
    ];
    const sides = [
      { title: 'Owns (assets)', segments: assets, total: ta! },
      { title: 'Funded by', segments: funding, total: tl! + eq! },
    ].map((s) => ({ ...s, aria: s.segments.map((g) => `${g.label} ${g.display}`).join(', ') }));
    return { sides, max: Math.max(ta!, tl! + eq!), legend: [...assets, ...funding] };
  });
}
