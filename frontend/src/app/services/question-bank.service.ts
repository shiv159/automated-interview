import { Injectable, inject } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { firstValueFrom } from "rxjs";

export interface QuestionSummary {
  id: string;
  stem: string;
  origin: "SEED" | "OWNER_IMPORT";
  status: "ACTIVE" | "INACTIVE";
  type: "TECHNICAL" | "BEHAVIORAL";
  primarySkill: string | null;
  secondarySkills: string;
  difficulty: string | null;
  tags: string;
  rubric: string;
  idealAnswer: string | null;
  updatedAt: string;
}
export interface CoverageBucket {
  type: string;
  primarySkill: string | null;
  difficulty: string | null;
  status: string;
  count: number;
}
export interface SkillOption {
  id: string;
  displayName: string;
  aliases: string[];
}
export interface QuestionBank {
  questions: QuestionSummary[];
  total: number;
  activeCount: number;
  skillAreaCount: number;
  coverage: CoverageBucket[];
  skills?: SkillOption[];
}
export interface AnalysisQuestion {
  stem: string;
  type: "TECHNICAL" | "BEHAVIORAL" | null;
  primarySkill: string | null;
  secondarySkills: string[];
  difficulty: string | null;
  tags: string[];
  idealAnswer: string | null;
  status: "VALID" | "INVALID" | "RETRYABLE" | "DUPLICATE";
  errorCode: string | null;
}
export interface SkillSuggestion {
  id: string;
  displayName: string;
  aliases: string[];
  questionIndexes: number[];
  approved?: boolean;
}
export interface QuestionAnalysis {
  questions: AnalysisQuestion[];
  newSkills: SkillSuggestion[];
  errors: { line?: number; item?: number; message: string }[];
}

@Injectable({ providedIn: "root" })
export class QuestionBankService {
  private http = inject(HttpClient);

  async getBank(): Promise<QuestionBank> {
    const response = await firstValueFrom(
      this.http.get<QuestionBank>("/api/v1/question-bank"),
    );
    return {
      ...response,
      activeCount: response.questions.filter(
        (item: any) => item.status === "ACTIVE",
      ).length,
    };
  }

  async importQuestions(
    file: File,
  ): Promise<{
    createdCount: number;
    updatedCount: number;
    skippedCount: number;
    questions: QuestionSummary[];
    errors: unknown[];
  }> {
    const body = new FormData();
    body.append("questionsFile", file);
    return firstValueFrom(
      this.http.post<{
        createdCount: number;
        updatedCount: number;
        skippedCount: number;
        questions: QuestionSummary[];
        errors: unknown[];
      }>("/api/v1/question-bank/import", body),
    );
  }

  async analyzeQuestions(file: File): Promise<QuestionAnalysis> {
    const body = new FormData();
    body.append("questionsFile", file);
    return firstValueFrom(
      this.http.post<QuestionAnalysis>("/api/v1/question-bank/analyze", body),
    );
  }

  async importDraft(payload: {
    questions: AnalysisQuestion[];
    approvedSkills: SkillSuggestion[];
  }): Promise<any> {
    return firstValueFrom(
      this.http.post("/api/v1/question-bank/import-draft", {
        questions: payload.questions.map(
          ({
            stem,
            type,
            primarySkill,
            secondarySkills,
            difficulty,
            tags,
            idealAnswer,
            status,
          }) => ({
            stem,
            type,
            primarySkill,
            secondarySkills,
            difficulty,
            tags,
            idealAnswer,
            status,
          }),
        ),
        approvedSkills: payload.approvedSkills.map(
          ({ id, displayName, aliases }) => ({ id, displayName, aliases }),
        ),
      }),
    );
  }

  async toggleStatus(question: QuestionSummary): Promise<void> {
    const status = question.status === "ACTIVE" ? "INACTIVE" : "ACTIVE";
    return firstValueFrom(
      this.http.patch<void>(
        `/api/v1/question-bank/questions/${question.id}/status`,
        { status },
      ),
    );
  }
}
