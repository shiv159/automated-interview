import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

@Injectable({ providedIn: 'root' })
export class QuestionBankService {
  private http = inject(HttpClient);

  async getBank(): Promise<any> {
    const response = await firstValueFrom(this.http.get<any>('/api/v1/question-bank'));
    return {
      ...response,
      activeCount: response.questions.filter((item: any) => item.status === 'ACTIVE').length
    };
  }

  async importQuestions(file: File): Promise<any> {
    const body = new FormData();
    body.append('questionsFile', file);
    return firstValueFrom(this.http.post<any>('/api/v1/question-bank/import', body));
  }

  async toggleStatus(question: any): Promise<void> {
    const status = question.status === 'ACTIVE' ? 'INACTIVE' : 'ACTIVE';
    return firstValueFrom(this.http.patch<void>(
      `/api/v1/question-bank/questions/${question.id}/status`,
      { status }
    ));
  }
}
