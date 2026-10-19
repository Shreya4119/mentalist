import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export interface Citation {
  documentId: string;
  filename: string;
  chunkIndex: number;
  chunkText: string;
}

export interface ChatMessage {
  id: string;
  role: 'USER' | 'ASSISTANT';
  content: string;
  createdAt: string;
  citations: Citation[];
}

export interface AskResponse {
  conversationId: string;
  messageId: string;
  answer: string;
  citations: Citation[];
}

export interface ConversationHistory {
  conversationId: string;
  messages: ChatMessage[];
}

export type FeedbackRating = 'UP' | 'DOWN';

@Injectable({ providedIn: 'root' })
export class ChatService {
  private readonly http = inject(HttpClient);

  ask(question: string, conversationId: string | null, documentId: string): Observable<AskResponse> {
    return this.http.post<AskResponse>('/api/chat', { question, conversationId, documentId });
  }

  getHistory(conversationId: string): Observable<ConversationHistory> {
    return this.http.get<ConversationHistory>(`/api/chat/${conversationId}`);
  }

  submitFeedback(messageId: string, rating: FeedbackRating): Observable<void> {
    return this.http.post<void>(`/api/chat/${messageId}/feedback`, { rating });
  }
}
