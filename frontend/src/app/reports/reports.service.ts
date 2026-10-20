import { HttpClient, HttpEvent } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type AnalysisStatus = 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
export type Assessment = 'HEALTHY' | 'CAUTION' | 'CONCERN' | 'NOT_APPLICABLE';
export type RatioFormat = 'MULTIPLE' | 'PERCENT' | 'AMOUNT';
export type RatioCategory = 'PROFITABILITY' | 'LIQUIDITY' | 'LEVERAGE' | 'CASH_FLOW' | 'EFFICIENCY' | 'GROWTH';

export interface ProviderInfo {
  id: string;
  label: string;
  model: string;
  configured: boolean;
  isDefault: boolean;
}

export interface ReportSummary {
  id: string;
  filename: string;
  uploadedAt: string;
  uploadedBy: string | null;
  documentStatus: 'QUEUED' | 'PROCESSING' | 'INDEXED' | 'FAILED';
  chatReady: boolean;
  analysisStatus: AnalysisStatus | null;
  provider: string | null;
  model: string | null;
  companyName: string | null;
  reportingPeriod: string | null;
  healthScore: number | null;
  healthRating: string | null;
  errorMessage: string | null;
  analysisRequestedAt: string | null;
  analysisCompletedAt: string | null;
  /** Uploaded by the site owner and visible to everyone. */
  shared?: boolean;
}

export interface Finding {
  point: string;
  evidence: string;
}

export interface Extraction {
  companyName: string | null;
  reportingPeriod: string | null;
  priorPeriod: string | null;
  currency: string | null;
  unit: string | null;
  industry: string | null;
  financialInstitution: boolean | null;
  statementBasis: string | null;
  summary: string | null;
  highlights: string[] | null;
  strengths: Finding[] | null;
  redFlags: Finding[] | null;
  visualInsights: string[] | null;
  outlook: string | null;
  dataQualityNotes: string[] | null;
}

export interface KeyMetric {
  key: string;
  label: string;
  current: number | null;
  prior: number | null;
  currentDisplay: string;
  priorDisplay: string;
  changePct: number | null;
  source: string;
}

export interface Ratio {
  key: string;
  name: string;
  category: RatioCategory;
  format: RatioFormat;
  value: number | null;
  priorValue: number | null;
  display: string;
  priorDisplay: string;
  formula: string;
  assessment: Assessment;
  benchmark: string;
  explanation: string;
}

export interface Dimension {
  category: RatioCategory;
  label: string;
  score: number | null;
  rating: string;
  ratiosUsed: number;
  weight: number;
}

export interface HealthScore {
  score: number | null;
  rating: string;
  summary: string;
  dimensions: Dimension[];
  ratiosUsed: number;
}

export interface ReportAnalysis {
  extraction: Extraction;
  metrics: KeyMetric[];
  ratios: Ratio[];
  health: HealthScore;
  provider: string;
  model: string;
  generatedAt: string;
  disclaimer: string;
}

export interface ReportDetail {
  report: ReportSummary;
  analysis: ReportAnalysis | null;
  canManage: boolean;
}

export type Direction = 'IMPROVED' | 'WORSENED' | 'UNCHANGED' | 'NOT_COMPARABLE';

export interface ComparisonSide {
  reportId: string;
  filename: string;
  companyName: string | null;
  period: string | null;
  currency: string | null;
  unit: string | null;
  score: number | null;
  rating: string;
}

export interface Comparison {
  a: ComparisonSide;
  b: ComparisonSide;
  sameCompany: boolean;
  warnings: string[];
  metrics: { key: string; label: string; a: number | null; b: number | null; aDisplay: string; bDisplay: string; changePct: number | null }[];
  ratios: { key: string; name: string; category: string; aDisplay: string; bDisplay: string; aAssessment: Assessment; bAssessment: Assessment; direction: Direction }[];
  improvements: string[];
  deteriorations: string[];
  disclaimer: string;
}

export const CATEGORY_LABELS: Record<RatioCategory, string> = {
  PROFITABILITY: 'Profitability',
  LIQUIDITY: 'Liquidity',
  LEVERAGE: 'Leverage & solvency',
  CASH_FLOW: 'Cash flow',
  EFFICIENCY: 'Efficiency',
  GROWTH: 'Growth',
};

export function isInFlight(r: ReportSummary): boolean {
  return r.analysisStatus === 'PENDING' || r.analysisStatus === 'PROCESSING';
}

@Injectable({ providedIn: 'root' })
export class ReportsService {
  private readonly http = inject(HttpClient);

  providers(): Observable<ProviderInfo[]> {
    return this.http.get<ProviderInfo[]>('/api/reports/providers');
  }

  list(): Observable<ReportSummary[]> {
    return this.http.get<ReportSummary[]>('/api/reports');
  }

  get(id: string): Observable<ReportDetail> {
    return this.http.get<ReportDetail>(`/api/reports/${id}`);
  }

  /** One report file, or several page photos the server combines into one PDF. Emits upload progress events. */
  upload(files: File[], provider: string | null): Observable<HttpEvent<ReportSummary>> {
    const form = new FormData();
    for (const f of files) {
      form.append('file', f, f.name);
    }
    if (provider) {
      form.append('provider', provider);
    }
    return this.http.post<ReportSummary>('/api/reports', form, { reportProgress: true, observe: 'events' });
  }

  reanalyze(id: string, provider: string | null): Observable<ReportSummary> {
    const query = provider ? `?provider=${encodeURIComponent(provider)}` : '';
    return this.http.post<ReportSummary>(`/api/reports/${id}/analysis${query}`, null);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`/api/reports/${id}`);
  }

  compare(a: string, b: string): Observable<Comparison> {
    return this.http.get<Comparison>(`/api/reports/compare?a=${encodeURIComponent(a)}&b=${encodeURIComponent(b)}`);
  }
}
