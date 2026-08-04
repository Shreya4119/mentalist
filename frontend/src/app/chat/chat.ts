import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
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
  imports: [FormsModule],
  templateUrl: './chat.html',
  styleUrl: './chat.css',
})
export class Chat {
  private readonly chatService = inject(ChatService);

  readonly question = signal('');
  readonly messages = signal<DisplayMessage[]>([]);
  readonly sending = signal(false);
  readonly error = signal<string | null>(null);

  private conversationId: string | null = null;
  private localMessageCounter = 0;

  ask(): void {
    const question = this.question().trim();
    if (!question || this.sending()) {
      return;
    }

    this.error.set(null);
    this.messages.update((msgs) => [
      ...msgs,
      { id: `local-${this.localMessageCounter++}`, role: 'USER', content: question, citations: [], feedback: null },
    ]);
    this.question.set('');
    this.sending.set(true);

    this.chatService.ask(question, this.conversationId).subscribe({
      next: (response) => {
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
      error: () => {
        this.sending.set(false);
        this.error.set('Something went wrong asking that question. Please try again.');
      },
    });
  }

  submitFeedback(message: DisplayMessage, rating: FeedbackRating): void {
    if (message.feedback) {
      return;
    }
    this.chatService.submitFeedback(message.id, rating).subscribe({
      next: () => {
        this.messages.update((msgs) => msgs.map((m) => (m.id === message.id ? { ...m, feedback: rating } : m)));
      },
    });
  }

  newConversation(): void {
    this.conversationId = null;
    this.messages.set([]);
    this.error.set(null);
  }
}
