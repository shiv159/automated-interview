import { bootstrapApplication } from '@angular/platform-browser';
import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [FormsModule],
  template: `
    @if (isBank) {
      <main class="shell">
        <p class="eyebrow">Owner workspace</p>
        <h1>Question bank</h1>
        <p class="lede">Upload one UTF-8 plain-text file, maximum 64 KiB, with 1–10 unique question stems per line. Each stem must be 10–1,000 characters.</p>
        <p><a href="/">← Candidate review</a></p>
        <form (ngSubmit)="importQuestions()" novalidate>
          <label>Question stems <input type="file" accept=".txt,text/plain" (change)="ownerFile = $any($event.target).files?.[0] ?? null"></label>
          <button type="submit" [disabled]="busy()">{{ busy() ? 'Importing…' : 'Import questions' }}</button>
        </form>
        <p class="status" aria-live="polite">{{ message() }}</p>
        @if (bank(); as rows) {
          <section class="bank" aria-label="Question bank rows">
            <p>{{ rows.total }} questions · {{ rows.activeCount }} active</p>
            <div class="coverage" aria-label="Question coverage">
              @for (bucket of rows.coverage; track bucket.type + bucket.primarySkill + bucket.difficulty + bucket.status) {
                <small>{{ bucket.primarySkill || 'Behavioral' }} · {{ bucket.difficulty || 'All levels' }} · {{ bucket.status }}: {{ bucket.count }}</small>
              }
            </div>
            @for (question of rows.questions; track question.id) {
              <article class="bank-row">
                <strong>{{ question.stem }}</strong>
                <small>{{ question.origin }} · {{ question.type }} · {{ question.primarySkill || 'Behavioral' }} · {{ question.status }}</small>
                @if (question.origin === 'OWNER_IMPORT') {
                  <button type="button" (click)="toggleStatus(question)" [disabled]="busy()">{{ question.status === 'ACTIVE' ? 'Deactivate' : 'Activate' }}</button>
                }
              </article>
            }
          </section>
        }
      </main>
    } @else {
    <main class="shell">
      <p class="eyebrow">Automated Interview</p>
      <h1>Practice with evidence, not guesswork.</h1>
      <p class="lede">Upload synthetic interview materials to begin a focused technical and behavioral review.</p>
      <p class="notice">Your synthetic job description and résumé are sent to the configured Vertex AI model for skill analysis. Provider retention follows the provider’s terms; do not upload confidential material.</p>
      <p class="rules">Accepted documents: PDF, DOCX, or strict UTF-8 TXT; each file must be at most 2 MiB and extracted text at most 30,000 characters.</p>
      <form (ngSubmit)="startReview()" novalidate>
        <label>Job description <input type="file" accept=".pdf,.docx,.txt,application/pdf,text/plain" (change)="jobFile = $any($event.target).files?.[0] ?? null" required></label>
        <label>Résumé <input type="file" accept=".pdf,.docx,.txt,application/pdf,text/plain" (change)="resumeFile = $any($event.target).files?.[0] ?? null" required></label>
        <label>Years of experience <input name="years" type="number" min="0" max="30" [(ngModel)]="yearsExperience" required></label>
        <label class="check"><input name="attested" type="checkbox" [(ngModel)]="syntheticDataAttested" required> These files are synthetic or non-confidential.</label>
        <button type="submit" [disabled]="busy()">{{ busy() ? 'Analyzing…' : 'Start candidate review' }}</button>
      </form>
      <p class="status" aria-live="polite">{{ message() }}</p>
      @if (result(); as session) {
        <section class="result" aria-labelledby="result-title">
          <h2 id="result-title">Analysis ready</h2>
          <p>Profile match: {{ session.profileMatch }}% · Difficulty: {{ session.difficulty }}</p>
          <p>Matched: {{ session.matchedSkills.join(', ') || 'None' }}</p>
          <p>Missing: {{ session.missingSkills.join(', ') || 'None' }}</p>
          <button type="button" (click)="startInterview()" [disabled]="busy()">Start interview</button>
        </section>
      }
      @if (question(); as current) {
        <section class="result" aria-labelledby="question-title">
          <p class="eyebrow">Question {{ current.position }} of 3</p>
          <h2 id="question-title">{{ current.stem }}</h2>
          <textarea aria-label="Your answer" rows="8" [(ngModel)]="answerText"></textarea>
          <button type="button" (click)="submitAnswer()" [disabled]="busy()">{{ busy() ? 'Evaluating…' : 'Submit answer' }}</button>
        </section>
      }
      @if (report(); as finalReport) {
        <section class="result" aria-labelledby="report-title">
          <p class="eyebrow">Coaching report</p>
          <h2 id="report-title">{{ finalReport.readinessLabel }}</h2>
          <p>Readiness: {{ finalReport.readinessScore }} · Interview: {{ finalReport.interviewScore }}</p>
          @for (evaluation of finalReport.evaluations; track evaluation.position) {
            <article><strong>Question {{ evaluation.position }} · {{ evaluation.score }}/10</strong><p>{{ evaluation.improvements }}</p></article>
          }
          <div class="actions">
            <button type="button" (click)="downloadReport()">Download JSON</button>
            <button type="button" (click)="printReport()">Print / save PDF</button>
            <button type="button" (click)="deleteSession()">Delete session</button>
          </div>
        </section>
      }
    </main>
    }
  `
})
class AppComponent {
  readonly isBank = window.location.pathname === '/question-bank';
  jobFile: File | null = null;
  resumeFile: File | null = null;
  yearsExperience = 3;
  syntheticDataAttested = false;
  readonly busy = signal(false);
  readonly message = signal('');
  readonly result = signal<any>(null);
  readonly question = signal<any>(null);
  readonly report = signal<any>(null);
  answerText = '';
  ownerFile: File | null = null;
  readonly bank = signal<any>(null);

  constructor() {
    if (this.isBank) void this.loadBank();
    else {
      void this.restoreFromRoute();
      window.addEventListener('popstate', () => void this.restoreFromRoute());
    }
  }

  private navigate(path: string): void { window.history.pushState({}, '', path); }

  private problem(payload: any, fallback: string): string {
    switch (payload?.code) {
      case 'ATTESTATION_REQUIRED': return 'Confirm that both files are synthetic or non-confidential.';
      case 'INVALID_EXPERIENCE': return 'Enter years of experience from 0 through 30.';
      case 'SKILL_ANALYSIS_UNCERTAIN': return 'The materials did not produce a certain skill analysis.';
      case 'SKILL_ANALYSIS_UNAVAILABLE': return 'Skill analysis is temporarily unavailable. No session was created.';
      case 'QUESTION_ENRICHMENT_UNAVAILABLE': return 'Question enrichment is unavailable. Nothing was imported.';
      case 'QUESTION_SKILL_AMBIGUOUS': return 'Each technical question must clearly target exactly one supported skill.';
      case 'INVALID_ANSWER': return 'Enter a non-empty answer of at most 4,000 characters.';
      case 'SESSION_EXPIRED': return 'This session has expired.';
      default: return payload?.code || payload?.title || fallback;
    }
  }

  private validCandidateFile(file: File): boolean {
    return file.size <= 2 * 1024 * 1024 && /\.(pdf|docx|txt)$/i.test(file.name);
  }

  private async restoreFromRoute(): Promise<void> {
    const match = window.location.pathname.match(/^\/sessions\/([^/]+)\/(analysis|interview|report)$/);
    if (!match) return;
    try {
      const response = await fetch(`/api/v1/sessions/${match[1]}`, { credentials: 'include' });
      if (!response.ok) throw new Error('Session unavailable');
      const session = await response.json();
      this.result.set(session);
      if (match[2] === 'interview') await this.startInterview();
      if (match[2] === 'report') await this.loadReport();
    } catch (error) { this.message.set(error instanceof Error ? error.message : 'Session unavailable.'); }
  }

  async startReview(): Promise<void> {
    this.message.set('');
    this.result.set(null);
    if (!this.jobFile || !this.resumeFile || !this.syntheticDataAttested) {
      this.message.set('Choose both documents and confirm the synthetic-data attestation.');
      return;
    }
    if (!this.validCandidateFile(this.jobFile) || !this.validCandidateFile(this.resumeFile)) {
      this.message.set('Use PDF, DOCX, or TXT files no larger than 2 MiB each.');
      return;
    }
    const body = new FormData();
    body.append('jobDescription', this.jobFile);
    body.append('resume', this.resumeFile);
    body.append('yearsExperience', String(this.yearsExperience));
    body.append('syntheticDataAttested', String(this.syntheticDataAttested));
    this.busy.set(true);
    try {
      const response = await fetch('/api/v1/sessions', { method: 'POST', body, credentials: 'include' });
      const payload = await response.json();
      if (!response.ok) throw new Error(this.problem(payload, 'Session creation failed'));
      this.result.set(payload);
      this.navigate(`/sessions/${payload.id}/analysis`);
      this.message.set('Your materials were analyzed.');
    } catch (error) {
      this.message.set(error instanceof Error ? error.message : 'Session creation failed.');
    } finally {
      this.busy.set(false);
    }
  }

  async startInterview(): Promise<void> {
    const session = this.result();
    if (!session) return;
    this.busy.set(true);
    try {
      const response = await fetch(`/api/v1/sessions/${session.id}/interview`, { method: 'POST', credentials: 'include' });
      const payload = await response.json();
      if (!response.ok) throw new Error(this.problem(payload, 'Interview unavailable'));
      this.question.set(payload);
      this.navigate(`/sessions/${session.id}/interview`);
    } catch (error) { this.message.set(error instanceof Error ? error.message : 'Interview unavailable.'); }
    finally { this.busy.set(false); }
  }

  async submitAnswer(): Promise<void> {
    const session = this.result();
    const current = this.question();
    if (!session || !current || !this.answerText.trim()) { this.message.set('Write an answer before submitting.'); return; }
    this.busy.set(true);
    try {
      const response = await fetch(`/api/v1/sessions/${session.id}/questions/${current.instanceId}/answers`, {
        method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ answer: this.answerText })
      });
      const payload = await response.json();
      if (!response.ok) throw new Error(this.problem(payload, 'Answer unavailable'));
      this.answerText = '';
      if (payload.nextQuestion) this.question.set(payload.nextQuestion);
      else { this.question.set(null); await this.loadReport(); this.navigate(`/sessions/${session.id}/report`); }
    } catch (error) { this.message.set(error instanceof Error ? error.message : 'Answer unavailable.'); }
    finally { this.busy.set(false); }
  }

  private async loadReport(): Promise<void> {
    const session = this.result();
    const response = await fetch(`/api/v1/sessions/${session.id}/report`, { credentials: 'include' });
    const payload = await response.json();
    if (!response.ok) throw new Error(this.problem(payload, 'Report unavailable'));
    this.report.set(payload);
  }

  async loadBank(): Promise<void> {
    const response = await fetch('/api/v1/question-bank');
    if (response.ok) {
      const payload = await response.json();
      this.bank.set({ ...payload, activeCount: payload.questions.filter((item: any) => item.status === 'ACTIVE').length });
    }
  }

  async importQuestions(): Promise<void> {
    if (!this.ownerFile) { this.message.set('Choose a TXT file first.'); return; }
    if (this.ownerFile.size > 65536 || !this.ownerFile.name.toLowerCase().endsWith('.txt')) { this.message.set('Choose a UTF-8 TXT file no larger than 64 KiB.'); return; }
    const body = new FormData(); body.append('questionsFile', this.ownerFile); this.busy.set(true); this.message.set('');
    try {
      const response = await fetch('/api/v1/question-bank/import', { method: 'POST', body });
      const payload = await response.json();
      if (!response.ok) throw new Error(this.problem(payload, 'Import failed'));
      this.message.set(`Imported ${payload.createdCount} new and ${payload.updatedCount} updated questions.`);
      await this.loadBank();
    } catch (error) { this.message.set(error instanceof Error ? error.message : 'Import failed.'); }
    finally { this.busy.set(false); }
  }

  async toggleStatus(question: any): Promise<void> {
    const status = question.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    this.busy.set(true);
    try {
      const response = await fetch(`/api/v1/question-bank/questions/${question.id}/status`, { method: 'PATCH', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ status }) });
      if (!response.ok) { const payload = await response.json(); throw new Error(this.problem(payload, 'Only owner-imported questions can change status.')); }
      await this.loadBank();
    } catch (error) { this.message.set(error instanceof Error ? error.message : 'Status update failed.'); }
    finally { this.busy.set(false); }
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

  printReport(): void { window.print(); }

  async deleteSession(): Promise<void> {
    const session = this.result();
    if (!session) return;
    const response = await fetch(`/api/v1/sessions/${session.id}`, { method: 'DELETE', credentials: 'include' });
    if (response.ok) { this.result.set(null); this.report.set(null); this.question.set(null); this.navigate('/'); this.message.set('Session deleted.'); }
  }
}

bootstrapApplication(AppComponent).catch((error: unknown) => console.error(error));
