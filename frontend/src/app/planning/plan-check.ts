import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { InfoTip } from '../reports/learn/info-tip';
import { Goal, HouseholdInput, INSURANCE_NAMES, Insurance, PlanningService, emptyHousehold } from './planning.service';

const CURRENT_YEAR = new Date().getFullYear();

/** The household form: about 10 minutes, every field explained, only two required numbers. */
@Component({
  selector: 'app-plan-check',
  imports: [FormsModule, RouterLink, InfoTip],
  templateUrl: './plan-check.html',
  styleUrl: './planning.css',
})
export class PlanCheck implements OnInit {
  private readonly planning = inject(PlanningService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  m: HouseholdInput = emptyHousehold();
  readonly saving = signal(false);
  readonly errors = signal<string[]>([]);
  readonly prefilled = signal(false);
  readonly insuranceNames = INSURANCE_NAMES;
  readonly insuranceKeys: (keyof Insurance)[] = ['liability', 'health', 'disability', 'termLife'];
  readonly currentYear = CURRENT_YEAR;

  ngOnInit(): void {
    const from = this.route.snapshot.queryParamMap.get('from');
    if (from) {
      this.planning.get(from).subscribe({
        next: (d) => {
          this.m = structuredClone(d.input);
          // Checks saved before the toggle existed were household checks.
          this.m.individual = d.input.individual ?? false;
          this.m.goals = this.m.goals ?? [];
          this.m.insurance = this.m.insurance ?? emptyHousehold().insurance;
          this.prefilled.set(true);
        },
      });
    }
  }

  /** "Just me" fixes the check to one adult and no dependants. */
  setIndividual(individual: boolean): void {
    this.m.individual = individual;
    if (individual) {
      this.m.adults = 1;
      this.m.dependants = 0;
    }
  }

  names(): Record<keyof Insurance, string> {
    return this.insuranceNames[this.m.country ?? 'DE'];
  }

  addGoal(): void {
    const g: Goal = { name: '', targetAmount: null, targetYear: CURRENT_YEAR + 5, currentSaved: 0, monthlyContribution: 0 };
    this.m.goals = [...this.m.goals, g];
  }

  removeGoal(i: number): void {
    this.m.goals = this.m.goals.filter((_, idx) => idx !== i);
  }

  submit(): void {
    const local: string[] = [];
    if (!this.m.country) local.push('Choose your country.');
    if (!this.m.netMonthlyIncome || this.m.netMonthlyIncome <= 0) local.push('Net monthly income must be more than 0.');
    if (!this.m.essentialMonthlySpending || this.m.essentialMonthlySpending <= 0) local.push('Essential monthly spending must be more than 0.');
    if (local.length) {
      this.errors.set(local);
      this.focusErrors();
      return;
    }
    this.errors.set([]);
    this.saving.set(true);
    const payload: HouseholdInput = {
      ...this.m,
      employerMatchFullyUsed: this.m.employerMatchAvailable ? this.m.employerMatchFullyUsed : null,
    };
    this.planning.run(payload).subscribe({
      next: (res) => {
        this.saving.set(false);
        this.router.navigate(['/plan', res.id]);
      },
      error: (err: { status?: number; error?: { errors?: string[] } }) => {
        this.saving.set(false);
        this.errors.set(err.status === 400 && err.error?.errors?.length ? err.error.errors : ['Something went wrong saving your numbers. Please try again.']);
        this.focusErrors();
      },
    });
  }

  /** Move keyboard and screen-reader focus to the error list once it has rendered. */
  private focusErrors(): void {
    setTimeout(() => {
      const el = document.getElementById('form-errors');
      el?.focus();
      el?.scrollIntoView({ block: 'center' });
    });
  }
}
