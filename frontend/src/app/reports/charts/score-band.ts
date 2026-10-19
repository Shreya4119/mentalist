import { Component, computed, input } from '@angular/core';

/** The 0-100 health score on its four published bands, with a marker at the score. */
@Component({
  selector: 'app-score-band',
  template: `
    <div class="score-band" role="img"
         [attr.aria-label]="score() === null ? 'No score: ' + rating() : 'Health score ' + score() + ' of 100, ' + rating()">
      <div class="score-number">
        @if (score() !== null) {
          <span class="big">{{ score() }}</span><span class="of">/100</span>
        } @else {
          <span class="big na">—</span>
        }
        <span class="rating" [class]="'rating ' + ratingClass()">{{ rating() }}</span>
      </div>
      <div class="band">
        <span class="b weak" style="flex-grow: 35">Weak</span>
        <span class="b watch" style="flex-grow: 20">Watch</span>
        <span class="b stable" style="flex-grow: 20">Stable</span>
        <span class="b strong" style="flex-grow: 25">Strong</span>
        @if (score() !== null) {
          <span class="marker" [style.left.%]="score()"></span>
        }
      </div>
      <div class="ticks"><span>0</span><span>35</span><span>55</span><span>75</span><span>100</span></div>
    </div>
  `,
  styleUrl: './charts.css',
})
export class ScoreBand {
  readonly score = input<number | null>(null);
  readonly rating = input.required<string>();
  protected readonly ratingClass = computed(() => this.rating().toLowerCase().replace(/\s+/g, '-'));
}
