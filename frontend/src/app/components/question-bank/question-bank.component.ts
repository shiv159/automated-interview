import { Component, inject, signal, OnInit } from '@angular/core';
import { DatePipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { QuestionBankService } from '../../services/question-bank.service';

@Component({ selector: 'app-question-bank', standalone: true, imports: [DatePipe, RouterLink, FormsModule], templateUrl: './question-bank.component.html', styleUrl: './question-bank.component.scss' })
export class QuestionBankComponent implements OnInit {
  private questionBankService = inject(QuestionBankService);
  readonly busy = signal(false); readonly message = signal(''); readonly bank = signal<any>(null);
  ownerFile: File | null = null; searchTerm = ''; skillFilter = 'ALL'; difficultyFilter = 'ALL'; originFilter = 'ALL'; selectedQuestion: any = null;
  async ngOnInit() { await this.loadBank(); }
  async loadBank() { try { this.bank.set(await this.questionBankService.getBank()); } catch { this.bank.set(null); } }
  get filteredQuestions(): any[] { const rows = this.bank()?.questions ?? []; const term = this.searchTerm.trim().toLowerCase(); return rows.filter((q: any) => (!term || `${q.stem} ${q.primarySkill ?? ''} ${q.origin ?? ''}`.toLowerCase().includes(term)) && (this.skillFilter === 'ALL' || (q.primarySkill || 'BEHAVIORAL') === this.skillFilter) && (this.difficultyFilter === 'ALL' || (q.difficulty || 'ALL') === this.difficultyFilter) && (this.originFilter === 'ALL' || q.origin === this.originFilter)); }
  formatTags(value: unknown): string { if (Array.isArray(value)) return value.join(', '); if (typeof value !== 'string') return ''; try { const parsed = JSON.parse(value); return Array.isArray(parsed) ? parsed.join(', ') : value; } catch { return value; } }
  async importQuestions() { if (!this.ownerFile) { this.message.set('Choose a TXT file first.'); return; } if (this.ownerFile.size > 65536 || !this.ownerFile.name.toLowerCase().endsWith('.txt')) { this.message.set('Choose a UTF-8 TXT file no larger than 64 KiB.'); return; } this.busy.set(true); this.message.set(''); try { const payload = await this.questionBankService.importQuestions(this.ownerFile); this.message.set(`Imported ${payload.createdCount} new and ${payload.updatedCount} updated questions.`); await this.loadBank(); } catch (e: any) { this.message.set(e.message || 'Import failed.'); } finally { this.busy.set(false); } }
  async toggleStatus(question: any) { this.busy.set(true); try { await this.questionBankService.toggleStatus(question); await this.loadBank(); } catch (e: any) { this.message.set(e.message || 'Status update failed.'); } finally { this.busy.set(false); } }
  copyQuestion(question: any) { navigator.clipboard?.writeText(question.stem); this.message.set('Question copied to clipboard.'); }
  exportQuestions(format: 'json' | 'csv') { const rows = this.filteredQuestions; const content = format === 'json' ? JSON.stringify(rows, null, 2) : ['Question,Skill,Difficulty,Origin,Status', ...rows.map((q: any) => [q.stem, q.primarySkill || 'Behavioral', q.difficulty || 'All levels', q.origin, q.status].map((v: any) => `"${String(v).replaceAll('"', '""')}"`).join(','))].join('\n'); const link = document.createElement('a'); link.href = URL.createObjectURL(new Blob([content], { type: format === 'json' ? 'application/json' : 'text/csv' })); link.download = `intervu-question-bank.${format}`; link.click(); URL.revokeObjectURL(link.href); }
}
