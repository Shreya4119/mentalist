import { Component, computed, input } from '@angular/core';

export interface YearSeries {
  name: string;
  values: (number | null)[];
}

/**
 * Columns per year for one or two series on a shared zero-based scale
 * (negative values hang below the zero line). HTML/CSS rather than SVG so
 * labels stay readable on phones; a hidden table serves screen readers.
 */
@Component({
  selector: 'app-year-bars',
  template: `
    <figure class="ybars">
      <figcaption>
        <span class="title">{{ title() }}</span>
        @if (series().length > 1) {
          <span class="legend" aria-hidden="true">
            @for (s of series(); track s.name; let i = $index) {
              <span><i class="swatch" [class]="'swatch s' + (i + 1)"></i>{{ s.name }}</span>
            }
          </span>
        }
      </figcaption>
      <div class="plot" aria-hidden="true">
        <div class="cols">
          @for (col of columns(); track col.label) {
            <div class="col">
              <div class="area">
                @for (b of col.bars; track $index; let i = $index) {
                  <div class="bar-slot">
                    @if (b.value !== null) {
                      <span class="bar" [class]="'bar s' + (i + 1) + (b.value < 0 ? ' neg' : '')"
                            [style.bottom.%]="b.value < 0 ? zero() - b.height : zero()"
                            [style.height.%]="b.height" [title]="b.display"></span>
                    }
                  </div>
                }
                <span class="zero" [style.bottom.%]="zero()"></span>
              </div>
              <span class="xlabel">{{ col.label }}</span>
            </div>
          }
        </div>
      </div>
      <table class="visually-hidden">
        <caption>{{ title() }}</caption>
        <tr><th>Year</th>@for (s of series(); track s.name) { <th>{{ s.name }}</th> }</tr>
        @for (col of columns(); track col.label) {
          <tr><td>{{ col.label }}</td>@for (b of col.bars; track $index) { <td>{{ b.display }}</td> }</tr>
        }
      </table>
      @if (latest()) {
        <p class="latest">{{ latest() }}</p>
      }
    </figure>
  `,
  styles: `
    .ybars { margin: 0; display: grid; gap: 0.5rem; }
    figcaption { display: flex; flex-wrap: wrap; justify-content: space-between; gap: 0.4rem; align-items: baseline; }
    .title { font-weight: 700; font-size: 0.95rem; }
    .legend { display: flex; gap: 0.8rem; font-size: 0.78rem; color: var(--color-text-muted); }
    .swatch { display: inline-block; width: 10px; height: 10px; border-radius: 3px; margin-right: 0.3rem; vertical-align: -1px; }
    .s1 { background: var(--color-primary); }
    .s2 { background: var(--color-text-faint); }
    .plot { height: 150px; }
    .cols { display: flex; gap: 6px; height: 100%; }
    .col { flex: 1; display: grid; grid-template-rows: 1fr auto; min-width: 0; }
    .area { position: relative; display: flex; gap: 2px; align-items: stretch; }
    .bar-slot { position: relative; flex: 1; }
    .bar { position: absolute; left: 0; right: 0; border-radius: 3px 3px 0 0; }
    .bar.neg { border-radius: 0 0 3px 3px; }
    .zero { position: absolute; left: 0; right: 0; height: 1px; background: var(--color-border-strong); }
    .xlabel { text-align: center; font-size: 0.72rem; color: var(--color-text-muted); padding-top: 0.25rem; }
    .latest { margin: 0; font-size: 0.82rem; color: var(--color-text-muted); }
    .visually-hidden { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); white-space: nowrap; }
  `,
})
export class YearBars {
  readonly title = input.required<string>();
  readonly years = input.required<string[]>();
  readonly series = input.required<YearSeries[]>();
  readonly format = input<(v: number | null) => string>((v) => (v === null ? '—' : String(v)));
  /** One line under the chart, e.g. the latest value or growth. */
  readonly latest = input<string>('');

  private readonly range = computed(() => {
    const all = this.series().flatMap((s) => s.values).filter((v): v is number => v !== null);
    const max = Math.max(0, ...all);
    const min = Math.min(0, ...all);
    return { max, min, span: max - min || 1 };
  });

  /** Height of the zero line, in % from the bottom. */
  readonly zero = computed(() => (-this.range().min / this.range().span) * 100);

  readonly columns = computed(() =>
    this.years().map((label, i) => ({
      label,
      bars: this.series().map((s) => {
        const value = s.values[i] ?? null;
        return {
          value,
          height: value === null ? 0 : (Math.abs(value) / this.range().span) * 100,
          display: this.format()(value),
        };
      }),
    })),
  );
}
