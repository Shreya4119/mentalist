/**
 * Plain-language explanations for every term on the financial-report pages,
 * written for students, expats and working professionals with no finance
 * background. One source of truth for the ⓘ tips and the "Learn the basics"
 * page. Keep entries short, concrete and jargon-free; every entry answers:
 * what is it, why does it matter, what does good look like, and how does it
 * move the company's health score.
 */

export type GlossaryGroup = 'Start here' | 'Health score' | 'Ratios' | 'Line items' | 'Reading a report' | 'Your household';

export interface GlossaryEntry {
  key: string;
  term: string;
  group: GlossaryGroup;
  /** What it is, in one or two plain sentences. */
  what: string;
  /** An everyday comparison that makes it click. */
  analogy?: string;
  /** Why an investor should care. */
  why: string;
  /** What a healthy value looks like (and caveats). */
  good?: string;
  /** How it feeds the overall financial health score. */
  impact?: string;
  /** Common traps when reading it. */
  watchOut?: string;
  /** Related terms to explore next. */
  related?: string[];
}

/** Which health area a ratio counts towards, and that area's weight in the overall score. */
export const AREA_WEIGHTS: Record<string, { area: string; weight: number }> = {
  PROFITABILITY: { area: 'Profitability', weight: 25 },
  LIQUIDITY: { area: 'Liquidity', weight: 20 },
  LEVERAGE: { area: 'Leverage & solvency', weight: 20 },
  CASH_FLOW: { area: 'Cash flow', weight: 20 },
  GROWTH: { area: 'Growth', weight: 10 },
  EFFICIENCY: { area: 'Efficiency', weight: 5 },
};

const RATIO_CATEGORY: Record<string, string> = {
  netMargin: 'PROFITABILITY', operatingMargin: 'PROFITABILITY', roe: 'PROFITABILITY', roa: 'PROFITABILITY',
  currentRatio: 'LIQUIDITY', quickRatio: 'LIQUIDITY', cashRatio: 'LIQUIDITY',
  debtToEquity: 'LEVERAGE', debtRatio: 'LEVERAGE', interestCoverage: 'LEVERAGE',
  cashConversion: 'CASH_FLOW', freeCashFlow: 'CASH_FLOW',
  assetTurnover: 'EFFICIENCY',
  revenueGrowth: 'GROWTH', netIncomeGrowth: 'GROWTH', equityGrowth: 'GROWTH',
};

function ratioImpact(key: string, extra: string): string {
  const { area, weight } = AREA_WEIGHTS[RATIO_CATEGORY[key]];
  return `Counts towards ${area}, which is ${weight}% of the overall health score. A Healthy result scores 100 points for that area, Caution 60 and Concern 20. ${extra}`;
}

const ENTRIES: GlossaryEntry[] = [
  // ------------------------------------------------------------------ Start here
  {
    key: 'fundamentalAnalysis', term: 'Fundamental analysis', group: 'Start here',
    what: 'Judging a company by its actual business: how much it sells, how much profit it keeps, what it owns, what it owes and how much cash it really generates.',
    analogy: 'Like checking a used car’s engine, mileage and service history instead of just looking at the paint.',
    why: 'Share prices swing with news and mood. The fundamentals tell you whether there is a solid business underneath, which matters most if you invest for years rather than days.',
    watchOut: 'Strong fundamentals do not guarantee a good investment: the share price can already be too high. This tool looks at the business, not at whether the price is right.',
    related: ['healthScore', 'annualReport'],
  },
  {
    key: 'annualReport', term: 'Annual report and balance sheet', group: 'Start here',
    what: 'The annual report is the document a listed company publishes every year. Its core is three financial statements: the balance sheet (what it owns and owes on one day), the income statement (what it earned over the year) and the cash flow statement (where the cash actually came from and went).',
    analogy: 'Your own finances: the balance sheet is your savings, flat and loans today; the income statement is your salary minus expenses for the year; the cash flow statement is your bank account movements.',
    why: 'These statements are audited, so they are the most reliable public information about a company.',
    related: ['consolidated', 'priorPeriod', 'reportingUnit'],
  },

  // ------------------------------------------------------------------ Health score
  {
    key: 'healthScore', term: 'Financial health score', group: 'Health score',
    what: 'A 0–100 summary of how financially sound the company looks on its own figures. Mentalist calculates 16 standard ratios, rates each one Healthy, Caution or Concern, and combines them.',
    analogy: 'Like a health check-up report: blood pressure, cholesterol and sugar each get a rating, and the doctor sums up how you are doing overall.',
    why: 'It gives you a quick first impression before you dig into the details, and makes it easy to compare companies or years.',
    good: 'Strong 75+ · Stable 55–74 · Watch 35–54 · Weak below 35.',
    impact: 'Weights: Profitability 25%, Liquidity 20%, Leverage 20%, Cash flow 20%, Growth 10%, Efficiency 5%. Areas with no data are left out and the rest re-weighted.',
    watchOut: 'It is a starting point, not a verdict. Healthy ranges differ by industry (banks, utilities and software firms look very different), and it says nothing about whether the share price is fair.',
    related: ['assessment', 'PROFITABILITY', 'LIQUIDITY', 'LEVERAGE', 'CASH_FLOW'],
  },
  {
    key: 'assessment', term: 'Healthy · Caution · Concern', group: 'Health score',
    what: 'Each ratio is compared with a widely used rule-of-thumb range. Healthy means comfortably inside it, Caution means borderline, Concern means outside it. n/a means the report did not contain the figures needed.',
    why: 'It turns numbers you may not have a feel for into a simple signal, so you know where to look first.',
    impact: 'Healthy = 100 points, Caution = 60, Concern = 20. Each area’s score is the average of its ratios.',
    watchOut: 'One Concern is not automatically bad news: read why. A high debt ratio can be normal for a utility, and a low current ratio normal for a supermarket.',
  },
  {
    key: 'PROFITABILITY', term: 'Profitability', group: 'Health score',
    what: 'How good the company is at turning sales into profit, and profit into returns for its owners.',
    why: 'Profit is what ultimately pays dividends and grows the value of your shares. A company that cannot make money for long cannot survive.',
    impact: '25% of the overall score — the largest share. Made up of net margin, operating margin, ROE and ROA.',
    related: ['netMargin', 'operatingMargin', 'roe', 'roa'],
  },
  {
    key: 'LIQUIDITY', term: 'Liquidity', group: 'Health score',
    what: 'Whether the company can pay the bills that fall due within the next 12 months.',
    analogy: 'Do you have enough in your account to cover rent and card bills this month, without selling your car?',
    why: 'Profitable companies still go bust if they run out of cash at the wrong moment. Liquidity is the short-term safety check.',
    impact: '20% of the overall score. Made up of the current, quick and cash ratios.',
    related: ['currentRatio', 'quickRatio', 'cashRatio'],
  },
  {
    key: 'LEVERAGE', term: 'Leverage & solvency', group: 'Health score',
    what: 'How much the company relies on borrowed money, and how comfortably it can carry that debt over the long term.',
    analogy: 'A home loan is fine if your salary covers the payments easily; the same loan is dangerous if you lose half your income.',
    why: 'Debt magnifies results: good years look better, bad years much worse. Too much debt is the most common reason companies fail.',
    impact: '20% of the overall score. Made up of debt-to-equity, liabilities-to-assets and interest coverage.',
    related: ['debtToEquity', 'debtRatio', 'interestCoverage'],
  },
  {
    key: 'CASH_FLOW', term: 'Cash flow', group: 'Health score',
    what: 'Whether the profit shown in the accounts arrives as real cash, and whether cash is left over after investing in the business.',
    why: 'Accounting profit can be flattered by estimates; cash is much harder to fake. Investors often say “profit is opinion, cash is fact”.',
    impact: '20% of the overall score. Made up of cash conversion and free cash flow.',
    related: ['cashConversion', 'freeCashFlow', 'operatingCashFlow'],
  },
  {
    key: 'EFFICIENCY', term: 'Efficiency', group: 'Health score',
    what: 'How hard the company’s assets work to generate sales.',
    why: 'Two companies with the same profit margin can earn very different returns if one needs far fewer factories, shops or inventory to do it.',
    impact: '5% of the overall score — small, because healthy levels vary so much by industry. Made up of asset turnover.',
    related: ['assetTurnover'],
  },
  {
    key: 'GROWTH', term: 'Growth', group: 'Health score',
    what: 'How fast sales, profit and shareholders’ equity changed compared with the previous year.',
    why: 'A growing business can raise dividends and its value over time. Shrinking sales or profit is an early warning to investigate.',
    impact: '10% of the overall score. Needs the previous year’s figures, which most reports include.',
    watchOut: 'One year is a short window. A dip can be a one-off; look at several years where you can.',
    related: ['revenueGrowth', 'netIncomeGrowth', 'equityGrowth'],
  },

  // ------------------------------------------------------------------ Ratios
  {
    key: 'netMargin', term: 'Net profit margin', group: 'Ratios',
    what: 'The share of every sale the company keeps as final profit after all costs, interest and taxes. Net income ÷ Revenue.',
    analogy: 'If a café sells a coffee for 100 and keeps 12 after paying for beans, staff, rent, loan interest and tax, its net margin is 12%.',
    why: 'Higher margins mean a bigger cushion when costs rise or prices fall, and usually a stronger brand or competitive position.',
    good: '10% or more is healthy for most industries; 3–10% is thin; below 3% leaves little room for error. Supermarkets naturally run 2–4%, software firms 20%+.',
    impact: ratioImpact('netMargin', 'A falling margin often shows up in profit and growth too.'),
    related: ['operatingMargin', 'netIncome', 'revenue'],
  },
  {
    key: 'operatingMargin', term: 'Operating margin', group: 'Ratios',
    what: 'Profit from the core business, before interest and tax, as a share of sales. Operating income ÷ Revenue.',
    why: 'It shows how profitable the business itself is, separate from how it is financed or taxed — useful for comparing companies with different debt levels.',
    good: '15% or more is healthy; 5–15% is moderate; below 5% is thin.',
    impact: ratioImpact('operatingMargin', 'Operating profit also feeds interest coverage, so it matters for Leverage too.'),
    related: ['netMargin', 'operatingIncome', 'interestCoverage'],
  },
  {
    key: 'roe', term: 'Return on equity (ROE)', group: 'Ratios',
    what: 'The profit the company makes for each unit of money its shareholders have invested and left in the business. Net income ÷ Shareholders’ equity.',
    analogy: 'If you put 100 into a business you own and it earns you 15 a year, your return on equity is 15%.',
    why: 'As a shareholder, this is your money at work. Companies that keep a high ROE for years tend to create a lot of value.',
    good: '15% or more is healthy; 8–15% is moderate; below 8% is weak.',
    impact: ratioImpact('roe', 'If equity is negative, ROE can’t be calculated and is marked Concern.'),
    watchOut: 'A high ROE can come from heavy borrowing (less equity, same profit). Always check debt-to-equity alongside it.',
    related: ['debtToEquity', 'shareholdersEquity', 'roa'],
  },
  {
    key: 'roa', term: 'Return on assets (ROA)', group: 'Ratios',
    what: 'The profit the company makes from everything it owns. Net income ÷ Total assets.',
    why: 'Unlike ROE, debt can’t flatter it, so it shows how efficiently the whole business is run.',
    good: '5% or more is healthy for most companies; 2–5% is moderate. Banks are judged differently: about 1% is good for a bank.',
    impact: ratioImpact('roa', ''),
    related: ['roe', 'totalAssets', 'assetTurnover'],
  },
  {
    key: 'currentRatio', term: 'Current ratio', group: 'Ratios',
    what: 'Short-term assets (cash, money owed by customers, stock) divided by bills due within a year.',
    analogy: 'If you have 3,000 in your account and receivables, and 2,000 of bills due this month, your current ratio is 1.5.',
    why: 'Below 1 means the company doesn’t have enough short-term resources to cover its short-term bills, so it may need to borrow or sell assets in a hurry.',
    good: '1.5x or more is healthy; 1.0–1.5x is borderline; below 1.0x is a concern. Some retailers run below 1 safely because customers pay instantly.',
    impact: ratioImpact('currentRatio', 'Not used for banks and insurers.'),
    related: ['quickRatio', 'currentAssets', 'currentLiabilities'],
  },
  {
    key: 'quickRatio', term: 'Quick ratio', group: 'Ratios',
    what: 'Like the current ratio, but leaves out inventory: (Current assets − Inventory) ÷ Current liabilities.',
    why: 'Stock can be slow to sell or lose value. The quick ratio asks: could the company pay its bills without relying on selling stock?',
    good: '1.0x or more is healthy; 0.7–1.0x is borderline; below 0.7x is a concern.',
    impact: ratioImpact('quickRatio', 'Needs inventory to be reported; otherwise shown as n/a.'),
    related: ['currentRatio', 'inventory'],
  },
  {
    key: 'cashRatio', term: 'Cash ratio', group: 'Ratios',
    what: 'Cash alone divided by bills due within a year — the strictest liquidity test.',
    why: 'Shows how much of the coming year’s bills could be paid immediately, even if customers paid late.',
    good: '0.5x or more is healthy; 0.2–0.5x is moderate. Too much idle cash can also mean money isn’t being put to work.',
    impact: ratioImpact('cashRatio', ''),
    related: ['cashAndEquivalents', 'currentRatio'],
  },
  {
    key: 'debtToEquity', term: 'Debt-to-equity', group: 'Ratios',
    what: 'How much the company has borrowed for every unit of money its shareholders have put in. Total debt ÷ Shareholders’ equity.',
    analogy: 'Buying a 100 flat with 50 of your own money and a 50 loan is a debt-to-equity of 1.0.',
    why: 'Borrowing boosts returns in good times but creates fixed payments that must be made in bad times. High debt is the most common reason companies fail.',
    good: '0.5x or below is healthy; 0.5–1.5x is moderate; above 1.5x is high. Utilities and property firms normally carry more.',
    impact: ratioImpact('debtToEquity', 'Negative equity is marked Concern. Not used for banks.'),
    related: ['totalDebt', 'shareholdersEquity', 'interestCoverage'],
  },
  {
    key: 'debtRatio', term: 'Liabilities to assets', group: 'Ratios',
    what: 'The share of everything the company owns that is financed by what it owes (loans, suppliers, other obligations). Total liabilities ÷ Total assets.',
    why: 'The higher it is, the smaller the cushion before what it owes exceeds what it owns.',
    good: '50% or below is healthy; 50–70% is moderate; above 70% is high.',
    impact: ratioImpact('debtRatio', 'Not used for banks, whose deposits are liabilities by design.'),
    related: ['totalLiabilities', 'totalAssets', 'composition'],
  },
  {
    key: 'interestCoverage', term: 'Interest coverage', group: 'Ratios',
    what: 'How many times operating profit covers the interest on the company’s debt. Operating income ÷ Interest expense.',
    analogy: 'If you earn 5,000 a month and your loan interest is 1,000, you cover it 5 times — comfortable.',
    why: 'If coverage falls towards 1x, a bad year could leave the company unable to pay interest, which can trigger default.',
    good: '5x or more is healthy; 2–5x is moderate; below 2x is risky. “No interest” means the company has no meaningful interest cost.',
    impact: ratioImpact('interestCoverage', ''),
    related: ['interestExpense', 'operatingIncome', 'debtToEquity'],
  },
  {
    key: 'cashConversion', term: 'Cash conversion', group: 'Ratios',
    what: 'Operating cash flow divided by net income: how much of the reported profit actually arrived as cash.',
    why: 'Profit that never turns into cash is a warning sign: customers may not be paying, or accounting choices may be flattering results.',
    good: '1.0x or more is healthy (profit fully backed by cash); 0.7–1.0x is moderate; below 0.7x deserves a closer look.',
    impact: ratioImpact('cashConversion', 'Not meaningful when the company made a loss.'),
    related: ['operatingCashFlow', 'netIncome', 'receivables'],
  },
  {
    key: 'freeCashFlow', term: 'Free cash flow', group: 'Ratios',
    what: 'Cash left over after running the business and paying for investment in equipment and buildings. Operating cash flow − Capital expenditure.',
    analogy: 'Your salary minus living costs and the money you set aside to replace your laptop and car — what’s truly free to save or spend.',
    why: 'This is the money that can pay dividends, buy back shares, repay debt or fund growth without borrowing.',
    good: 'Positive is healthy; negative is a concern unless the company is deliberately investing heavily for growth.',
    impact: ratioImpact('freeCashFlow', ''),
    related: ['operatingCashFlow', 'capitalExpenditure'],
  },
  {
    key: 'assetTurnover', term: 'Asset turnover', group: 'Ratios',
    what: 'Sales generated for every unit of assets. Revenue ÷ Total assets.',
    why: 'Shows how hard the company’s factories, shops, stock and cash are working.',
    good: '1.0x or more is healthy for many businesses; asset-heavy industries (power, telecoms, property) naturally run much lower.',
    impact: ratioImpact('assetTurnover', 'Kept at a small weight because it varies so much by industry.'),
    related: ['roa', 'totalAssets', 'revenue'],
  },
  {
    key: 'revenueGrowth', term: 'Revenue growth', group: 'Ratios',
    what: 'How much sales changed compared with the previous year.',
    why: 'Growing sales usually means growing demand for the company’s products — the starting point for growing profits.',
    good: '10% or more is healthy; 0–10% is modest; shrinking sales is a concern.',
    impact: ratioImpact('revenueGrowth', ''),
    watchOut: 'Growth from buying another company or from price rises alone is less durable than growth from selling more.',
    related: ['revenue', 'netIncomeGrowth'],
  },
  {
    key: 'netIncomeGrowth', term: 'Net income growth', group: 'Ratios',
    what: 'How much final profit changed compared with the previous year.',
    why: 'Profit growing faster than sales means margins are improving; slower means costs are eating into sales.',
    good: '10% or more is healthy; 0–10% is modest; falling profit is a concern.',
    impact: ratioImpact('netIncomeGrowth', 'Not calculated if last year was a loss, because a percentage from a negative base is misleading.'),
    related: ['netIncome', 'netMargin'],
  },
  {
    key: 'equityGrowth', term: 'Equity growth', group: 'Ratios',
    what: 'How much shareholders’ equity — the owners’ stake — grew in a year.',
    why: 'Equity grows when the company keeps profits instead of losing money, so steady growth means the owners’ stake is getting more valuable.',
    good: '10% or more is healthy; falling equity usually means losses or large payouts.',
    impact: ratioImpact('equityGrowth', ''),
    related: ['shareholdersEquity', 'roe'],
  },

  // ------------------------------------------------------------------ Line items
  {
    key: 'revenue', term: 'Revenue', group: 'Line items',
    what: 'The total money earned from selling products or services in the year, before any costs. Also called sales or turnover.',
    why: 'The top line: everything else — profit, cash, growth — starts here.',
    related: ['revenueGrowth', 'netMargin'],
  },
  {
    key: 'operatingIncome', term: 'Operating income (EBIT)', group: 'Line items',
    what: 'Profit from the main business after running costs but before interest and tax. EBIT stands for Earnings Before Interest and Tax.',
    why: 'Shows what the business itself earns, regardless of how it is financed.',
    related: ['operatingMargin', 'interestCoverage'],
  },
  {
    key: 'netIncome', term: 'Net income', group: 'Line items',
    what: 'The final profit after all costs, interest and taxes — the “bottom line”. Also called profit after tax. A negative number is a loss.',
    why: 'This is the profit that belongs to shareholders and can be paid out or reinvested.',
    related: ['netMargin', 'roe', 'netIncomeGrowth'],
  },
  {
    key: 'interestExpense', term: 'Interest expense', group: 'Line items',
    what: 'The interest the company paid on its loans and bonds during the year (often called finance costs).',
    why: 'It must be paid whether business is good or bad, so it is a fixed burden on profit.',
    related: ['interestCoverage', 'totalDebt'],
  },
  {
    key: 'totalAssets', term: 'Total assets', group: 'Line items',
    what: 'Everything the company owns that has value: cash, money owed by customers, stock, factories, equipment, buildings, investments and brands it has bought.',
    why: 'Shows the size of the business and what it has to work with.',
    related: ['currentAssets', 'roa', 'composition'],
  },
  {
    key: 'currentAssets', term: 'Current assets', group: 'Line items',
    what: 'Assets expected to turn into cash within a year: cash, money owed by customers, stock.',
    why: 'These are what the company can use to pay its short-term bills.',
    related: ['currentRatio', 'currentLiabilities'],
  },
  {
    key: 'cashAndEquivalents', term: 'Cash and equivalents', group: 'Line items',
    what: 'Money in the bank plus very safe, short-term investments that can be turned into cash almost instantly.',
    why: 'The company’s immediate safety buffer for bills, opportunities and bad times.',
    related: ['cashRatio'],
  },
  {
    key: 'inventory', term: 'Inventory', group: 'Line items',
    what: 'Goods the company holds to sell: raw materials, work in progress and finished products.',
    why: 'Inventory ties up cash, and can lose value if it goes out of fashion or out of date. Rising faster than sales can be a warning sign.',
    related: ['quickRatio', 'currentAssets'],
  },
  {
    key: 'receivables', term: 'Trade receivables', group: 'Line items',
    what: 'Money customers owe the company for things already sold to them on credit.',
    why: 'If receivables grow much faster than sales, customers may be paying slowly — or sales may be booked too early.',
    related: ['cashConversion', 'currentAssets'],
  },
  {
    key: 'totalLiabilities', term: 'Total liabilities', group: 'Line items',
    what: 'Everything the company owes: loans and bonds, money owed to suppliers, taxes, employee obligations and more.',
    why: 'Compared with assets, it shows how much of the business really belongs to the shareholders.',
    related: ['debtRatio', 'totalDebt', 'composition'],
  },
  {
    key: 'currentLiabilities', term: 'Current liabilities', group: 'Line items',
    what: 'Bills and debts that must be paid within a year: suppliers, short-term loans, taxes due.',
    why: 'These are what the company must find cash for soon.',
    related: ['currentRatio', 'quickRatio'],
  },
  {
    key: 'totalDebt', term: 'Total debt (borrowings)', group: 'Line items',
    what: 'Money borrowed from banks and bond investors, short-term and long-term. A subset of total liabilities.',
    why: 'Debt carries interest and repayment dates; too much of it makes a company fragile.',
    related: ['debtToEquity', 'interestCoverage'],
  },
  {
    key: 'shareholdersEquity', term: 'Shareholders’ equity', group: 'Line items',
    what: 'What would be left for the owners if the company sold everything it owns and paid everything it owes. Total assets − Total liabilities. Also called net worth or book value.',
    analogy: 'Your flat is worth 100 and your loan is 60: your equity is 40.',
    why: 'Negative equity means the company owes more than it owns — a serious warning sign.',
    related: ['roe', 'debtToEquity', 'equityGrowth'],
  },
  {
    key: 'operatingCashFlow', term: 'Operating cash flow', group: 'Line items',
    what: 'The actual cash generated by day-to-day business in the year, from the cash flow statement.',
    why: 'Harder to flatter than profit. A healthy company usually generates at least as much cash as it reports in profit.',
    related: ['cashConversion', 'freeCashFlow'],
  },
  {
    key: 'capitalExpenditure', term: 'Capital expenditure (capex)', group: 'Line items',
    what: 'Cash spent on long-lasting assets such as factories, machines, buildings and software. Shown as a negative (cash going out).',
    why: 'Needed to maintain and grow the business, but it reduces the cash left for shareholders.',
    related: ['freeCashFlow'],
  },

  // ------------------------------------------------------------------ Reading a report
  {
    key: 'consolidated', term: 'Consolidated vs standalone', group: 'Reading a report',
    what: 'Consolidated statements combine the parent company and all the companies it controls, as one group. Standalone statements show the parent company alone.',
    why: 'Consolidated figures show the whole business you are investing in, so Mentalist prefers them when a report contains both.',
  },
  {
    key: 'reportingUnit', term: 'Reporting unit (crore, millions…)', group: 'Reading a report',
    what: 'Reports shorten big numbers. “INR crore” means ×10,000,000 (1 crore = 10 million); “USD millions” means ×1,000,000. Every amount on this page is in the unit shown at the top.',
    why: 'Mixing units up is the easiest way to misread a company’s size by 10× or more.',
    watchOut: 'When comparing two reports in different currencies or units, compare ratios and percentages, not amounts.',
  },
  {
    key: 'priorPeriod', term: 'Current vs prior period', group: 'Reading a report',
    what: 'Reports show this year’s figures next to last year’s, so you can see the direction of travel. FY means financial year, which may not match the calendar year (e.g. April–March in India).',
    why: 'Direction often matters more than level: improving from weak can be better than slipping from strong.',
  },
  {
    key: 'composition', term: 'What it owns vs how it’s funded', group: 'Reading a report',
    what: 'The balance sheet always balances: everything the company owns (assets) is paid for either by what it owes (liabilities) or by its owners (equity).',
    analogy: 'A 100 flat bought with a 60 loan and 40 of savings: assets 100 = liabilities 60 + equity 40.',
    why: 'The bigger the equity slice, the bigger the cushion before trouble. A large short-term liabilities slice means lots of bills coming soon.',
    related: ['totalAssets', 'totalLiabilities', 'shareholdersEquity'],
  },
  {
    key: 'strengths', term: 'Strengths and red flags', group: 'Reading a report',
    what: 'Specific positives and warning signs the AI found in the report, each with the figure, page or note it rests on.',
    why: 'Numbers don’t tell the whole story. Red flags often hide in the notes: auditor concerns, lawsuits, large guarantees, dealings with related parties, or promoters pledging their shares.',
    watchOut: 'Always click through to the original report for anything important — AI can misread.',
  },
  {
    key: 'auditorOpinion', term: 'Auditor qualification', group: 'Reading a report',
    what: 'An independent auditor checks the accounts. A “qualified” opinion or an “emphasis of matter” means the auditor has a reservation about part of them.',
    why: 'One of the most serious red flags an annual report can contain — treat any such mention with care.',
  },
  {
    key: 'dataQuality', term: 'How the figures were read', group: 'Reading a report',
    what: 'Notes on assumptions the AI made: which statements it used, figures it had to derive, unit conversions, and anything it couldn’t read.',
    why: 'Tells you how much to trust each number, and what to double-check in the original report.',
  },
  {
    key: 'visualInsights', term: 'What the report’s charts show', group: 'Reading a report',
    what: 'The AI also reads the charts and images in the report — trend lines, segment pie charts, regional maps — and summarises what they show.',
    why: 'Companies often put their most important trends in charts rather than tables.',
  },
  {
    key: 'outlook', term: 'Management outlook', group: 'Reading a report',
    what: 'What the company’s leadership says it expects for the coming year: growth plans, guidance, risks.',
    why: 'Useful context, but it is the company talking about itself — compare it with the hard numbers.',
  },
  // ------------------------------------------------------------------ Your household (family health check)
  {
    key: 'householdScore', term: 'Household health score', group: 'Your household',
    what: 'A 0–100 score for your own finances, built the same way as a company’s: nine ratios, each Healthy, Caution or Concern, averaged into five areas.',
    analogy: 'A yearly check-up for your money: not a grade on you as a person, but a list of what is fine and what to work on.',
    why: 'It turns scattered numbers into one picture, and the trend over the years shows whether your habits are working.',
    good: 'Strong is 75+, Stable 55–74, Watch 35–54, Weak below 35. Fewer than 4 ratios gives no score rather than a misleading one.',
    impact: 'Areas are weighted: Safety net 25%, Future 25%, Cash flow 20%, Debt 15%, Investing quality 15%. Healthy scores 100 points, Caution 60, Concern 20.',
    related: ['emergencyFund', 'savingsRate', 'priorityLadder'],
  },
  {
    key: 'emergencyFund', term: 'Emergency fund', group: 'Your household',
    what: 'How many months of essential costs (rent, food, insurance, transport) your cash savings would cover if your income stopped.',
    analogy: 'The spare tyre in your car: boring until the day you really need it.',
    why: 'Without it, a job loss or a broken boiler turns into debt, or forces you to sell investments at a bad moment.',
    good: '6 months or more is Healthy, 3–6 Caution, under 3 Concern. Keep it in an instant-access savings account, not in shares.',
    impact: 'Counts towards Safety net, 25% of the household score.',
    watchOut: 'In the Netherlands, add the yearly health-insurance own risk (eigen risico) on top.',
    related: ['insuranceCover', 'priorityLadder'],
  },
  {
    key: 'insuranceCover', term: 'Insurance cover', group: 'Your household',
    what: 'Whether the covers that protect a household from the biggest shocks are in place: liability, health, disability (losing your ability to work) and, with dependants, term life.',
    why: 'These events are rare but can cost more than a lifetime of savings. Insurance is the cheap way to make sure they can’t ruin your plan.',
    good: 'All in place is Healthy, one gap Caution; missing liability or health, or two gaps, is Concern.',
    impact: 'Counts towards Safety net, 25% of the household score.',
    watchOut: 'Insure the big risks, not small ones: a phone or travel insurance matters far less than disability cover.',
    related: ['emergencyFund'],
  },
  {
    key: 'savingsRate', term: 'Savings rate', group: 'Your household',
    what: 'The share of your take-home pay left after all spending: (net income − spending) ÷ net income.',
    analogy: 'A company’s profit margin, for your household.',
    why: 'It is the engine of everything else: emergency fund, goals and investing are all paid from it. Over decades, how much you save usually matters more than which investments you pick.',
    good: '20% or more is Healthy, 10–20% Caution, under 10% Concern.',
    impact: 'Counts towards Cash flow, 20% of the household score.',
    watchOut: 'Pension contributions taken from your gross salary don’t show up here; they count under Retirement saving.',
    related: ['housingCost', 'retirementSaving'],
  },
  {
    key: 'housingCost', term: 'Housing cost', group: 'Your household',
    what: 'Rent or mortgage payment as a share of take-home pay.',
    why: 'Housing is usually the biggest fixed cost, and the hardest to cut quickly. When it is high, there is little room left to save.',
    good: 'Up to 30% is Healthy, 30–40% Caution, above 40% Concern.',
    impact: 'Counts towards Cash flow, 20% of the household score.',
    related: ['savingsRate'],
  },
  {
    key: 'consumerDebt', term: 'Consumer debt load', group: 'Your household',
    what: 'Monthly repayments on loans, car finance and credit (not your mortgage) as a share of take-home pay.',
    analogy: 'A company’s interest burden: money committed before you can decide anything.',
    why: 'Expensive debt often costs more than any investment earns, so paying it off is a guaranteed return.',
    good: 'Under 10% is Healthy, 10–20% Caution, above 20% Concern. Any expensive debt outstanding (credit card, overdraft) caps this at Caution.',
    impact: 'Debt is 15% of the household score.',
    related: ['priorityLadder'],
  },
  {
    key: 'retirementSaving', term: 'Retirement saving', group: 'Your household',
    what: 'What you put towards retirement each month (workplace pension plus your own retirement investing) as a share of gross income.',
    why: 'State pensions replace only part of your income. Starting early lets decades of compounding do most of the work.',
    good: '15% or more is Healthy, 8–15% Caution, under 8% Concern. Where state and workplace pensions are generous, less may be enough; this is still an open decision per country.',
    impact: 'Counts towards Future, 25% of the household score.',
    watchOut: 'If your employer adds money when you contribute, take the full amount first: it is extra pay.',
    related: ['goalsOnTrack', 'priorityLadder'],
  },
  {
    key: 'goalsOnTrack', term: 'Goals on track', group: 'Your household',
    what: 'How many of your goals (a home deposit, education, a car) are projected to reach their target by their date, after inflation.',
    why: 'Saving “for later” is vague; a goal with an amount and a date tells you exactly how much to put aside each month.',
    good: 'All on track is Healthy, some Caution, none Concern.',
    impact: 'Counts towards Future, 25% of the household score.',
    related: ['goalBuckets', 'inflationAdjusted'],
  },
  {
    key: 'goalBuckets', term: 'Safe, balanced and growth money', group: 'Your household',
    what: 'How a goal’s money is assumed to be invested, decided by when you need it: up to 3 years Safe (2% a year), 3–7 years Balanced (4%), more than 7 years Growth (6%).',
    analogy: 'You wouldn’t put next month’s rent in shares, and you wouldn’t leave money for retirement in 30 years under the mattress.',
    why: 'Shares can fall 30–50% and take years to recover, so money needed soon should not depend on them. Long-term money has time to ride out the drops.',
    watchOut: 'These returns are illustrative assumptions, not forecasts. Real returns vary and can be negative for years.',
    related: ['goalsOnTrack', 'inflationAdjusted'],
  },
  {
    key: 'inflationAdjusted', term: 'Inflation-adjusted target', group: 'Your household',
    what: 'Your goal in tomorrow’s money. A €40,000 deposit in today’s money needs about €43,300 in 4 years if prices rise 2% a year.',
    why: 'Without it, goals look closer than they are, because prices keep rising while you save.',
    related: ['goalsOnTrack'],
  },
  {
    key: 'feeDrag', term: 'Fee drag', group: 'Your household',
    what: 'The average yearly fee (TER) of your funds, weighted by how much you hold in each.',
    why: 'Fees are taken every year whether markets go up or down, and they compound. Over 30 years, 1% in extra fees can cost roughly a quarter of the final value.',
    good: 'Up to 0.3% is Healthy, 0.3–1% Caution, above 1% Concern. Broad index funds often cost 0.1–0.3%.',
    impact: 'Counts towards Investing quality, 15% of the household score.',
    related: ['concentration'],
  },
  {
    key: 'concentration', term: 'Concentration', group: 'Your household',
    what: 'How much of your investments sits in your single largest company holding.',
    analogy: 'Putting all your eggs in one basket.',
    why: 'Even great companies can collapse. When one holding is a large part of your money, its bad news becomes your bad news.',
    good: 'Up to 10% is Healthy, 10–25% Caution, above 25% Concern. Broad funds count as 0%.',
    impact: 'Counts towards Investing quality, 15% of the household score.',
    watchOut: 'Employer shares count too, and they add risk on top of your job depending on the same company.',
    related: ['feeDrag'],
  },
  {
    key: 'priorityLadder', term: 'Your next steps (priority order)', group: 'Your household',
    what: 'The usual order for building financial security: a starter buffer, basic insurance, paying off expensive debt, your employer’s pension contribution, a full emergency fund, income protection, your goals, retirement saving, then low-cost investing.',
    why: 'Each step protects the ones after it. Investing before you have a buffer means a surprise bill can force you to sell at the worst time.',
    good: 'Mentalist marks your first unfinished step as Next. Work on that one before the rest.',
    related: ['emergencyFund', 'insuranceCover', 'consumerDebt'],
  },
];

export const GLOSSARY: Record<string, GlossaryEntry> = Object.fromEntries(ENTRIES.map((e) => [e.key, e]));
export const GLOSSARY_GROUPS: GlossaryGroup[] = ['Start here', 'Health score', 'Ratios', 'Line items', 'Reading a report', 'Your household'];
export const GLOSSARY_LIST = ENTRIES;

/**
 * "What this means for this company": one sentence translating the ratio's
 * actual value into everyday language. Returns null when there is no value.
 */
export function explainForCompany(
  key: string,
  value: number | null,
  display: string,
  assessment: string,
  company: string,
  currency: string | null,
): string | null {
  if (value === null || value === undefined) {
    return null;
  }
  const c = company || 'The company';
  const cur = currency || '';
  const per = (n: number) => `${n.toFixed(2)} ${cur}`.trim();
  const verdict =
    assessment === 'HEALTHY' ? 'That is in the healthy range.'
      : assessment === 'CAUTION' ? 'That is borderline — worth keeping an eye on.'
        : assessment === 'CONCERN' ? 'That is outside the healthy range — read the details before investing.'
          : '';
  const pct = (v: number) => `${(v * 100).toFixed(1)}%`;
  switch (key) {
    case 'netMargin':
      return `${c} keeps about ${per(value)} of profit from every 1 ${cur} of sales (${display}). ${verdict}`;
    case 'operatingMargin':
      return `Before interest and tax, ${c} earns ${display} on each sale from its core business. ${verdict}`;
    case 'roe':
      return `For every 100 ${cur} shareholders have in the business, ${c} earned about ${(value * 100).toFixed(1)} ${cur} of profit this year. ${verdict}`;
    case 'roa':
      return `Everything ${c} owns generated a ${display} profit return this year. ${verdict}`;
    case 'currentRatio':
      return `${c} has ${per(value)} of short-term assets for every 1 ${cur} of bills due within a year. ${verdict}`;
    case 'quickRatio':
      return `Leaving out stock, ${c} still has ${per(value)} of quick assets for every 1 ${cur} due within a year. ${verdict}`;
    case 'cashRatio':
      return `${c} could pay ${pct(value)} of the next year’s bills from cash alone today. ${verdict}`;
    case 'debtToEquity':
      return `${c} has borrowed ${per(value)} for every 1 ${cur} its shareholders have in the business. ${verdict}`;
    case 'debtRatio':
      return `${pct(value)} of what ${c} owns is financed by what it owes; the owners fund the other ${pct(1 - value)}. ${verdict}`;
    case 'interestCoverage':
      return `${c}’s operating profit covers its interest bill ${value.toFixed(1)} times over. ${verdict}`;
    case 'cashConversion':
      return `For every 1 ${cur} of profit reported, ${c} brought in ${per(value)} of actual cash from operations. ${verdict}`;
    case 'freeCashFlow':
      return value > 0
        ? `After running the business and investing in it, ${c} had ${display} of cash left over. ${verdict}`
        : `${c} spent ${display.replace('-', '')} more than its operations brought in after investment. ${verdict}`;
    case 'assetTurnover':
      return `Every 1 ${cur} of assets generated ${per(value)} of sales this year. ${verdict}`;
    case 'revenueGrowth':
      return `Sales ${value >= 0 ? 'grew' : 'fell'} ${pct(Math.abs(value))} compared with last year. ${verdict}`;
    case 'netIncomeGrowth':
      return `Profit ${value >= 0 ? 'grew' : 'fell'} ${pct(Math.abs(value))} compared with last year. ${verdict}`;
    case 'equityGrowth':
      return `Shareholders’ equity ${value >= 0 ? 'grew' : 'fell'} ${pct(Math.abs(value))} over the year. ${verdict}`;
    default:
      return null;
  }
}
