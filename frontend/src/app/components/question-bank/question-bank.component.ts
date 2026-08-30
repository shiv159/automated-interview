import { Component, inject, signal, OnInit } from "@angular/core";
import { DatePipe } from "@angular/common";
import { RouterLink } from "@angular/router";
import { FormsModule } from "@angular/forms";
import { HttpErrorResponse } from "@angular/common/http";
import { ApiErrorService } from "../../services/api-error.service";
import { AdminKeyService } from "../../services/admin-key.service";
import {
  AnalysisQuestion,
  QuestionBank,
  QuestionBankService,
  QuestionSummary,
  SkillSuggestion,
} from "../../services/question-bank.service";

@Component({
  selector: "app-question-bank",
  standalone: true,
  imports: [DatePipe, RouterLink, FormsModule],
  templateUrl: "./question-bank.component.html",
  styleUrl: "./question-bank.component.scss",
})
export class QuestionBankComponent implements OnInit {
  private questionBankService = inject(QuestionBankService);
  private apiErrors = inject(ApiErrorService);
  private adminKey = inject(AdminKeyService);

  readonly busy = signal(false);
  readonly message = signal("");
  readonly bank = signal<QuestionBank | null>(null);
  readonly draft = signal<AnalysisQuestion[]>([]);
  readonly suggestions = signal<SkillSuggestion[]>([]);
  readonly page = signal(0);
  readonly pageSize = 50;

  // Admin key modal
  readonly showKeyModal = signal(false);
  readonly keyInput = signal("");
  readonly keyError = signal("");

  ownerFile: File | null = null;
  searchTerm = "";
  skillFilter = "ALL";
  difficultyFilter = "ALL";
  originFilter = "ALL";
  selectedQuestion: QuestionSummary | null = null;

  async ngOnInit() {
    if (!this.adminKey.hasKey()) {
      this.showKeyModal.set(true);
    } else {
      await this.loadBank();
    }
  }

  submitKey() {
    const key = this.keyInput().trim();
    if (!key) {
      this.keyError.set("Please enter an API key.");
      return;
    }
    this.adminKey.setKey(key);
    this.showKeyModal.set(false);
    this.keyError.set("");
    this.loadBank();
  }

  async loadBank() {
    try {
      this.bank.set(await this.questionBankService.getBank({ page: this.page(), size: this.pageSize, search: this.searchTerm, skill: this.skillFilter, difficulty: this.difficultyFilter, origin: this.originFilter }));
    } catch (e) {
      this.bank.set(null);
      if (e instanceof HttpErrorResponse && e.status === 401) {
        this.keyError.set("Invalid key — try again.");
        this.keyInput.set("");
        this.showKeyModal.set(true);
      } else {
        this.message.set(this.apiErrors.message(e, "Question bank unavailable."));
      }
    }
  }

  async applyFilters() {
    this.page.set(0);
    await this.loadBank();
  }

  async changePage(delta: number) {
    const next = this.page() + delta;
    const totalPages = this.bank()?.totalPages ?? 0;
    if (next < 0 || next >= totalPages) return;
    this.page.set(next);
    await this.loadBank();
  }

  get skillOptions(): string[] {
    const skills = new Set(
      this.bank()?.skills?.map((skill) => skill.id) ??
        (this.bank()?.questions ?? []).map(
          (q) => q.primarySkill ?? "BEHAVIORAL",
        ),
    );
    return ["ALL", ...Array.from(skills).sort()];
  }

  skillDisplayNames(): Record<string, string> {
    return Object.fromEntries(
      (this.bank()?.skills ?? []).map((skill) => [skill.id, skill.displayName]),
    );
  }

  get filteredQuestions(): QuestionSummary[] {
    return this.bank()?.questions ?? [];
  }

  displaySkill(skill: string): string {
    return skill === "ALL"
      ? "All Skills"
      : skill === "BEHAVIORAL"
        ? "Behavioral"
        : (this.skillDisplayNames()[skill] ?? skill.replaceAll("_", " "));
  }
  formatTags(value: unknown): string {
    if (Array.isArray(value)) return value.join(", ");
    if (typeof value !== "string") return "";
    try {
      const parsed = JSON.parse(value);
      return Array.isArray(parsed) ? parsed.join(", ") : value;
    } catch {
      return value;
    }
  }

  async importQuestions() {
    if (!this.ownerFile) {
      this.message.set("Choose a TXT or JSON file first.");
      return;
    }
    const name = this.ownerFile.name.toLowerCase();
    if (
      this.ownerFile.size > 65536 ||
      (!name.endsWith(".txt") && !name.endsWith(".json"))
    ) {
      this.message.set(
        "Choose a UTF-8 TXT or JSON file no larger than 64 KiB.",
      );
      return;
    }
    this.busy.set(true);
    this.message.set("");
    try {
      const payload = await this.questionBankService.analyzeQuestions(
        this.ownerFile,
      );
      this.draft.set(payload.questions);
      this.suggestions.set(
        payload.newSkills.map((skill) => ({ ...skill, approved: true })),
      );
      this.message.set(
        "Review the detected skills, then import valid questions.",
      );
    } catch (e: unknown) {
      this.message.set(this.apiErrors.message(e, "Import failed."));
    } finally {
      this.busy.set(false);
    }
  }

  async retryAnalysis() {
    if (!this.ownerFile) return;
    this.busy.set(true);
    this.message.set("");
    try {
      const payload = await this.questionBankService.analyzeQuestions(
        this.ownerFile,
      );
      this.draft.set(payload.questions);
      this.suggestions.set(
        payload.newSkills.map((skill) => ({ ...skill, approved: true })),
      );
      this.message.set(
        "Analysis retried. Review the results before importing.",
      );
    } catch (e: unknown) {
      this.message.set(this.apiErrors.message(e, "Analysis retry failed."));
    } finally {
      this.busy.set(false);
    }
  }

  async commitDraft() {
    const questions = this.draft();
    if (!questions.length) return;
    this.busy.set(true);
    this.message.set("");
    try {
      const payload = await this.questionBankService.importDraft({
        questions,
        approvedSkills: this.suggestions().filter((skill) => skill.approved),
      });
      this.message.set(
        `Imported ${payload.createdCount} new, ${payload.updatedCount} updated, and ${payload.skippedCount} skipped questions.`,
      );
      this.draft.set([]);
      this.suggestions.set([]);
      await this.loadBank();
    } catch (e: unknown) {
      this.message.set(this.apiErrors.message(e, "Import failed."));
    } finally {
      this.busy.set(false);
    }
  }

  toggleSuggestion(skill: SkillSuggestion) {
    skill.approved = !skill.approved;
    this.suggestions.set([...this.suggestions()]);
  }
  updateSuggestionAliases(skill: SkillSuggestion, value: string) {
    skill.aliases = value
      .split(",")
      .map((alias) => alias.trim())
      .filter(Boolean);
    this.suggestions.set([...this.suggestions()]);
  }

  async toggleStatus(question: QuestionSummary) {
    this.busy.set(true);
    try {
      await this.questionBankService.toggleStatus(question);
      await this.loadBank();
    } catch (e: unknown) {
      this.message.set(this.apiErrors.message(e, "Status update failed."));
    } finally {
      this.busy.set(false);
    }
  }

  copyQuestion(question: QuestionSummary) {
    navigator.clipboard?.writeText(question.stem);
    this.message.set("Question copied to clipboard.");
  }

  selectedFile(event: Event): File | null {
    return (event.target as HTMLInputElement).files?.[0] ?? null;
  }

  inputValue(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  async exportQuestions(format: "json" | "csv") {
    const content = await this.questionBankService.exportQuestions(format, { search: this.searchTerm, skill: this.skillFilter, difficulty: this.difficultyFilter, origin: this.originFilter });
    const link = document.createElement("a");
    link.href = URL.createObjectURL(
      new Blob([content], {
        type: format === "json" ? "application/json" : "text/csv",
      }),
    );
    link.download = `intervu-question-bank.${format}`;
    link.click();
    URL.revokeObjectURL(link.href);
  }
}
