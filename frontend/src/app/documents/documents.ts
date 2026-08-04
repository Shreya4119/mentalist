import { Component, OnDestroy, inject, signal } from '@angular/core';
import { Subscription, interval, switchMap, takeWhile } from 'rxjs';
import { DocumentResponse, DocumentsService } from './documents.service';

@Component({
  selector: 'app-documents',
  imports: [],
  templateUrl: './documents.html',
  styleUrl: './documents.css',
})
export class Documents implements OnDestroy {
  private readonly documentsService = inject(DocumentsService);

  readonly selectedFileName = signal<string | null>(null);
  readonly uploading = signal(false);
  readonly current = signal<DocumentResponse | null>(null);
  readonly error = signal<string | null>(null);

  private selectedFile: File | null = null;
  private pollSubscription: Subscription | null = null;

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.[0] ?? null;
    this.selectedFile = file;
    this.selectedFileName.set(file?.name ?? null);
  }

  upload(): void {
    if (!this.selectedFile || this.uploading()) {
      return;
    }

    this.error.set(null);
    this.uploading.set(true);
    this.current.set(null);

    this.documentsService.upload(this.selectedFile).subscribe({
      next: (doc) => {
        this.current.set(doc);
        this.uploading.set(false);
        this.pollStatus(doc.id);
      },
      error: () => {
        this.uploading.set(false);
        this.error.set('Upload failed. Please try again.');
      },
    });
  }

  ngOnDestroy(): void {
    this.pollSubscription?.unsubscribe();
  }

  private pollStatus(id: string): void {
    this.pollSubscription?.unsubscribe();

    this.pollSubscription = interval(1500)
      .pipe(
        switchMap(() => this.documentsService.getStatus(id)),
        takeWhile((doc) => doc.status === 'QUEUED' || doc.status === 'PROCESSING', true),
      )
      .subscribe((doc) => this.current.set(doc));
  }
}
