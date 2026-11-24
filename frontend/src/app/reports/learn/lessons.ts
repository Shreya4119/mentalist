/**
 * The "Learn the basics" course: short lessons made of slides. Term slides
 * reuse the glossary text (one source of truth with the ⓘ tips); concept
 * slides add the big ideas that tie the terms together. Every slide can carry
 * an illustration from `learn-visual.ts`, drawn with made-up example numbers.
 */
import { GLOSSARY } from './glossary';

export type VisualKey =
  | 'flow-analysis' | 'three-statements' | 'balance-blocks' | 'four-steps' | 'price-vs-value'
  | 'score-gauge' | 'area-weights' | 'traffic-light'
  | 'margin-waterfall' | 'roe-roa' | 'liquidity' | 'debt-equity' | 'coverage'
  | 'profit-vs-cash' | 'fcf' | 'turnover' | 'growth-bars' | 'doubling'
  | 'group-tree' | 'units' | 'checklist' | 'auditor' | 'data-quality' | 'axis-trick' | 'outlook'
  | 'emergency-fund' | 'insurance' | 'income-split' | 'compounding' | 'goals' | 'buckets'
  | 'inflation' | 'fee-drag' | 'concentration' | 'ladder';

export interface Slide {
  /** Stable id: the glossary key for term slides, so ⓘ links can open them. */
  id: string;
  title: string;
  /** Term slides: the glossary entry shown on this slide. */
  termKey?: string;
  /** Concept slides: short paragraphs or steps. */
  points?: string[];
  /** Concept slides: show points as a numbered list. */
  numbered?: boolean;
  visual?: VisualKey;
  /** Which part of the illustration to emphasise. */
  highlight?: string;
  /** One line under the illustration, e.g. the worked calculation. */
  caption?: string;
  /** The one thing to remember. */
  takeaway?: string;
}

export interface Lesson {
  id: string;
  title: string;
  blurb: string;
  slides: Slide[];
}

const term = (key: string, visual?: VisualKey, highlight?: string, caption?: string, takeaway?: string): Slide => ({
  id: key, title: GLOSSARY[key]?.term ?? key, termKey: key, visual, highlight, caption, takeaway,
});

export const LESSONS: Lesson[] = [
  {
    id: 'start', title: 'Start here', blurb: 'What fundamental analysis is, what is inside an annual report and how to read one in four steps.',
    slides: [
      term('fundamentalAnalysis', 'flow-analysis', undefined, undefined, 'You judge the business first; the share price comes later.'),
      term('annualReport', 'three-statements', undefined, 'Three statements, three questions: did it earn, what does it have, did cash come in?'),
      {
        id: 'start-equation', title: 'The one equation behind every balance sheet', visual: 'balance-blocks',
        points: [
          'Everything a company owns was paid for by someone: either lenders and suppliers (liabilities) or the owners (equity).',
          'So both sides always add up: assets = liabilities + equity. If the company owns 200 and owes 120, the owners’ share is 80.',
        ],
        caption: 'Example: 200 of assets = 120 of liabilities + 80 of equity.',
        takeaway: 'More of the right-hand side in equity means a sturdier company.',
      },
      {
        id: 'start-steps', title: 'Reading a report in four steps', visual: 'four-steps', numbered: true,
        points: [
          'Start with the health score. It sums up 16 checks into one 0–100 number and shows which areas are strong or weak.',
          'Read the plain-language summary, strengths and red flags. They tell the story behind the numbers, with evidence from the report.',
          'Look at the ratios that matter most to you. Tap ⓘ next to any term to see what it means and what good looks like.',
          'Ask questions. Use “Ask about this report” for anything unclear, e.g. “Can this company survive a bad year?”.',
        ],
      },
      {
        id: 'start-caveat', title: 'A healthy business is not always a good buy', visual: 'price-vs-value',
        points: [
          'Fundamental analysis tells you how healthy a business is, not whether its share price is a bargain today.',
          'Prices swing with news and mood; the business underneath changes slowly. Over many years the two tend to meet.',
          'Use it as one input, spread your money across several investments, and consider a licensed adviser for big decisions.',
        ],
        caption: 'Illustration, not real data.',
        takeaway: 'Great company + too high a price can still be a poor investment.',
      },
    ],
  },
  {
    id: 'score', title: 'The health score', blurb: 'How Mentalist turns 16 ratios into one number, and what Healthy, Caution and Concern mean.',
    slides: [
      term('healthScore', 'score-gauge', undefined, 'Example: a score of 68 sits in the Stable band.'),
      {
        id: 'score-weights', title: 'Six areas, weighted by how much they matter', visual: 'area-weights',
        points: [
          'Each ratio belongs to one area. Each area gets its own score, and the overall score is a weighted average.',
          'Profitability counts most (25%). Liquidity, leverage and cash flow count 20% each; growth 10% and efficiency 5%.',
        ],
        takeaway: 'A weak area pulls the score down in proportion to its weight.',
      },
      term('assessment', 'traffic-light'),
      term('PROFITABILITY', 'area-weights', 'PROFITABILITY'),
      term('LIQUIDITY', 'area-weights', 'LIQUIDITY'),
      term('LEVERAGE', 'area-weights', 'LEVERAGE'),
      term('CASH_FLOW', 'area-weights', 'CASH_FLOW'),
      term('EFFICIENCY', 'area-weights', 'EFFICIENCY'),
      term('GROWTH', 'area-weights', 'GROWTH'),
    ],
  },
  {
    id: 'statements', title: 'The three statements', blurb: 'The line items behind every ratio: from revenue down to profit, what is owned, what is owed and where cash went.',
    slides: [
      {
        id: 'statements-income', title: 'The income statement: from sales to profit', visual: 'margin-waterfall',
        points: [
          'Read it top to bottom. Start with everything the company sold, then take off the costs of running it, then interest and tax.',
          'What is left at each step tells a different story: operating income shows the business itself, net income what is left for owners.',
        ],
        caption: 'Example: 100 of sales becomes 15 of operating income and 10 of net income.',
      },
      term('revenue', 'margin-waterfall', 'revenue'),
      term('operatingIncome', 'margin-waterfall', 'operating'),
      term('interestExpense', 'margin-waterfall', 'interest'),
      term('netIncome', 'margin-waterfall', 'net'),
      {
        id: 'statements-balance', title: 'The balance sheet: a snapshot on one day', visual: 'balance-blocks',
        points: [
          'Left: what the company owns. Current assets turn into cash within a year; long-term assets (buildings, machines, software) last longer.',
          'Right: who paid for it. Current liabilities are due within a year, long-term liabilities later, and equity belongs to the owners.',
        ],
      },
      term('totalAssets', 'balance-blocks', 'assets'),
      term('currentAssets', 'liquidity', 'currentAssets'),
      term('cashAndEquivalents', 'liquidity', 'cash'),
      term('inventory', 'liquidity', 'inventory'),
      term('receivables', 'liquidity', 'receivables'),
      term('totalLiabilities', 'balance-blocks', 'liabilities'),
      term('currentLiabilities', 'liquidity', 'bills'),
      term('totalDebt', 'debt-equity', 'debt'),
      term('shareholdersEquity', 'balance-blocks', 'equity'),
      term('operatingCashFlow', 'profit-vs-cash', 'cash'),
      term('capitalExpenditure', 'fcf', 'capex'),
    ],
  },
  {
    id: 'profit', title: 'Profitability', blurb: 'How much of every sale a company keeps, and how hard its money works.',
    slides: [
      term('netMargin', 'margin-waterfall', 'net', 'Net margin = 10 ÷ 100 = 10%.'),
      term('operatingMargin', 'margin-waterfall', 'operating', 'Operating margin = 15 ÷ 100 = 15%.'),
      term('roe', 'roe-roa', 'roe', 'ROE = profit 10 ÷ equity 80 = 12.5%.'),
      term('roa', 'roe-roa', 'roa', 'ROA = profit 10 ÷ assets 200 = 5%.', 'High ROE with low ROA often means a lot of borrowing.'),
    ],
  },
  {
    id: 'liquidity', title: 'Paying the bills', blurb: 'Can the company pay what is due in the next 12 months? Three ratios, from generous to strict.',
    slides: [
      term('currentRatio', 'liquidity', 'currentRatio', 'Current ratio = 120 ÷ 80 = 1.5×.'),
      term('quickRatio', 'liquidity', 'quickRatio', 'Quick ratio = (30 + 40) ÷ 80 = 0.88×. Stock is left out because it may sell slowly.'),
      term('cashRatio', 'liquidity', 'cashRatio', 'Cash ratio = 30 ÷ 80 = 0.38×.', 'Each ratio counts fewer assets as “ready money”.'),
    ],
  },
  {
    id: 'debt', title: 'Debt and safety', blurb: 'How much of the company is funded by borrowing, and whether profits comfortably cover the interest.',
    slides: [
      term('debtToEquity', 'debt-equity', 'debtToEquity', 'Debt-to-equity = borrowings 48 ÷ equity 100 = 0.48×.'),
      term('debtRatio', 'debt-equity', 'debtRatio', 'Liabilities to assets = 100 ÷ 200 = 50%.'),
      term('interestCoverage', 'coverage', undefined, 'Interest coverage = operating income 50 ÷ interest 10 = 5×.'),
    ],
  },
  {
    id: 'cash', title: 'Cash and efficiency', blurb: 'Profit is an opinion, cash is a fact: is the profit real, and what is left after investing?',
    slides: [
      term('cashConversion', 'profit-vs-cash', undefined, 'Cash conversion = 12 ÷ 10 = 1.2×.'),
      term('freeCashFlow', 'fcf', undefined, 'Free cash flow = 12 − 5 = 7.'),
      term('assetTurnover', 'turnover', undefined, 'Asset turnover = sales 300 ÷ assets 200 = 1.5×.'),
    ],
  },
  {
    id: 'growth', title: 'Growth', blurb: 'Is the business getting bigger, and is profit growing with it?',
    slides: [
      term('revenueGrowth', 'growth-bars', 'revenue', 'Example: sales 100 → 110 = +10%.'),
      term('netIncomeGrowth', 'growth-bars', 'profit', 'Example: profit 10 → 12.5 = +25%, faster than sales.'),
      term('equityGrowth', 'growth-bars', 'equity', 'Example: equity 80 → 88 = +10%.'),
      {
        id: 'growth-doubling', title: 'Small growth rates add up', visual: 'doubling',
        points: [
          'Growth compounds: each year grows on top of last year’s bigger base.',
          'Rule of 72: divide 72 by the growth rate to get the years it takes to double. At 10% a year that is about 7 years; at 5%, about 14.',
        ],
        caption: 'Starting from 100: after 15 years, 10% a year gives about 418 and 5% a year about 208.',
        takeaway: 'Steady growth for many years beats a single great year.',
      },
    ],
  },
  {
    id: 'reading', title: 'Reading a real report', blurb: 'Units, periods, the auditor, management’s promises and how charts can mislead.',
    slides: [
      term('consolidated', 'group-tree'),
      term('reportingUnit', 'units', undefined, undefined, 'Always check the unit line at the top of each table.'),
      term('priorPeriod', 'growth-bars', 'revenue', 'Reports show this year next to last year so you can see the direction.'),
      term('composition', 'balance-blocks'),
      term('strengths', 'checklist'),
      term('auditorOpinion', 'auditor'),
      term('dataQuality', 'data-quality', undefined, 'Example: 12 of 15 figures found. With fewer than 4, the analysis is flagged as possibly not meaningful.'),
      term('visualInsights', 'axis-trick', undefined, 'Same numbers (98 and 102) on both charts; only the starting point of the axis differs.'),
      term('outlook', 'outlook', undefined, 'Illustration: compare past promises with what was delivered.'),
    ],
  },
  {
    id: 'warnings', title: 'Warning signs over the years', blurb: 'Why trends beat single years, and three classic scores that flag strength, distress and suspicious earnings.',
    slides: [
      term('officialFilings', undefined, undefined, undefined, 'Exact figures from the filing beat AI reading a PDF.'),
      term('multiYearTrend', 'growth-bars', 'revenue', 'One year shows a snapshot; several years show the direction.'),
      term('piotroski', undefined, undefined, undefined, 'More “yes” answers = a business getting stronger.'),
      term('altmanZ', undefined, undefined, undefined, 'A signal from many past bankruptcies, not a prediction for one company.'),
      term('beneishM', undefined, undefined, undefined, 'A reason to read the notes carefully, never proof of fraud.'),
    ],
  },
  {
    id: 'household', title: 'Your household money', blurb: 'The same thinking for your own finances: buffers, savings, debt, goals, fees and spreading risk.',
    slides: [
      term('householdScore', 'score-gauge'),
      term('emergencyFund', 'emergency-fund', undefined, 'Example: €8,000 saved ÷ €2,000 essential costs a month = 4 months.'),
      term('insuranceCover', 'insurance', undefined, 'Example: one gap (disability cover) gives Caution.'),
      term('savingsRate', 'income-split', 'savings', 'Example: €600 saved of €3,000 income = 20%.'),
      term('housingCost', 'income-split', 'housing', 'Example: €900 housing of €3,000 income = 30%.'),
      term('consumerDebt', 'income-split', 'debt', 'Example: €240 debt repayments of €3,000 income = 8%.'),
      term('retirementSaving', 'compounding', undefined, '€200 a month for 30 years at an assumed 6% a year. Illustration, not a forecast.'),
      term('goalsOnTrack', 'goals'),
      term('goalBuckets', 'buckets'),
      term('inflationAdjusted', 'inflation', undefined, '€40,000 in today’s money at 2% inflation a year.'),
      term('feeDrag', 'fee-drag', undefined, '€10,000 invested for 30 years at an assumed 6% a year before costs.'),
      term('concentration', 'concentration'),
      term('priorityLadder', 'ladder'),
    ],
  },
];

/** Where a slide lives: lesson index and 0-based slide index. */
export function locateSlide(id: string): { lesson: Lesson; index: number } | null {
  for (const lesson of LESSONS) {
    const index = lesson.slides.findIndex((s) => s.id === id);
    if (index >= 0) return { lesson, index };
  }
  return null;
}
