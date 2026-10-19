import { Component, computed, input } from '@angular/core';
import { AREA_WEIGHTS } from './glossary';
import { VisualKey } from './lessons';

type Pt = [number, number];

/** Polyline path through points. */
function line(points: Pt[]): string {
  return points.map(([x, y], i) => `${i ? 'L' : 'M'}${x.toFixed(1)},${y.toFixed(1)}`).join(' ');
}

/** Maps a series onto a plot box. */
function plot(values: number[], x0: number, x1: number, y0: number, y1: number, min: number, max: number): Pt[] {
  return values.map((v, i) => [x0 + ((x1 - x0) * i) / (values.length - 1), y0 - ((v - min) / (max - min)) * (y0 - y1)]);
}

/** Donut slice between two angles (radians, 0 = 12 o'clock, clockwise). */
function arc(cx: number, cy: number, r0: number, r1: number, a0: number, a1: number): string {
  const p = (r: number, a: number) => `${(cx + r * Math.sin(a)).toFixed(2)},${(cy - r * Math.cos(a)).toFixed(2)}`;
  const large = a1 - a0 > Math.PI ? 1 : 0;
  return `M${p(r1, a0)} A${r1},${r1} 0 ${large} 1 ${p(r1, a1)} L${p(r0, a1)} A${r0},${r0} 0 ${large} 0 ${p(r0, a0)} Z`;
}

const range = (n: number) => Array.from({ length: n }, (_, i) => i);
const euro = (v: number) => '€' + Math.round(v).toLocaleString('en-US');

/** Worked examples behind the line charts, computed rather than drawn by hand. */
const GEO = (() => {
  const years15 = range(16);
  const g10 = years15.map((t) => 100 * 1.1 ** t);
  const g5 = years15.map((t) => 100 * 1.05 ** t);

  const years30 = range(31);
  const paidIn = years30.map((t) => 2400 * t);
  const value = years30.map((t) => (200 * (1.005 ** (12 * t) - 1)) / 0.005);

  const years10 = range(11);
  const target = years10.map((t) => 40000 * 1.02 ** t);

  const lowFee = years30.map((t) => 10000 * 1.058 ** t);
  const highFee = years30.map((t) => 10000 * 1.045 ** t);

  const bizValue = years10.map((t) => 100 * 1.07 ** t);
  const price = bizValue.map((v, t) => v * (1 + 0.22 * Math.sin(1.4 * t + 0.6)));

  const donutA = [0.6, 0.1, 0.1, 0.1, 0.1];
  let a = 0;
  const sliceGap = 0.03;
  const concentrated = donutA.map((share) => {
    const d = arc(95, 100, 38, 62, a + sliceGap / 2, a + share * 2 * Math.PI - sliceGap / 2);
    a += share * 2 * Math.PI;
    return d;
  });
  const spread = range(30).map((i) => arc(265, 100, 38, 62, (i * 2 * Math.PI) / 30 + 0.02, ((i + 1) * 2 * Math.PI) / 30 - 0.02));

  return {
    g10: line(plot(g10, 40, 300, 185, 25, 0, 450)), g10End: g10[15], g5: line(plot(g5, 40, 300, 185, 25, 0, 450)), g5End: g5[15],
    paidIn: line(plot(paidIn, 40, 300, 185, 25, 0, 210000)),
    value: line(plot(value, 40, 300, 185, 25, 0, 210000)),
    valueArea: line(plot(value, 40, 300, 185, 25, 0, 210000)) + ' L300,185 L40,185 Z',
    valueEnd: value[30], paidInEnd: paidIn[30],
    target: line(plot(target, 40, 300, 180, 30, 38000, 50000)), targetAt4: target[4], targetEnd: target[10],
    targetDot: plot(target, 40, 300, 180, 30, 38000, 50000)[4],
    lowFee: line(plot(lowFee, 40, 300, 185, 25, 0, 60000)), lowFeeEnd: lowFee[30],
    highFee: line(plot(highFee, 40, 300, 185, 25, 0, 60000)), highFeeEnd: highFee[30],
    lowFeeY: plot(lowFee, 40, 300, 185, 25, 0, 60000)[30][1], highFeeY: plot(highFee, 40, 300, 185, 25, 0, 60000)[30][1],
    bizValue: line(plot(bizValue, 30, 300, 180, 30, 50, 250)), price: line(plot(price, 30, 300, 180, 30, 50, 250)),
    bizValueEndY: plot(bizValue, 30, 300, 180, 30, 50, 250)[10][1], priceEndY: plot(price, 30, 300, 180, 30, 50, 250)[10][1],
    concentrated, spread,
  };
})();

const AREAS = ['PROFITABILITY', 'LIQUIDITY', 'LEVERAGE', 'CASH_FLOW', 'GROWTH', 'EFFICIENCY'].map((k, i) => ({
  key: k, label: AREA_WEIGHTS[k].area, weight: AREA_WEIGHTS[k].weight, y: 26 + i * 29,
}));

const LABELS: Record<VisualKey, string> = {
  'flow-analysis': 'Diagram: the business, its reports, ratios, then your own judgement.',
  'three-statements': 'Diagram of the three statements: income statement, balance sheet and cash flow statement.',
  'balance-blocks': 'Balance sheet blocks: 200 of assets equal 120 of liabilities plus 80 of equity.',
  'four-steps': 'Four steps: health score, summary and red flags, key ratios, ask questions.',
  'price-vs-value': 'Line chart: business value grows steadily while the share price swings above and below it.',
  'score-gauge': 'Score scale from 0 to 100: Weak below 35, Watch 35 to 54, Stable 55 to 74, Strong 75 and above. Example marker at 68.',
  'area-weights': 'Bar chart of area weights: profitability 25%, liquidity, leverage and cash flow 20% each, growth 10%, efficiency 5%.',
  'traffic-light': 'Healthy adds 100 points, Caution 60 and Concern 20.',
  'margin-waterfall': 'Waterfall: sales 100, minus costs 85 gives operating income 15, minus interest 3 and tax 2 gives net income 10.',
  'roe-roa': 'Assets of 200 funded by 120 of liabilities and 80 of equity, producing profit of 10.',
  'liquidity': 'Current assets of 120 (cash 30, receivables 40, stock 50) compared with 80 of bills due within a year.',
  'debt-equity': 'Funding of 200: borrowings 48, other liabilities 52, equity 100.',
  'coverage': 'Operating income of 50 split into five blocks of 10, each the size of the interest bill.',
  'profit-vs-cash': 'Bar chart: net income 10, operating cash flow 12.',
  'fcf': 'Waterfall: operating cash flow 12 minus capital expenditure 5 leaves free cash flow of 7.',
  'turnover': 'Bars: assets 200 produce sales of 300.',
  'growth-bars': 'Bars indexed to last year equals 100: revenue 110, net income 125, equity 110.',
  'doubling': 'Line chart: 100 growing 10% a year reaches about 418 after 15 years; at 5% about 208.',
  'group-tree': 'A parent company with three subsidiaries; consolidated covers the whole group, standalone only the parent.',
  'units': 'Table: 1 lakh is 100,000, 1 crore is 10 million, 1 billion is 1,000 million.',
  'checklist': 'Examples of strengths and red flags.',
  'auditor': 'Auditor opinions from best to worst: unmodified, qualified, adverse, disclaimer.',
  'data-quality': '12 of 15 key figures found in the report.',
  'axis-trick': 'Two bar charts of 98 and 102: with the axis starting at 95 the gap looks huge, starting at 0 it looks small.',
  'outlook': 'Bar chart of promised versus delivered growth: 2023 8% vs 6%, 2024 8% vs 8%, 2025 10% vs 7%.',
  'emergency-fund': 'Eight month blocks, four filled: savings cover 4 months, in the Caution zone of 3 to 6 months.',
  'insurance': 'Insurance checklist: liability and health covered, disability missing, term life only with dependants.',
  'income-split': 'Income of 3,000 split: housing 30%, essentials 30%, debt repayments 8%, savings 20%, other 12%.',
  'compounding': 'Line chart: paying in 200 a month for 30 years totals 72,000; at 6% a year it grows to about 200,900.',
  'goals': 'Three goals: emergency fund and car on track, home deposit projected at 78% of target.',
  'buckets': 'Three buckets: safe up to 3 years at 2%, balanced 3 to 7 years at 4%, growth over 7 years at 6%.',
  'inflation': 'Line chart: a 40,000 target grows to about 43,300 after 4 years and 48,760 after 10 years at 2% inflation.',
  'fee-drag': 'Line chart: 10,000 over 30 years grows to about 54,300 with 0.2% fees and 37,500 with 1.5% fees.',
  'concentration': 'Two donuts: one company is 60% of a portfolio, versus a broad fund spread across many companies.',
  'ladder': 'Priority ladder of nine steps; the first two are done and the third is next.',
};

/**
 * Explanatory illustrations for the Learn slides. Hand-built SVG so they follow
 * the app's themes (colours come from CSS variables) and need no chart library.
 * Text always uses text colours; colour only marks identity or status.
 */
@Component({
  selector: 'app-learn-visual',
  templateUrl: './learn-visual.html',
  styleUrl: './learn-visual.css',
})
export class LearnVisual {
  readonly kind = input.required<VisualKey>();
  readonly highlight = input<string | undefined>(undefined);

  protected readonly label = computed(() => LABELS[this.kind()]);
  protected readonly g = GEO;
  protected readonly areas = AREAS;
  protected readonly range = range;
  protected readonly euro = euro;

  /** True when something else on the picture is highlighted, so this part steps back. */
  protected dim(...keys: string[]): boolean {
    const h = this.highlight();
    return !!h && !keys.includes(h);
  }

  protected on(...keys: string[]): boolean {
    const h = this.highlight();
    return !!h && keys.includes(h);
  }
}
