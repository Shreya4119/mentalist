import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { HouseholdResult } from './household-result';
import { PlanningService, SnapshotDetail, SnapshotSummary } from './planning.service';

/** "My plan": the latest family health check, its history, or an introduction when there is none yet. */
@Component({
  selector: 'app-plan-home',
  imports: [DatePipe, RouterLink, HouseholdResult],
  template: `
    <div class="plan-page">
      <header class="page-head">
        <div>
          <h1>My plan</h1>
          <p class="subtitle">Your financial health check, for you alone or your whole family: where you stand, what to do next, and whether your goals are on track.</p>
        </div>
        @if (latest(); as l) {
          <div class="actions">
            <a class="button-link" routerLink="/plan/check" [queryParams]="{ from: l.id }">Update my numbers</a>
          </div>
        }
      </header>

      @if (error()) {
        <p class="error" role="alert">{{ error() }} <button type="button" class="secondary" (click)="load()">Retry</button></p>
      } @else if (loading()) {
        <p class="muted">Loading your plan…</p>
      } @else if (!latest()) {
        <section class="card intro" aria-labelledby="intro-title">
          <h2 class="section-title" id="intro-title">Check your financial health in about 10 minutes</h2>
          <p class="muted">Do it for yourself, or for your whole household with a partner or children.</p>
          <ol>
            <li><strong>Enter your monthly numbers</strong>: income, spending, savings, debts, pension, investments, insurance and goals.</li>
            <li><strong>Get your household score</strong>: nine checks, each explained with your own numbers.</li>
            <li><strong>See your next step</strong>: the order that builds security, and whether each goal is on track.</li>
          </ol>
          <p class="privacy">
            Only you can see your numbers. Mentalist never connects to your bank, and nothing is computed by AI: every number
            comes from fixed, published formulas. Rules are available for Germany and the Netherlands.
          </p>
          <div><a class="button-link" routerLink="/plan/check">Start my health check</a></div>
        </section>
      } @else {
        @let l = latest()!;
        <p class="muted small">Last checked {{ l.createdAt | date: 'd MMM y' }} · {{ l.result.countryName }}</p>
        <app-household-result [result]="l.result" />

        @if (history().length > 1) {
          <section class="card" aria-labelledby="history-title">
            <h2 class="section-title" id="history-title">Your progress</h2>
            @if (trend(); as t) { <p class="trend">{{ t }}</p> }
            <ul class="history">
              @for (h of history(); track h.id) {
                <li>
                  <a [routerLink]="['/plan', h.id]">
                    <span>{{ h.createdAt | date: 'd MMM y, HH:mm' }} · {{ h.country === 'NL' ? 'Netherlands' : 'Germany' }}</span>
                    <span class="h-score">{{ h.score ?? '—' }}</span>
                    <span class="muted small">{{ h.rating }}</span>
                  </a>
                </li>
              }
            </ul>
          </section>
        }
      }
    </div>
  `,
  styleUrl: './planning.css',
})
export class PlanHome implements OnInit {
  private readonly planning = inject(PlanningService);

  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly history = signal<SnapshotSummary[]>([]);
  readonly latest = signal<SnapshotDetail | null>(null);

  /** Score change between the oldest and newest check, in plain words. */
  readonly trend = computed(() => {
    const h = this.history().filter((x) => x.score !== null);
    if (h.length < 2) return null;
    const diff = h[0].score! - h[h.length - 1].score!;
    const since = new Date(h[h.length - 1].createdAt).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' });
    if (diff === 0) return `Your score is unchanged since ${since}.`;
    return `Your score is ${diff > 0 ? 'up' : 'down'} ${Math.abs(diff)} points since ${since}.`;
  });

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.planning.list().subscribe({
      next: (list) => {
        this.history.set(list);
        if (list.length === 0) {
          this.latest.set(null);
          this.loading.set(false);
          return;
        }
        this.planning.get(list[0].id).subscribe({
          next: (d) => {
            this.latest.set(d);
            this.loading.set(false);
          },
          error: () => this.fail(),
        });
      },
      error: () => this.fail(),
    });
  }

  private fail(): void {
    this.loading.set(false);
    this.error.set('Could not load your plan.');
  }
}
