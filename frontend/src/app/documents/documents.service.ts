import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

export type DocumentStatus = 'QUEUED' | 'PROCESSING' | 'INDEXED' | 'FAILED';

export interface DocumentResponse {
  id: string;
  filename: string;
  status: DocumentStatus;
  chunkCount: number | null;
  errorMessage: string | null;
  uploadedAt: string;
  indexedAt: string | null;
}

@Injectable({ providedIn: 'root' })
export class DocumentsService {
  private readonly http = inject(HttpClient);

  upload(file: File): Observable<DocumentResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<DocumentResponse>('/api/documents', formData);
  }

  getStatus(id: string): Observable<DocumentResponse> {
    return this.http.get<DocumentResponse>(`/api/documents/${id}`);
  }
}
