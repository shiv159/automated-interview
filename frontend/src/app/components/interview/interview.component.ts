import { Component, inject, signal, Input, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { SessionService, Question } from '../../services/session.service';

@Component({ selector: 'app-interview', standalone: true, imports: [FormsModule, RouterLink], templateUrl: './interview.component.html', styleUrl: './interview.component.scss' })
export class InterviewComponent implements OnInit {
  @Input() id!: string; private sessionService = inject(SessionService); private router = inject(Router);
  readonly busy = signal(false); readonly message = signal(''); readonly question = signal<Question | null>(null); answerText = '';
  async ngOnInit() { if (!this.id) { this.router.navigate(['/']); return; } this.busy.set(true); try { this.question.set(await this.sessionService.startInterview(this.id)); } catch (e: any) { this.message.set(e.message || 'Interview unavailable.'); } finally { this.busy.set(false); } }
  async submitAnswer() { const current = this.question(); if (!current || !this.answerText.trim()) { this.message.set('Write an answer before submitting.'); return; } this.busy.set(true); try { const response = await this.sessionService.submitAnswer(this.id, current.instanceId, this.answerText); this.answerText = ''; if (response.nextQuestion) this.question.set(response.nextQuestion); else { this.question.set(null); this.router.navigate(['/sessions', this.id, 'report']); } } catch (e: any) { this.message.set(e.message || 'Answer unavailable.'); } finally { this.busy.set(false); } }
}
