import { Component, inject, signal, Input, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { SessionService, Session } from '../../services/session.service';

@Component({
  selector: 'app-candidate-review',
  standalone: true,
  imports: [FormsModule],
  template: `
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
          <div class="evidence" aria-label="Skill evidence">
            @for (claim of session.jobSkills; track claim.skillId) {
              <p><strong>{{ claim.skillId }}</strong> · {{ claim.importance }}: “{{ evidenceText(claim) }}”</p>
            }
          </div>
          <button type="button" (click)="startInterview()" [disabled]="busy()">Start interview</button>
        </section>
      }
    </main>
  `
})
export class CandidateReviewComponent implements OnInit {
  // If routed to with an ID for analysis
  @Input() id?: string;

  private sessionService = inject(SessionService);
  private router = inject(Router);

  jobFile: File | null = null;
  resumeFile: File | null = null;
  yearsExperience = 3;
  syntheticDataAttested = false;
  
  readonly busy = signal(false);
  readonly message = signal('');
  readonly result = signal<Session | null>(null);

  async ngOnInit() {
    if (this.id) {
      this.busy.set(true);
      try {
        const session = await this.sessionService.getSession(this.id);
        this.result.set(session);
      } catch (error) {
        this.message.set(error instanceof Error ? error.message : 'Session unavailable.');
      } finally {
        this.busy.set(false);
      }
    }
  }

  evidenceText(claim: any): string { return claim?.evidence ?? ''; }

  private validCandidateFile(file: File): boolean {
    return file.size <= 2 * 1024 * 1024 && /\.(pdf|docx|txt)$/i.test(file.name);
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
    
    this.busy.set(true);
    try {
      const session = await this.sessionService.createSession(this.jobFile, this.resumeFile, this.yearsExperience, this.syntheticDataAttested);
      this.result.set(session);
      this.router.navigate(['/sessions', session.id, 'analysis']);
      this.message.set('Your materials were analyzed.');
    } catch (error: any) {
      this.message.set(error.message || 'Session creation failed.');
    } finally {
      this.busy.set(false);
    }
  }

  async startInterview(): Promise<void> {
    const session = this.result();
    if (!session) return;
    this.busy.set(true);
    try {
      await this.sessionService.startInterview(session.id);
      this.router.navigate(['/sessions', session.id, 'interview']);
    } catch (error: any) { 
      this.message.set(error.message || 'Interview unavailable.'); 
    } finally { 
      this.busy.set(false); 
    }
  }
}
