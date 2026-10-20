import { HostingService, serverMessage } from '../core/hosting.service';
import { Component, ElementRef, OnDestroy, OnInit, computed, effect, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';
import { ReportSummary, ReportsService } from '../reports/reports.service';
import { ChatService, Citation, FeedbackRating } from './chat.service';

interface DisplayMessage {
  id: string;
  role: 'USER' | 'ASSISTANT';
  content: string;
  citations: Citation[];
  feedback: FeedbackRating | null;
}

@Component({
  selector: 'app-chat',
  imports: [FormsModule, RouterLink],
  templateUrl: './chat.html',
  styleUrl: './chat.css',
})
export class Chat implements OnInit, OnDestroy {
  private readonly chatService = inject(ChatService);
  private readonly reportsService = inject(ReportsService);
  private readonly route = inject(ActivatedRoute);
  private readonly hosting = inject(HostingService);
  private readonly router = inject(Router);

  readonly question = signal('');
  readonly messages = signal<DisplayMessage[]>([]);
  readonly sending = signal(false);
  readonly error = signal<string | null>(null);
  readonly scopedDocument = signal<ReportSummary | null>(null);
  /** Set when the route's report is missing or not ready; chatting is blocked. */
  readonly scopeError = signal(false);
  /**
   * Chat only opens on a ready report: chat-ready once its AI analysis is done (the server
   * checks too and answers 409 otherwise).
   */
  readonly canSend = computed(
    () => !this.sending() && !this.scopeError() && !!this.scopedDocument()?.chatReady && this.question().trim().length > 0,
  );

  private readonly messagesEl = viewChild<ElementRef<HTMLElement>>('messagesEl');

  private documentId = '';
  private conversationId: string | null = null;
  private localMessageCounter = 0;
  private paramSubscription: Subscription | null = null;

  constructor() {
    // Keep the newest message (or the "Thinking…" bubble) in view.
    effect(() => {
      this.messages();
      this.sending();
      const el = this.messagesEl()?.nativeElement;
      if (el) {
        queueMicrotask(() => el.scrollTo({ top: el.scrollHeight }));
      }
    });
  }

  ngOnInit(): void {
    this.paramSubscription = this.route.paramMap.subscribe((params) => {
      const documentId = params.get('id') ?? '';
      this.documentId = documentId;
      this.conversationId = null;
      this.messages.set([]);
      this.error.set(null);
      this.scopedDocument.set(null);
      this.scopeError.set(false);

      if (!documentId) {
        this.router.navigate(['/reports']);
        return;
      }
      this.reportsService.get(documentId).subscribe({
        next: ({ report }) => {
          this.scopedDocument.set(report);
          if (!report.chatReady) {
            this.scopeError.set(true);
            this.error.set(`${report.filename} is still being analysed. Try again once its analysis is ready.`);
          }
        },
        error: () => {
          this.scopeError.set(true);
          this.error.set('Could not load that report. It may have been removed.');
        },
      });
    });
  }

  ngOnDestroy(): void {
    this.paramSubscription?.unsubscribe();
  }

  ask(): void {
    const question = this.question().trim();
    if (!this.canSend()) {
      return;
    }

    this.error.set(null);
    this.messages.update((msgs) => [
      ...msgs,
      { id: `local-${this.localMessageCounter++}`, role: 'USER', content: question, citations: [], feedback: null },
    ]);
    this.question.set('');
    this.sending.set(true);

    this.chatService.ask(question, this.conversationId, this.documentId).subscribe({
      next: (response) => {
        this.hosting.refreshLimits();
        this.conversationId = response.conversationId;
        this.messages.update((msgs) => [
          ...msgs,
          {
            id: response.messageId,
            role: 'ASSISTANT',
            content: response.answer,
            citations: response.citations,
            feedback: null,
          },
        ]);
        this.sending.set(false);
      },
      error: (err: { status?: number }) => {
        this.sending.set(false);
        if (err.status === 429 || err.status === 403) {
          this.error.set(serverMessage(err) ?? 'You’ve reached today’s question limit. It resets at midnight (Amsterdam time).');
        } else if (err.status === 404 || err.status === 409) {
          this.scopeError.set(true);
          this.error.set('This report is no longer available for questions. Go back to Financial reports to pick another.');
        } else {
          this.error.set('Something went wrong asking that question. Please try again.');
        }
      },
    });
  }

  submitFeedback(message: DisplayMessage, rating: FeedbackRating): void {
    if (message.feedback) {
      return;
    }
    this.error.set(null);
    this.chatService.submitFeedback(message.id, rating).subscribe({
      next: () => {
        this.messages.update((msgs) => msgs.map((m) => (m.id === message.id ? { ...m, feedback: rating } : m)));
      },
      error: () => this.error.set('Could not save your feedback. Please try again.'),
    });
  }

  newConversation(): void {
    this.conversationId = null;
    this.messages.set([]);
    if (!this.scopeError()) {
      this.error.set(null);
    }
  }
}
