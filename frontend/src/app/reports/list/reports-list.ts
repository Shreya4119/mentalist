import { DatePipe } from '@angular/common';
import { HttpErrorResponse, HttpEventType } from '@angular/common/http';
import { Component, ElementRef, OnDestroy, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Subscription, interval } from 'rxjs';
import { ProviderInfo, ReportSummary, ReportsService, isInFlight } from '../reports.service';
import { ACCEPT, MAX_UPLOAD_MB, checkUpload, mb } from '../upload-rules';

@Component({
  selector: 'app-reports-list',
  imports: [DatePipe, FormsModule, RouterLink],
  templateUrl: './reports-list.html',
  styleUrl: '../reports.css',
})
export class ReportsList implements OnInit, OnDestroy {
  private readonly reports = inject(ReportsService);
  private readonly router = inject(Router);

  readonly items = signal<ReportSummary[]>([]);
  readonly loading = signal(true);
  readonly loadError = signal<string | null>(null);
  readonly providers = signal<ProviderInfo[]>([]);
  readonly provider = signal<string | null>(null);
  readonly uploading = signal(false);
  readonly uploadError = signal<string | null>(null);
  readonly dragOver = signal(false);
  readonly selected = signal<string[]>([]);
  /** Upload progress 0-100 while bytes are sent; null when idle. */
  readonly progress = signal<number | null>(null);
  readonly progressText = signal('');
  readonly maxUploadMb = MAX_UPLOAD_MB;

  readonly configuredProviders = computed(() => this.providers().filter((p) => p.configured));
  readonly noProvider = computed(() => this.providers().length > 0 && this.configuredProviders().length === 0);
  readonly canCompare = computed(() => this.selected().length === 2);

  private readonly fileInput = viewChild<ElementRef<HTMLInputElement>>('fileInput');
  private poller: Subscription | null = null;

  protected readonly isInFlight = isInFlight;
  protected readonly accept = ACCEPT;

  ngOnInit(): void {
    this.reports.providers().subscribe((p) => {
      this.providers.set(p);
      this.provider.set(p.find((x) => x.isDefault && x.configured)?.id ?? p.find((x) => x.configured)?.id ?? null);
    });
    this.load();
    // Poll while anything is still being analysed or indexed.
    this.poller = interval(3000).subscribe(() => {
      if (this.items().some((r) => isInFlight(r) || r.documentStatus === 'QUEUED' || r.documentStatus === 'PROCESSING')) {
        this.load(true);
      }
    });
  }

  ngOnDestroy(): void {
    this.poller?.unsubscribe();
  }

  load(quiet = false): void {
    if (!quiet) {
      this.loading.set(true);
    }
    this.reports.list().subscribe({
      next: (items) => {
        this.items.set(items);
        this.loading.set(false);
        this.loadError.set(null);
        this.selected.update((ids) => ids.filter((id) => items.some((r) => r.id === id && r.analysisStatus === 'COMPLETED')));
      },
      error: () => {
        this.loading.set(false);
        if (!quiet) {
          this.loadError.set('Could not load reports. Please try again.');
        }
      },
    });
  }

  onDrop(event: DragEvent): void {
    event.preventDefault();
    this.dragOver.set(false);
    const files = Array.from(event.dataTransfer?.files ?? []);
    if (files.length) {
      this.upload(files);
    }
  }

  onFileChosen(event: Event): void {
    const files = Array.from((event.target as HTMLInputElement).files ?? []);
    if (files.length) {
      this.upload(files);
    }
  }

  upload(files: File[]): void {
    if (this.uploading()) {
      return;
    }
    const problem = checkUpload(files);
    if (problem) {
      this.uploadError.set(problem);
      this.resetInput();
      return;
    }
    const total = files.reduce((n, f) => n + f.size, 0);
    this.uploadError.set(null);
    this.uploading.set(true);
    this.progress.set(0);
    this.progressText.set(`Uploading… 0 of ${mb(total)}`);
    this.reports.upload(files, this.provider()).subscribe({
      next: (event) => {
        if (event.type === HttpEventType.UploadProgress) {
          const sent = event.loaded;
          const pct = event.total ? Math.round((sent / event.total) * 100) : 0;
          this.progress.set(pct);
          this.progressText.set(pct >= 100 ? 'Checking the file…' : `Uploading… ${mb(sent)} of ${mb(total)}`);
        } else if (event.type === HttpEventType.Response && event.body) {
          this.finishUpload();
          this.router.navigate(['/reports', event.body.id]);
        }
      },
      error: (err: HttpErrorResponse) => {
        this.finishUpload();
        if (err.status === 409 && err.error?.existingId) {
          this.router.navigate(['/reports', err.error.existingId], { queryParams: { duplicate: 1 } });
          return;
        }
        this.uploadError.set(this.uploadMessage(err));
      },
    });
  }

  private finishUpload(): void {
    this.uploading.set(false);
    this.progress.set(null);
    this.resetInput();
  }

  private uploadMessage(err: HttpErrorResponse): string {
    const server: string | undefined = err.error?.message ?? err.error?.detail;
    switch (err.status) {
      case 0:
        return "Couldn't reach the server. Check that the backend is running, then try again.";
      case 413:
        return server ?? `That upload is larger than ${MAX_UPLOAD_MB} MB. Upload just the financial statements pages, or compress the PDF.`;
      case 400:
      case 415:
        return server ?? 'That file type is not supported.';
      default:
        return server ? `Upload failed: ${server}` : 'Upload failed. Please try again.';
    }
  }

  toggleSelect(r: ReportSummary, event: Event): void {
    event.stopPropagation();
    this.selected.update((ids) =>
      ids.includes(r.id) ? ids.filter((id) => id !== r.id) : ids.length >= 2 ? [ids[1], r.id] : [...ids, r.id],
    );
  }

  compare(): void {
    const [a, b] = this.selected();
    this.router.navigate(['/reports/compare'], { queryParams: { a, b } });
  }

  statusText(r: ReportSummary): string {
    switch (r.analysisStatus) {
      case 'PENDING':
        return 'Queued for analysis…';
      case 'PROCESSING':
        return 'Reading the report…';
      case 'FAILED':
        return 'Analysis failed';
      case 'COMPLETED':
        return r.healthRating ?? 'Analysed';
      default:
        return 'Not analysed';
    }
  }

  private resetInput(): void {
    const input = this.fileInput()?.nativeElement;
    if (input) {
      input.value = '';
    }
  }
}
