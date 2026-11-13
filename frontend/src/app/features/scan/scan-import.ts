import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { MatButton } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { describeError, retryAfterSeconds } from '../../core/http/error-messages';
import { RetryCountdown } from '../../shared/retry-countdown';
import { TEMPLATE_CSV, checkCsv, errorsCsv, type ImportReport } from './import-report';
import { ScanService } from './scan.service';

function download(name: string, content: string): void {
  const url = URL.createObjectURL(new Blob([content], { type: 'text/csv;charset=utf-8' }));
  const link = document.createElement('a');
  link.href = url;
  link.download = name;
  link.click();
  URL.revokeObjectURL(url);
}

/** Texte d'une erreur de l'import ; le 503 dit combien de scans sont déjà partis. */
function describeImportError(err: HttpErrorResponse): string {
  if (err.status === 503) {
    const published: unknown = (err.error as { published?: unknown } | null)?.published;
    if (typeof published === 'number') {
      return (
        `Kafka indisponible, ${published} ${published > 1 ? 'scans déjà publiés' : 'scan déjà publié'} ; ` +
        'réimporter le fichier ne créera pas de doublon.'
      );
    }
  }
  return describeError(err);
}

@Component({
  selector: 'app-scan-import',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, MatButton, RetryCountdown],
  styleUrl: './scan.scss',
  templateUrl: './scan-import.html',
})
export class ScanImport {
  private readonly service = inject(ScanService);

  protected readonly report = signal<ImportReport | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly retryAfter = signal<number | null>(null);

  protected busy(): boolean {
    return this.loading() || this.retryAfter() !== null;
  }

  protected downloadTemplate(): void {
    download('modele-scans.csv', TEMPLATE_CSV);
  }

  protected downloadReport(): void {
    const report = this.report();
    if (report) {
      download('rapport-import.csv', errorsCsv(report.errors));
    }
  }

  protected onPick(event: Event): void {
    const input = event.target as HTMLInputElement;
    const file = input.files?.item(0);
    input.value = '';
    if (file) {
      this.send(file);
    }
  }

  private send(file: File): void {
    this.report.set(null);
    this.retryAfter.set(null);
    const problem = checkCsv(file);
    if (problem) {
      this.error.set(problem);
      return;
    }
    this.error.set(null);
    this.loading.set(true);
    this.service.importCsv(file).subscribe({
      next: (report) => {
        this.report.set(report);
        this.loading.set(false);
      },
      error: (err: unknown) => {
        this.loading.set(false);
        if (err instanceof HttpErrorResponse) {
          this.error.set(describeImportError(err));
          if (err.status === 429) {
            this.retryAfter.set(retryAfterSeconds(err));
          }
        } else {
          this.error.set('Une erreur est survenue.');
        }
      },
    });
  }
}
