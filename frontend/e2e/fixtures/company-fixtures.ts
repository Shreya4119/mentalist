/** A made-up listed company ("Northwind Industries NV") as the backend would return it after importing four filings. */
export const NORTHWIND_LEI = '724500NORTHWIND00001';
export const RIVERSIDE_LEI = '724500RIVERSIDE00002';

const year = (end: string, f: Record<string, number>, r: Record<string, number | null>) => ({ periodEnd: end, currency: 'EUR', figures: f, ratios: r });

export function northwindView() {
  const years = [
    year('2021-12-31', { REVENUE: 800e6, GROSS_PROFIT: 320e6, OPERATING_INCOME: 96e6, NET_INCOME: 64e6, OPERATING_CASH_FLOW: 90e6, CAPEX: 30e6, TOTAL_ASSETS: 1.0e9, TOTAL_LIABILITIES: 560e6, NONCURRENT_BORROWINGS: 300e6, EQUITY: 440e6, CASH: 120e6 },
      { operatingMargin: 0.12, netMargin: 0.08, revenueGrowth: null }),
    year('2022-12-31', { REVENUE: 900e6, GROSS_PROFIT: 369e6, OPERATING_INCOME: 117e6, NET_INCOME: 76e6, OPERATING_CASH_FLOW: 104e6, CAPEX: 34e6, TOTAL_ASSETS: 1.08e9, TOTAL_LIABILITIES: 580e6, NONCURRENT_BORROWINGS: 290e6, EQUITY: 500e6, CASH: 140e6 },
      { operatingMargin: 0.13, netMargin: 0.0844, revenueGrowth: 0.125 }),
    year('2023-12-31', { REVENUE: 1.0e9, GROSS_PROFIT: 420e6, OPERATING_INCOME: 140e6, NET_INCOME: 90e6, OPERATING_CASH_FLOW: 120e6, CAPEX: 40e6, TOTAL_ASSETS: 1.15e9, TOTAL_LIABILITIES: 590e6, NONCURRENT_BORROWINGS: 280e6, EQUITY: 560e6, CASH: 160e6 },
      { operatingMargin: 0.14, netMargin: 0.09, revenueGrowth: 0.111 }),
    year('2024-12-31', { REVENUE: 1.2e9, GROSS_PROFIT: 516e6, OPERATING_INCOME: 180e6, NET_INCOME: 120e6, OPERATING_CASH_FLOW: 156e6, CAPEX: 48e6, TOTAL_ASSETS: 1.25e9, TOTAL_LIABILITIES: 600e6, NONCURRENT_BORROWINGS: 260e6, EQUITY: 650e6, CASH: 190e6 },
      { operatingMargin: 0.15, netMargin: 0.1, revenueGrowth: 0.2 }),
  ];
  const sig = (key: string, label: string, detail: string, result = 'PASS') => ({ key, label, result, detail });
  return {
    company: { lei: NORTHWIND_LEI, name: 'Northwind Industries NV', country: 'NL', importedAt: '2026-11-01T10:00:00Z', firstYear: '2021-12-31', lastYear: '2024-12-31', yearsOfData: 4 },
    history: {
      years,
      trends: { yearsOfData: 4, firstYear: '2021-12-31', lastYear: '2024-12-31', revenueCagr: 0.1447, netIncomeCagr: 0.2333, operatingCashFlowCagr: 0.2007, revenueGrowthYears: 3, revenueDeclineYears: 0 },
      piotroski: [{
        periodEnd: '2024-12-31', score: 8, known: 9, verdict: 'Strong: 8 of 9 checks pass.',
        signals: [
          sig('roa', 'Profitable', 'Return on assets 9.6%'),
          sig('cfo', 'Positive operating cash flow', 'EUR 156.0 m'),
          sig('droa', 'Return on assets improved', '7.8% → 9.6%'),
          sig('accrual', 'Cash flow above profit', 'EUR 156.0 m vs EUR 120.0 m'),
          sig('leverage', 'Less long-term debt', '24.3% → 20.8% of assets'),
          sig('liquidity', 'Current ratio improved', 'Not reported', 'UNKNOWN'),
          sig('shares', 'No new shares issued', 'Shares unchanged'),
          sig('margin', 'Gross margin improved', '42.0% → 43.0%'),
          sig('turnover', 'Asset turnover improved', '0.87× → 0.96×'),
        ],
      }],
      altman: [
        { periodEnd: '2023-12-31', z: 3.52, zone: 'SAFE', parts: {}, missing: [], verdict: 'Safe zone.' },
        { periodEnd: '2024-12-31', z: 3.96, zone: 'SAFE', parts: {}, missing: [], verdict: 'Safe zone: low risk of financial distress on these figures.' },
      ],
      beneish: [{ periodEnd: '2024-12-31', m: -2.49, flagged: false, parts: {}, missing: [], verdict: 'Below the −1.78 line: no unusual earnings patterns.' }],
      warnings: [
        { key: 'receivablesOutpaceSales', title: 'Customers are paying more slowly', evidence: 'Receivables grew 35.0% while revenue grew 20.0% in 2024.', periodEnd: '2024-12-31' },
      ],
      strengths: [
        { key: 'steadyGrowth', title: 'Steady sales growth', evidence: 'Revenue grew every year, 14.5% a year on average.', periodEnd: '2024-12-31' },
      ],
    },
    sources: years.map((y) => ({
      periodEnd: y.periodEnd, filingPeriodEnd: y.periodEnd === '2021-12-31' ? '2022-12-31' : y.periodEnd,
      viewerUrl: `https://filings.xbrl.org/viewer/northwind-${y.periodEnd.slice(0, 4)}.html`,
      concepts: { REVENUE: 'ifrs-full:Revenue', NET_INCOME: 'ifrs-full:ProfitLoss' },
    })),
    importNote: null,
  };
}
