import { Component, ElementRef, HostListener, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { GLOSSARY } from './glossary';

/** Only one tip is open at a time across the page. */
const openTip = signal<object | null>(null);
let nextId = 0;

/**
 * The ⓘ icon after a term. Click/tap (or Enter/Space) opens a short,
 * plain-language explanation from the glossary: what it is, an everyday
 * analogy, why it matters, what good looks like, how it moves the health
 * score -- and, when given, what this company's own number means.
 * Escape, the close button or a click elsewhere closes it. On phones the
 * panel becomes a bottom sheet so it never runs off-screen.
 */
@Component({
  selector: 'app-info-tip',
  imports: [RouterLink],
  template: `
    @if (entry(); as e) {
      <button type="button" class="info-btn" [attr.aria-expanded]="open()" [attr.aria-controls]="panelId"
              [attr.aria-label]="'What is ' + e.term + '?'" (click)="toggle($event)">
        <span aria-hidden="true">i</span>
      </button>
      @if (open()) {
        <div class="info-panel" [id]="panelId" role="dialog" [attr.aria-label]="e.term" [style.left.px]="offset()"
             [class.above]="above()" [style.max-height.px]="maxHeight()" (click)="$event.stopPropagation()">
          <div class="info-head">
            <strong>{{ e.term }}</strong>
            <button type="button" class="info-close" (click)="close()" aria-label="Close explanation">×</button>
          </div>
          @if (forCompany()) {
            <p class="info-company"><span class="tag">{{ contextLabel() }}</span> {{ forCompany() }}</p>
          }
          <p>{{ e.what }}</p>
          @if (e.analogy) {
            <p class="info-analogy"><span class="tag">In everyday terms</span> {{ e.analogy }}</p>
          }
          <p><span class="tag">Why it matters</span> {{ e.why }}</p>
          @if (e.good) {
            <p><span class="tag">What good looks like</span> {{ e.good }}</p>
          }
          @if (e.impact) {
            <p class="info-impact"><span class="tag">Effect on the health score</span> {{ e.impact }}</p>
          }
          @if (e.watchOut) {
            <p class="info-watch"><span class="tag">Watch out</span> {{ e.watchOut }}</p>
          }
          <a class="info-more" routerLink="/reports/learn" [fragment]="e.key" (click)="close()">More in Learn the basics →</a>
        </div>
      }
    }
  `,
  styleUrl: './info-tip.css',
})
export class InfoTip {
  /** Glossary key, e.g. "currentRatio". Unknown keys render nothing. */
  readonly term = input.required<string>();
  /** Optional sentence about this company's own value. */
  readonly forCompany = input<string | null>(null);
  /** Label for that sentence: "This report" on report pages, "Your numbers" on the household check. */
  readonly contextLabel = input('This report');

  private readonly host = inject(ElementRef<HTMLElement>);
  protected readonly panelId = `info-tip-${nextId++}`;
  protected readonly entry = computed(() => GLOSSARY[this.term()] ?? null);
  protected readonly open = computed(() => openTip() === this);
  protected readonly offset = signal(0);
  /** Open upwards when there isn't room below the icon. */
  protected readonly above = signal(false);
  protected readonly maxHeight = signal<number | null>(null);

  toggle(event: Event): void {
    event.stopPropagation();
    if (this.open()) {
      this.close();
      return;
    }
    openTip.set(this);
    // Keep the panel inside the viewport horizontally (desktop; phones use a bottom sheet).
    const rect = (this.host.nativeElement as HTMLElement).getBoundingClientRect();
    const width = Math.min(340, window.innerWidth - 24);
    const overflowRight = rect.left + width - (window.innerWidth - 12);
    this.offset.set(overflowRight > 0 ? -overflowRight : 0);
    const spaceBelow = window.innerHeight - rect.bottom - 16;
    const spaceAbove = rect.top - 16;
    const openAbove = spaceBelow < 360 && spaceAbove > spaceBelow;
    this.above.set(openAbove);
    this.maxHeight.set(Math.max(180, Math.min(520, openAbove ? spaceAbove : spaceBelow)));
  }

  close(): void {
    if (this.open()) {
      openTip.set(null);
      (this.host.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.info-btn')?.focus();
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    this.close();
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (this.open() && !(this.host.nativeElement as HTMLElement).contains(event.target as Node)) {
      openTip.set(null);
    }
  }
}
