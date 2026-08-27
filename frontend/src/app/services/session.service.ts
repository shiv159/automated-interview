import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

export interface Session {
  id: string;
  expiresAt: string;
  roleTitle: string;
  profileMatch: number;
  difficulty: string;
  matchedSkills: string[];
  missingSkills: string[];
  unsupportedJobSkills: string[];
  softSkillRequirements: string[];
  domainRequirements: string[];
  jobSkills: SkillClaim[];
  resumeSkills: SkillClaim[];
}

export interface SkillClaim { skillId: string; importance: string; evidence: string; matched: boolean; }

export interface Question {
  instanceId: string;
  position: number;
  totalQuestions: number;
  roleTitle: string;
  type: 'TECHNICAL' | 'BEHAVIORAL';
  primarySkill?: string | null;
  difficulty?: string | null;
  stem: string;
  criteria: string;
  guidance: string;
}

export interface Evaluation {
  position: number;
  type: 'TECHNICAL' | 'BEHAVIORAL';
  primarySkill?: string | null;
  stem: string;
  criteria: string;
  score: number;
  strengths: string | string[];
  improvements: string | string[];
  criteriaScores?: CriterionScore[];
}

export interface CriterionScore { criterion: string; score: number; feedback?: string; }

export interface Report {
  sessionId: string;
  roleTitle: string;
  profileMatch: number;
  technicalScore: number;
  behavioralScore: number;
  readinessLabel: string;
  readinessScore: number;
  interviewScore: number;
  expiresAt: string;
  evaluations: Evaluation[];
  jobSkills?: SkillClaim[];
  resumeSkills?: SkillClaim[];
  matchedSkills?: string[];
  missingSkills?: string[];
  unsupportedJobSkills?: string[];
  softSkillRequirements?: string[];
  domainRequirements?: string[];
}

@Injectable({ providedIn: 'root' })
export class SessionService {
  private http = inject(HttpClient);

  async createSession(jobFile: File, resumeFile: File, yearsExperience: number, syntheticDataAttested: boolean, roleTitle = ''): Promise<Session> {
    const body = new FormData();
    body.append('jobDescription', jobFile);
    body.append('resume', resumeFile);
    body.append('yearsExperience', String(yearsExperience));
    body.append('syntheticDataAttested', String(syntheticDataAttested));
    body.append('roleTitle', roleTitle);
    
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

  async previewDocument(file: File): Promise<{ text: string; truncated: boolean; documentType: string }> {
    const body = new FormData();
    body.append('file', file);
    return firstValueFrom(this.http.post<{ text: string; truncated: boolean; documentType: string }>('/api/v1/documents/preview', body));
  }

  async getReport(sessionId: string): Promise<Report> {
    return firstValueFrom(this.http.get<Report>(`/api/v1/sessions/${sessionId}/report`, { withCredentials: true }));
  }

  async deleteSession(sessionId: string): Promise<void> {
    return firstValueFrom(this.http.delete<void>(`/api/v1/sessions/${sessionId}`, { withCredentials: true }));
  }
}
