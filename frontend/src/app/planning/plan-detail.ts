import { Component, OnInit, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { HouseholdResult } from './household-result';
import { PlanningService, SnapshotDetail } from './planning.service';

/** One saved health check, e.g. from last year, with update and delete. */
@Component({
  selector: 'app-plan-detail',
  imports: [DatePipe, RouterLink, HouseholdResult],
  template: `
    <div class="plan-page">
      <a routerLink="/plan" class="back-link">← My plan</a>
      @if (error()) {
        <p class="error" role="alert">{{ error() }}</p>
      } @else if (!detail()) {
        <p class="muted">Loading…</p>
      } @else {
        @let d = detail()!;
        <header class="page-head">
          <div>
            <h1>Health check of {{ d.createdAt | date: 'd MMM y' }}</h1>
            <p class="subtitle">
              {{ d.result.countryName }} ·
              @if (d.result.individual) { Just me } @else {
                {{ d.input.adults === 2 ? 'Two adults' : 'One adult' }}{{ d.input.dependants ? ' · ' + d.input.dependants + ' dependant' + (d.input.dependants === 1 ? '' : 's') : '' }}
              }
            </p>
          </div>
          <div class="actions">
            <a class="button-link" routerLink="/plan/check" [queryParams]="{ from: d.id }">Update from these numbers</a>
            @if (!confirming()) {
              <button type="button" class="danger" (click)="confirming.set(true)">Delete</button>
            }
          </div>
        </header>
        @if (confirming()) {
          <div class="card" role="alertdialog" aria-labelledby="del-q">
            <p id="del-q"><strong>Delete this health check and the numbers you entered?</strong> This can't be undone.</p>
            <div class="actions">
              <button type="button" class="danger" (click)="remove(d.id)">Yes, delete</button>
              <button type="button" class="secondary" (click)="confirming.set(false)">Cancel</button>
            </div>
          </div>
        }
        <app-household-result [result]="d.result" />
      }
    </div>
  `,
  styleUrl: './planning.css',
})
export class PlanDetail implements OnInit {
  private readonly planning = inject(PlanningService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  readonly detail = signal<SnapshotDetail | null>(null);
  readonly error = signal<string | null>(null);
  readonly confirming = signal(false);

  ngOnInit(): void {
    this.route.paramMap.subscribe((p) => {
      this.detail.set(null);
      this.error.set(null);
      this.confirming.set(false);
      this.planning.get(p.get('id') ?? '').subscribe({
        next: (d) => this.detail.set(d),
        error: () => this.error.set('This health check could not be found. It may have been deleted.'),
      });
    });
  }

  remove(id: string): void {
    this.planning.delete(id).subscribe({
      next: () => this.router.navigate(['/plan']),
      error: () => this.error.set('Could not delete this health check. Please try again.'),
    });
  }
}
