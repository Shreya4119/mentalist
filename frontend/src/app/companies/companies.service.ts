import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type Metric =
  | 'REVENUE' | 'COST_OF_SALES' | 'GROSS_PROFIT' | 'SGA' | 'DEPRECIATION' | 'OPERATING_INCOME' | 'FINANCE_COSTS'
  | 'PROFIT_BEFORE_TAX' | 'NET_INCOME' | 'OPERATING_CASH_FLOW' | 'CAPEX' | 'TOTAL_ASSETS' | 'CURRENT_ASSETS' | 'CASH'
  | 'INVENTORIES' | 'RECEIVABLES' | 'PPE' | 'TOTAL_LIABILITIES' | 'CURRENT_LIABILITIES' | 'NONCURRENT_BORROWINGS'
  | 'EQUITY' | 'RETAINED_EARNINGS' | 'SHARES_OUTSTANDING';

export interface CompanySummary {
  lei: string;
  name: string;
  country: string | null;
  importedAt: string | null;
  firstYear: string | null;
  lastYear: string | null;
  yearsOfData: number;
}

export interface SearchHit {
  lei: string;
  name: string;
  imported: boolean;
}

export interface HistoryYear {
  periodEnd: string;
  currency: string | null;
  figures: Partial<Record<Metric, number | null>>;
  ratios: Record<string, number | null>;
}

export type SignalResult = 'PASS' | 'FAIL' | 'UNKNOWN';

export interface Piotroski {
  periodEnd: string;
  score: number;
  known: number;
  signals: { key: string; label: string; result: SignalResult; detail: string }[];
  verdict: string;
}

export interface Altman {
  periodEnd: string;
  z: number | null;
  zone: 'SAFE' | 'GREY' | 'DISTRESS' | null;
  parts: Record<string, number>;
  missing: string[];
  verdict: string;
}

export interface Beneish {
  periodEnd: string;
  m: number | null;
  flagged: boolean;
  parts: Record<string, number>;
  missing: string[];
  verdict: string;
}

export interface Finding {
  key: string;
  title: string;
  evidence: string;
  periodEnd: string;
}

export interface Country {
  code: string;
  name: string;
}

export interface DirectoryItem {
  lei: string;
  name: string;
  country: string;
  lastPeriodEnd: string | null;
  imported: boolean;
}

export interface CompanyHistory {
  years: HistoryYear[];
  trends: {
    yearsOfData: number;
    firstYear: string | null;
    lastYear: string | null;
    revenueCagr: number | null;
    netIncomeCagr: number | null;
    operatingCashFlowCagr: number | null;
    revenueGrowthYears: number;
    revenueDeclineYears: number;
  };
  piotroski: Piotroski[];
  altman: Altman[];
  beneish: Beneish[];
  warnings: Finding[];
  strengths: Finding[];
}

export interface YearSource {
  periodEnd: string;
  filingPeriodEnd: string | null;
  viewerUrl: string | null;
  concepts: Record<string, string>;
}

export interface CompanyView {
  company: CompanySummary;
  history: CompanyHistory;
  sources: YearSource[];
  importNote: string | null;
}

/** Listed EU companies read from their official annual filings (ESEF). */
@Injectable({ providedIn: 'root' })
export class CompaniesService {
  private readonly http = inject(HttpClient);

  list(): Observable<CompanySummary[]> {
    return this.http.get<CompanySummary[]>('/api/companies');
  }

  search(q: string): Observable<SearchHit[]> {
    return this.http.get<SearchHit[]>('/api/companies/search', { params: { q } });
  }

  countries(): Observable<Country[]> {
    return this.http.get<Country[]>('/api/companies/countries');
  }

  /** Every company from one country with an annual filing in the index, A–Z. */
  directory(country: string): Observable<DirectoryItem[]> {
    return this.http.get<DirectoryItem[]>('/api/companies/directory', { params: { country } });
  }

  get(lei: string): Observable<CompanyView> {
    return this.http.get<CompanyView>(`/api/companies/${encodeURIComponent(lei)}`);
  }

  import(lei: string, refresh = false): Observable<CompanyView> {
    return this.http.post<CompanyView>(`/api/companies/${encodeURIComponent(lei)}/import`, null, {
      params: refresh ? { refresh: 'true' } : {},
    });
  }
}

/** "EUR 1.2 bn" style amounts, matching the backend's evidence texts. */
export function money(v: number | null | undefined, currency: string | null): string {
  if (v === null || v === undefined) return '—';
  const c = currency ? currency + ' ' : '';
  const a = Math.abs(v);
  const sign = v < 0 ? '−' : '';
  if (a >= 1e9) return `${sign}${c}${(a / 1e9).toFixed(1)} bn`;
  if (a >= 1e6) return `${sign}${c}${(a / 1e6).toFixed(1)} m`;
  return `${sign}${c}${Math.round(a).toLocaleString('en-US')}`;
}

export function pct(v: number | null | undefined, digits = 1): string {
  return v === null || v === undefined ? '—' : `${(v * 100).toFixed(digits)}%`;
}

export function times(v: number | null | undefined): string {
  return v === null || v === undefined ? '—' : `${v.toFixed(2)}×`;
}
