import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type Country = 'DE' | 'NL';
export type Assessment = 'HEALTHY' | 'CAUTION' | 'CONCERN' | 'NOT_APPLICABLE';
export type StepStatus = 'DONE' | 'NEXT' | 'TODO' | 'NOT_APPLICABLE';

export interface Insurance {
  liability: boolean | null;
  health: boolean | null;
  disability: boolean | null;
  termLife: boolean | null;
}

export interface Goal {
  name: string;
  targetAmount: number | null;
  targetYear: number | null;
  currentSaved: number | null;
  monthlyContribution: number | null;
}

export interface HouseholdInput {
  /** true = a check for one person ("Just me"); adults/dependants are then ignored. */
  individual: boolean | null;
  country: Country | null;
  adults: number | null;
  dependants: number | null;
  netMonthlyIncome: number | null;
  grossMonthlyIncome: number | null;
  essentialMonthlySpending: number | null;
  totalMonthlySpending: number | null;
  monthlyHousingCost: number | null;
  cashSavings: number | null;
  monthlyDebtPayments: number | null;
  expensiveDebtBalance: number | null;
  monthlyRetirementSaving: number | null;
  employerMatchAvailable: boolean | null;
  employerMatchFullyUsed: boolean | null;
  investmentsTotal: number | null;
  largestHoldingValue: number | null;
  weightedFundFeePct: number | null;
  insurance: Insurance | null;
  goals: Goal[];
}

export interface HouseholdRatio {
  key: string;
  name: string;
  area: string;
  value: number | null;
  display: string;
  formula: string | null;
  assessment: Assessment;
  benchmark: string;
  meaning: string;
  nextStep: string | null;
}

export interface AreaScore {
  area: string;
  label: string;
  score: number | null;
  rating: string;
  ratiosUsed: number;
  weight: number;
}

export interface GoalResult {
  name: string;
  targetToday: number;
  targetYear: number;
  years: number;
  bucket: 'SAFE' | 'BALANCED' | 'GROWTH';
  bucketLabel: string;
  assumedReturnPct: number;
  targetInFuture: number;
  projected: number;
  currentSaved: number;
  monthlyContribution: number;
  monthlyNeeded: number;
  onTrack: boolean;
  summary: string;
}

export interface Step {
  number: number;
  key: string;
  title: string;
  why: string;
  status: StepStatus;
  detail: string;
}

export interface Source {
  label: string;
  url: string;
}

export interface CountryNote {
  key: string;
  title: string;
  text: string;
  source: Source | null;
}

export interface HealthCheck {
  individual: boolean;
  country: Country;
  countryName: string;
  currency: string;
  health: { score: number | null; rating: string; summary: string; areas: AreaScore[]; ratiosUsed: number };
  ratios: HouseholdRatio[];
  goals: GoalResult[];
  priorities: Step[];
  notes: CountryNote[];
  assumptions: { inflationPct: number; safeReturnPct: number; balancedReturnPct: number; growthReturnPct: number; note: string };
  rulesVerifiedOn: string;
  sources: Source[];
  disclaimer: string;
}

export interface SnapshotSummary {
  id: string;
  createdAt: string;
  country: Country;
  score: number | null;
  rating: string;
}

export interface SnapshotDetail {
  id: string;
  createdAt: string;
  input: HouseholdInput;
  result: HealthCheck;
}

/** Local names of the insurance covers, matching the backend country packs. */
export const INSURANCE_NAMES: Record<Country, Record<keyof Insurance, string>> = {
  DE: {
    liability: 'Private liability insurance (Privathaftpflicht)',
    health: 'Health insurance (statutory or private)',
    disability: 'Occupational disability insurance (Berufsunfähigkeitsversicherung)',
    termLife: 'Term life insurance (Risikolebensversicherung)',
  },
  NL: {
    liability: 'Liability insurance (aansprakelijkheidsverzekering, AVP)',
    health: 'Basic health insurance (basisverzekering)',
    disability: 'Disability insurance (arbeidsongeschiktheidsverzekering)',
    termLife: 'Term life insurance (overlijdensrisicoverzekering)',
  },
};

export function emptyHousehold(): HouseholdInput {
  return {
    individual: true, country: null, adults: 1, dependants: 0,
    netMonthlyIncome: null, grossMonthlyIncome: null,
    essentialMonthlySpending: null, totalMonthlySpending: null, monthlyHousingCost: null,
    cashSavings: null, monthlyDebtPayments: null, expensiveDebtBalance: null,
    monthlyRetirementSaving: null, employerMatchAvailable: null, employerMatchFullyUsed: null,
    investmentsTotal: null, largestHoldingValue: null, weightedFundFeePct: null,
    insurance: { liability: false, health: true, disability: false, termLife: false },
    goals: [],
  };
}

@Injectable({ providedIn: 'root' })
export class PlanningService {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/planning/health-check';

  run(input: HouseholdInput): Observable<SnapshotDetail> {
    return this.http.post<SnapshotDetail>(this.base, input);
  }

  list(): Observable<SnapshotSummary[]> {
    return this.http.get<SnapshotSummary[]>(this.base);
  }

  get(id: string): Observable<SnapshotDetail> {
    return this.http.get<SnapshotDetail>(`${this.base}/${id}`);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${this.base}/${id}`);
  }
}
