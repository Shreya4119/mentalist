import { Component, computed, input } from '@angular/core';
import { Assessment } from '../reports.service';

/** Status is never colour-alone: every chip carries an icon and a word. */
@Component({
  selector: 'app-assessment-chip',
  template: `<span class="chip" [class]="'chip ' + css()"><span aria-hidden="true">{{ icon() }}</span> {{ label() }}</span>`,
  styleUrl: './charts.css',
})
export class AssessmentChip {
  readonly value = input.required<Assessment>();
  private static readonly MAP: Record<Assessment, [string, string, string]> = {
    HEALTHY: ['✓', 'Healthy', 'good'],
    CAUTION: ['!', 'Caution', 'warning'],
    CONCERN: ['✕', 'Concern', 'critical'],
    NOT_APPLICABLE: ['–', 'n/a', 'na'],
  };
  protected readonly icon = computed(() => AssessmentChip.MAP[this.value()][0]);
  protected readonly label = computed(() => AssessmentChip.MAP[this.value()][1]);
  protected readonly css = computed(() => AssessmentChip.MAP[this.value()][2]);
}
