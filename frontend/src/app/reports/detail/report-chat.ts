import { HostingService, plural, serverMessage } from '../../core/hosting.service';
import { Component, ElementRef, computed, effect, inject, input, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ChatService, Citation } from '../../chat/chat.service';

interface QaMessage {
  id: string;
  role: 'USER' | 'ASSISTANT';
  content: string;
  citations: Citation[];
}

/**
 * "Ask about this report" -- a compact Q&A panel embedded in the analysis
 * page, so investors can ask follow-up questions without leaving the
 * numbers. Uses the same document-scoped chat API as the full chat page
 * (answers come only from this report and its computed analysis), keeps a
 * conversation for follow-ups, and offers starter questions.
 */
@Component({
  selector: 'app-report-chat',
  imports: [FormsModule, RouterLink],
  template: `
    <section class="card qa-card" id="ask" aria-labelledby="ask-title">
      <div class="qa-head">
        <h2 class="section-title" id="ask-title">Ask about this report</h2>
        <div class="qa-head-actions">
          @if (messages().length) {
            <button type="button" class="secondary small-btn" (click)="reset()">New conversation</button>
          }
          <a class="qa-full" [routerLink]="['/reports', documentId(), 'chat']">Open full screen</a>
        </div>
      </div>
      <p class="muted small">
        Answers come only from {{ filename() }} and Mentalist's analysis of it, with the passages they used.
        @if (documentStatus() === 'FAILED') {
          This file's pages are images (a scan or photos) or its text couldn't be read, so answers come from the analysis only.
        } @else if (!fullTextReady()) {
          The full report text is still being indexed, so answers use the analysis for now and get more detailed once indexing finishes.
        }
      </p>
      @if (questionsLeftText()) {
        <p class="muted small" data-testid="questions-left">{{ questionsLeftText() }}</p>
      }

      @if (messages().length === 0) {
        <div class="suggestions" role="group" aria-label="Suggested questions">
          @for (q of suggestions; track q) {
            <button type="button" class="suggestion" (click)="ask(q)" [disabled]="sending()">{{ q }}</button>
          }
        </div>
      }

      <div class="qa-messages" #scroller aria-live="polite">
        @for (m of messages(); track m.id) {
          <div class="qa-msg" [class.user]="m.role === 'USER'" [class.assistant]="m.role === 'ASSISTANT'">
            <p class="qa-text">{{ m.content }}</p>
            @if (m.citations.length) {
              <details class="qa-cites">
                <summary>{{ m.citations.length }} source{{ m.citations.length === 1 ? '' : 's' }}</summary>
                @for (c of m.citations; track $index) {
                  <div class="qa-cite">
                    <div class="qa-cite-src">{{ c.chunkIndex < 0 ? 'Mentalist analysis' : 'Report text, passage ' + (c.chunkIndex + 1) }}</div>
                    <div class="qa-cite-text">{{ c.chunkText }}</div>
                  </div>
                }
              </details>
            }
          </div>
        }
        @if (sending()) {
          <div class="qa-msg assistant pending"><span class="spinner" aria-hidden="true"></span> Reading the report…</div>
        }
      </div>

      @if (error()) {
        <p class="error" role="alert">{{ error() }}</p>
      }

      <form class="qa-composer" (ngSubmit)="ask(question())">
        <input type="text" name="question" [ngModel]="question()" (ngModelChange)="question.set($event)"
               [disabled]="sending()" placeholder="e.g. Can the company pay its short-term debts?"
               aria-label="Your question about this report" />
        <button type="submit" [disabled]="!canSend()">Ask</button>
      </form>
    </section>
  `,
  styleUrl: './report-chat.css',
})
export class ReportChat {
  private readonly chat = inject(ChatService);

  readonly documentId = input.required<string>();
  readonly filename = input.required<string>();
  readonly fullTextReady = input(true);
  readonly documentStatus = input<string | null>(null);

  readonly question = signal('');
  readonly messages = signal<QaMessage[]>([]);
  readonly sending = signal(false);
  readonly error = signal<string | null>(null);
  readonly canSend = computed(() => !this.sending() && this.question().trim().length > 0);
  protected readonly hosting = inject(HostingService);
  readonly questionsLeftText = computed(() => {
    const l = this.hosting.limits();
    return l && l.questionsPerDay > 0 ? `${plural(l.questionsLeft, 'question', 'questions')} left today` : '';
  });

  protected readonly suggestions = [
    'Explain the health score in simple terms',
    'Is the company’s debt a concern?',
    'How did revenue and profit change from last year?',
    'What are the biggest risks mentioned in the report?',
    'What does management expect for next year?',
  ];

  private conversationId: string | null = null;
  private counter = 0;
  private readonly scroller = viewChild<ElementRef<HTMLElement>>('scroller');

  constructor() {
    effect(() => {
      this.messages();
      this.sending();
      const el = this.scroller()?.nativeElement;
      if (el) {
        queueMicrotask(() => el.scrollTo({ top: el.scrollHeight }));
      }
    });
  }

  ask(text: string): void {
    const q = text.trim();
    if (!q || this.sending()) {
      return;
    }
    this.error.set(null);
    this.messages.update((m) => [...m, { id: `u${this.counter++}`, role: 'USER', content: q, citations: [] }]);
    this.question.set('');
    this.sending.set(true);
    this.chat.ask(q, this.conversationId, this.documentId()).subscribe({
      next: (r) => {
        this.hosting.refreshLimits();
        this.conversationId = r.conversationId;
        this.messages.update((m) => [...m, { id: r.messageId, role: 'ASSISTANT', content: r.answer, citations: r.citations }]);
        this.sending.set(false);
      },
      error: (err) => {
        this.sending.set(false);
        this.error.set(
          err.status === 429 || err.status === 403
            ? (serverMessage(err) ?? 'You’ve reached today’s question limit. It resets at midnight (Amsterdam time).')
            : err.status === 409
            ? 'This report isn’t ready for questions yet. Try again once the analysis finishes.'
            : err.status === 404
              ? 'This report no longer exists.'
              : 'Something went wrong asking that question. Please try again.',
        );
      },
    });
  }

  reset(): void {
    this.conversationId = null;
    this.messages.set([]);
    this.error.set(null);
  }
}
