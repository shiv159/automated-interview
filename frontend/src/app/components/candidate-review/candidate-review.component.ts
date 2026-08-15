import { Component, inject, signal, Input, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { SessionService, Session } from '../../services/session.service';

@Component({ selector: 'app-candidate-review', standalone: true, imports: [FormsModule, RouterLink], templateUrl: './candidate-review.component.html', styleUrl: './candidate-review.component.scss' })
export class CandidateReviewComponent implements OnInit {
  @Input() id?: string; private sessionService = inject(SessionService); private router = inject(Router);
  jobFile: File | null = null; resumeFile: File | null = null; yearsExperience = 3; syntheticDataAttested = false;
  readonly busy = signal(false); readonly message = signal(''); readonly result = signal<Session | null>(null);
  async ngOnInit() { if (this.id) { this.busy.set(true); try { this.result.set(await this.sessionService.getSession(this.id)); } catch (e) { this.message.set(e instanceof Error ? e.message : 'Session unavailable.'); } finally { this.busy.set(false); } } }
  evidenceText(claim: any): string { return claim?.evidence ?? ''; }
  private validCandidateFile(file: File): boolean { return file.size <= 2 * 1024 * 1024 && /\.(pdf|docx|txt)$/i.test(file.name); }
  async startReview() { this.message.set(''); this.result.set(null); if (!this.jobFile || !this.resumeFile || !this.syntheticDataAttested) { this.message.set('Choose both documents and confirm the synthetic-data attestation.'); return; } if (!this.validCandidateFile(this.jobFile) || !this.validCandidateFile(this.resumeFile)) { this.message.set('Use PDF, DOCX, or TXT files no larger than 2 MiB each.'); return; } this.busy.set(true); try { const session = await this.sessionService.createSession(this.jobFile, this.resumeFile, this.yearsExperience, this.syntheticDataAttested); this.result.set(session); this.router.navigate(['/sessions', session.id, 'analysis']); this.message.set('Your materials were analyzed.'); } catch (e: any) { this.message.set(e.message || 'Session creation failed.'); } finally { this.busy.set(false); } }
  async startInterview() { const session = this.result(); if (!session) return; this.busy.set(true); try { await this.sessionService.startInterview(session.id); this.router.navigate(['/sessions', session.id, 'interview']); } catch (e: any) { this.message.set(e.message || 'Interview unavailable.'); } finally { this.busy.set(false); } }
}
