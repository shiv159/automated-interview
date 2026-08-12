import { Component, inject, signal, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { QuestionBankService } from '../../services/question-bank.service';

@Component({
  selector: 'app-question-bank',
  standalone: true,
  imports: [DatePipe, RouterLink],
  template: `
    <main class="shell">
      <p class="eyebrow">Owner workspace</p>
      <h1>Question bank</h1>
      <p class="lede">Upload one UTF-8 plain-text file, maximum 64 KiB, with 1–10 unique question stems per line. Each stem must be 10–1,000 characters.</p>
      <p><a routerLink="/">← Candidate review</a></p>
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
              <small>{{ question.origin }} · {{ question.type }} · {{ question.primarySkill || 'Behavioral' }} · {{ question.difficulty || 'All levels' }} · {{ question.status }}</small>
              <small>Tags: {{ formatTags(question.tags) }} · Updated: {{ question.updatedAt | date:'medium' }}</small>
              @if (question.origin === 'OWNER_IMPORT') {
                <button type="button" (click)="toggleStatus(question)" [disabled]="busy()">{{ question.status === 'ACTIVE' ? 'Deactivate' : 'Activate' }}</button>
              }
            </article>
          }
        </section>
      }
    </main>
  `
})
export class QuestionBankComponent implements OnInit {
  private questionBankService = inject(QuestionBankService);

  readonly busy = signal(false);
  readonly message = signal('');
  readonly bank = signal<any>(null);
  
  ownerFile: File | null = null;

  async ngOnInit() {
    await this.loadBank();
  }

  async loadBank(): Promise<void> {
    try {
      const payload = await this.questionBankService.getBank();
      this.bank.set(payload);
    } catch (error: any) {
      // Backend may return 404 or errors if empty initially
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

  async importQuestions(): Promise<void> {
    if (!this.ownerFile) { 
      this.message.set('Choose a TXT file first.'); 
      return; 
    }
    if (this.ownerFile.size > 65536 || !this.ownerFile.name.toLowerCase().endsWith('.txt')) { 
      this.message.set('Choose a UTF-8 TXT file no larger than 64 KiB.'); 
      return; 
    }
    this.busy.set(true); 
    this.message.set('');
    
    try {
      const payload = await this.questionBankService.importQuestions(this.ownerFile);
      this.message.set(`Imported ${payload.createdCount} new and ${payload.updatedCount} updated questions.`);
      await this.loadBank();
    } catch (error: any) { 
      this.message.set(error.message || 'Import failed.'); 
    } finally { 
      this.busy.set(false); 
    }
  }

  async toggleStatus(question: any): Promise<void> {
    this.busy.set(true);
    try {
      await this.questionBankService.toggleStatus(question);
      await this.loadBank();
    } catch (error: any) { 
      this.message.set(error.message || 'Status update failed.'); 
    } finally { 
      this.busy.set(false); 
    }
  }
}
