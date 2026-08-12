import { Injectable, inject, resource, signal, ResourceStatus } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

export interface Session {
  id: string;
  profileMatch: number;
  difficulty: string;
  matchedSkills: string[];
  missingSkills: string[];
  jobSkills: any[];
}

export interface Question {
  instanceId: string;
  position: number;
  stem: string;
}

export interface Report {
  sessionId: string;
  readinessLabel: string;
  readinessScore: number;
  interviewScore: number;
  evaluations: any[];
}

@Injectable({ providedIn: 'root' })
export class SessionService {
  private http = inject(HttpClient);

  async createSession(jobFile: File, resumeFile: File, yearsExperience: number, syntheticDataAttested: boolean): Promise<Session> {
    const body = new FormData();
    body.append('jobDescription', jobFile);
    body.append('resume', resumeFile);
    body.append('yearsExperience', String(yearsExperience));
    body.append('syntheticDataAttested', String(syntheticDataAttested));
    
    return firstValueFrom(this.http.post<Session>('/api/v1/sessions', body, { withCredentials: true }));
  }

  async getSession(id: string): Promise<Session> {
    return firstValueFrom(this.http.get<Session>(`/api/v1/sessions/${id}`, { withCredentials: true }));
  }

  async startInterview(sessionId: string): Promise<Question> {
    return firstValueFrom(this.http.post<Question>(`/api/v1/sessions/${sessionId}/interview`, {}, { withCredentials: true }));
  }

  async submitAnswer(sessionId: string, instanceId: string, answerText: string): Promise<{ nextQuestion: Question | null }> {
    return firstValueFrom(this.http.post<{ nextQuestion: Question | null }>(
      `/api/v1/sessions/${sessionId}/questions/${instanceId}/answers`,
      { answer: answerText },
      { withCredentials: true }
    ));
  }

  async getReport(sessionId: string): Promise<Report> {
    return firstValueFrom(this.http.get<Report>(`/api/v1/sessions/${sessionId}/report`, { withCredentials: true }));
  }

  async deleteSession(sessionId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api/v1/sessions/${sessionId}`, { withCredentials: true }));
  }
}
