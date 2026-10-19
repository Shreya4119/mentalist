import { Component, computed, input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { AssessmentChip } from '../reports/charts/assessment-chip';
import { ScoreBand } from '../reports/charts/score-band';
import { InfoTip } from '../reports/learn/info-tip';
import { HealthCheck, HouseholdRatio, StepStatus } from './planning.service';

const STEP_MARK: Record<StepStatus, string> = { DONE: '✓', NEXT: '→', TODO: '', NOT_APPLICABLE: '–' };
const STEP_LABEL: Record<StepStatus, string> = { DONE: 'done', NEXT: 'next step', TODO: 'to do', NOT_APPLICABLE: 'does not apply' };

/** Everything the family health check found: score, next steps, ratios, goals, country notes. */
@Component({
  selector: 'app-household-result',
  imports: [DecimalPipe, AssessmentChip, ScoreBand, InfoTip],
  templateUrl: './household-result.html',
  styleUrl: './planning.css',
})
export class HouseholdResult {
  readonly result = input.required<HealthCheck>();

  protected readonly stepMark = STEP_MARK;
  protected readonly stepLabel = STEP_LABEL;

  /** Ratios grouped by area, in the order the score uses. */
  protected readonly groups = computed(() =>
    this.result().health.areas.map((a) => ({
      area: a.area,
      label: a.label,
      weight: a.weight,
      ratios: this.result().ratios.filter((r) => r.area === a.area),
    })),
  );

  protected readonly nextStep = computed(() => this.result().priorities.find((s) => s.status === 'NEXT') ?? null);

  protected ratingClass(rating: string): string {
    return rating.toLowerCase().replace(/\s+/g, '-');
  }

  protected progress(projected: number, target: number): number {
    return target > 0 ? Math.min(100, Math.round((projected / target) * 100)) : 0;
  }

  protected yourNumbers(r: HouseholdRatio): string | null {
    return r.assessment === 'NOT_APPLICABLE' ? null : r.meaning;
  }
}
