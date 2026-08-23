import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

export interface QuestionSummary {
  id: string; stem: string; origin: 'SEED' | 'OWNER_IMPORT'; status: 'ACTIVE' | 'INACTIVE';
  type: 'TECHNICAL' | 'BEHAVIORAL'; primarySkill: string | null; difficulty: string | null;
  tags: string; rubric: string; idealAnswer: string | null; updatedAt: string;
}
export interface CoverageBucket { type: string; primarySkill: string | null; difficulty: string | null; status: string; count: number; }
export interface QuestionBank { questions: QuestionSummary[]; total: number; activeCount: number; skillAreaCount: number; coverage: CoverageBucket[]; }

@Injectable({ providedIn: 'root' })
export class QuestionBankService {
  private http = inject(HttpClient);

  async getBank(): Promise<QuestionBank> {
    const response = await firstValueFrom(this.http.get<QuestionBank>('/api/v1/question-bank'));
    return {
      ...response,
      activeCount: response.questions.filter((item: any) => item.status === 'ACTIVE').length
    };
  }

  async importQuestions(file: File): Promise<{ createdCount: number; updatedCount: number; questions: QuestionSummary[] }> {
    const body = new FormData();
    body.append('questionsFile', file);
    return firstValueFrom(this.http.post<{ createdCount: number; updatedCount: number; questions: QuestionSummary[] }>('/api/v1/question-bank/import', body));
  }

  async toggleStatus(question: QuestionSummary): Promise<void> {
    const status = question.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    return firstValueFrom(this.http.patch<void>(
      `/api/v1/question-bank/questions/${question.id}/status`,
      { status }
    ));
  }
}
