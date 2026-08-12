import { Component, inject, signal, Input, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { SessionService, Report } from '../../services/session.service';

@Component({
  selector: 'app-report',
  standalone: true,
  imports: [DecimalPipe],
  template: `
    <main class="shell">
      <p class="status" aria-live="polite">{{ message() }}</p>
      @if (report(); as finalReport) {
        <section class="result" aria-labelledby="report-title">
          <p class="eyebrow">Coaching report</p>
          <h2 id="report-title">{{ finalReport.readinessLabel }}</h2>
            <p>Readiness: {{ finalReport.readinessScore | number:'1.0-1' }} · Interview: {{ finalReport.interviewScore | number:'1.0-1' }}</p>
          @for (evaluation of finalReport.evaluations; track evaluation.position) {
            <article>
              <strong>Question {{ evaluation.position }} · {{ evaluation.score }}/10</strong>
              <p>Strengths: {{ formatTags(evaluation.strengths) }}</p>
              <p>Improvements: {{ formatTags(evaluation.improvements) }}</p>
            </article>
          }
          <p class="disclaimer">This coaching report is practice feedback, not a hiring or employment decision.</p>
          <div class="actions">
            <button type="button" (click)="downloadReport()">Download JSON</button>
            <button type="button" (click)="printReport()">Print / save PDF</button>
            <button type="button" (click)="deleteSession()">Delete session</button>
          </div>
        </section>
      } @else if (busy()) {
        <p>Loading report...</p>
      }
    </main>
  `
})
export class ReportComponent implements OnInit {
  @Input() id!: string;

  private sessionService = inject(SessionService);
  private router = inject(Router);

  readonly busy = signal(false);
  readonly message = signal('');
  readonly report = signal<Report | null>(null);

  async ngOnInit() {
    if (!this.id) {
      this.router.navigate(['/']);
      return;
    }
    this.busy.set(true);
    try {
      const rep = await this.sessionService.getReport(this.id);
      this.report.set(rep);
    } catch (error: any) {
      this.message.set(error.message || 'Report unavailable.');
    } finally {
      this.busy.set(false);
    }
  }

  formatTags(value: unknown): string {
    if (Array.isArray(value)) return value.join(', ');
    if (typeof value !== 'string') return '';
    try {
      const parsed = JSON.parse(value);
      return Array.isArray(parsed) ? parsed.join(', ') : value;
    } catch { return value; }
  }

  downloadReport(): void {
    const value = this.report();
    if (!value) return;
    const link = document.createElement('a');
    link.href = URL.createObjectURL(new Blob([JSON.stringify(value, null, 2)], { type: 'application/json' }));
    link.download = `interview-report-${value.sessionId}.json`;
    link.click();
    URL.revokeObjectURL(link.href);
  }

  printReport(): void { 
    window.print(); 
  }

  async deleteSession(): Promise<void> {
    if (!this.id) return;
    this.busy.set(true);
    try {
      await this.sessionService.deleteSession(this.id);
      this.report.set(null);
      this.router.navigate(['/']);
    } catch (error: any) {
      this.message.set(error.message || 'Failed to delete session.');
    } finally {
      this.busy.set(false);
    }
  }
}
