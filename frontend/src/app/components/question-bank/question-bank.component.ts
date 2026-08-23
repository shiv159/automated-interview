import { Component, inject, signal, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ApiErrorService } from '../../services/api-error.service';
import { QuestionBank, QuestionBankService, QuestionSummary } from '../../services/question-bank.service';

@Component({ selector: 'app-question-bank', standalone: true, imports: [DatePipe, RouterLink, FormsModule], templateUrl: './question-bank.component.html', styleUrl: './question-bank.component.scss' })
export class QuestionBankComponent implements OnInit {
  private questionBankService = inject(QuestionBankService);
  private apiErrors = inject(ApiErrorService);
  readonly busy = signal(false); readonly message = signal(''); readonly bank = signal<QuestionBank | null>(null);
  ownerFile: File | null = null; searchTerm = ''; skillFilter = 'ALL'; difficultyFilter = 'ALL'; originFilter = 'ALL'; selectedQuestion: QuestionSummary | null = null;

  async ngOnInit() { await this.loadBank(); }

  async loadBank() {
    try { this.bank.set(await this.questionBankService.getBank()); }
    catch (e) { this.bank.set(null); this.message.set(this.apiErrors.message(e, 'Question bank unavailable.')); }
  }

  get skillOptions(): string[] {
    const skills = new Set((this.bank()?.questions ?? []).map(q => q.primarySkill ?? 'BEHAVIORAL'));
    return ['ALL', ...Array.from(skills).sort()];
  }

  get filteredQuestions(): QuestionSummary[] {
    const rows = this.bank()?.questions ?? [];
    const term = this.searchTerm.trim().toLowerCase();
    return rows.filter(q => (!term || `${q.stem} ${q.primarySkill ?? ''} ${q.origin ?? ''}`.toLowerCase().includes(term))
      && (this.skillFilter === 'ALL' || (q.primarySkill ?? 'BEHAVIORAL') === this.skillFilter)
      && (this.difficultyFilter === 'ALL' || (q.difficulty ?? 'ALL') === this.difficultyFilter)
      && (this.originFilter === 'ALL' || q.origin === this.originFilter));
  }

  displaySkill(skill: string): string { return skill === 'ALL' ? 'All Skills' : skill === 'BEHAVIORAL' ? 'Behavioral' : skill.replaceAll('_', ' '); }
  formatTags(value: unknown): string { if (Array.isArray(value)) return value.join(', '); if (typeof value !== 'string') return ''; try { const parsed = JSON.parse(value); return Array.isArray(parsed) ? parsed.join(', ') : value; } catch { return value; } }

  async importQuestions() {
    if (!this.ownerFile) { this.message.set('Choose a TXT or JSON file first.'); return; }
    const name = this.ownerFile.name.toLowerCase();
    if (this.ownerFile.size > 65536 || (!name.endsWith('.txt') && !name.endsWith('.json'))) { this.message.set('Choose a UTF-8 TXT or JSON file no larger than 64 KiB.'); return; }
    this.busy.set(true); this.message.set('');
    try { const payload = await this.questionBankService.importQuestions(this.ownerFile); this.message.set(`Imported ${payload.createdCount} new and ${payload.updatedCount} updated questions.`); await this.loadBank(); }
    catch (e: any) { this.message.set(this.apiErrors.message(e, 'Import failed.')); }
    finally { this.busy.set(false); }
  }

  async toggleStatus(question: QuestionSummary) {
    this.busy.set(true);
    try { await this.questionBankService.toggleStatus(question); await this.loadBank(); }
    catch (e: any) { this.message.set(this.apiErrors.message(e, 'Status update failed.')); }
    finally { this.busy.set(false); }
  }

  copyQuestion(question: QuestionSummary) { navigator.clipboard?.writeText(question.stem); this.message.set('Question copied to clipboard.'); }

  exportQuestions(format: 'json' | 'csv') {
    const rows = this.filteredQuestions;
    const content = format === 'json' ? JSON.stringify(rows, null, 2) : ['Question,Skill,Difficulty,Origin,Status', ...rows.map(q => [q.stem, q.primarySkill || 'Behavioral', q.difficulty || 'All levels', q.origin, q.status].map(v => `"${String(v).replaceAll('"', '""')}"`).join(','))].join('\n');
    const link = document.createElement('a'); link.href = URL.createObjectURL(new Blob([content], { type: format === 'json' ? 'application/json' : 'text/csv' })); link.download = `intervu-question-bank.${format}`; link.click(); URL.revokeObjectURL(link.href);
  }
}
