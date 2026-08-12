import { Component, inject, signal, Input, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { SessionService, Question } from '../../services/session.service';

@Component({
  selector: 'app-interview',
  standalone: true,
  imports: [FormsModule],
  template: `
    <main class="shell">
      <p class="status" aria-live="polite">{{ message() }}</p>
      @if (question(); as current) {
        <section class="result" aria-labelledby="question-title">
          <p class="eyebrow">Question {{ current.position }} of 3</p>
          <h2 id="question-title">{{ current.stem }}</h2>
          <textarea aria-label="Your answer" rows="8" [(ngModel)]="answerText"></textarea>
          <button type="button" (click)="submitAnswer()" [disabled]="busy()">{{ busy() ? 'Evaluating…' : 'Submit answer' }}</button>
        </section>
      } @else if (busy()) {
        <p>Loading interview...</p>
      }
    </main>
  `
})
export class InterviewComponent implements OnInit {
  @Input() id!: string;

  private sessionService = inject(SessionService);
  private router = inject(Router);

  readonly busy = signal(false);
  readonly message = signal('');
  readonly question = signal<Question | null>(null);
  answerText = '';

  async ngOnInit() {
    if (!this.id) {
      this.router.navigate(['/']);
      return;
    }
    this.busy.set(true);
    try {
      // In the original flow, starting an interview returns the first question.
      // If we land here directly, we might need to fetch the current question.
      // But based on the backend API we have `/api/v1/sessions/:id/interview` as a POST to start.
      // Assuming a GET equivalent doesn't exist, this might just run start again, which usually resumes.
      const q = await this.sessionService.startInterview(this.id);
      this.question.set(q);
    } catch (error: any) {
      this.message.set(error.message || 'Interview unavailable.');
    } finally {
      this.busy.set(false);
    }
  }

  async submitAnswer(): Promise<void> {
    const current = this.question();
    if (!current || !this.answerText.trim()) { 
      this.message.set('Write an answer before submitting.'); 
      return; 
    }
    this.busy.set(true);
    try {
      const response = await this.sessionService.submitAnswer(this.id, current.instanceId, this.answerText);
      this.answerText = '';
      if (response.nextQuestion) {
        this.question.set(response.nextQuestion);
      } else {
        this.question.set(null);
        this.router.navigate(['/sessions', this.id, 'report']);
      }
    } catch (error: any) { 
      this.message.set(error.message || 'Answer unavailable.'); 
    } finally { 
      this.busy.set(false); 
    }
  }
}
