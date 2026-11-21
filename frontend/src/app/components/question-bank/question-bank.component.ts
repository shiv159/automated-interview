import { Component, ElementRef, inject, OnDestroy, OnInit, signal, ViewChild } from "@angular/core";
import { DatePipe } from "@angular/common";
import { RouterLink } from "@angular/router";
import { FormsModule } from "@angular/forms";
import { HttpErrorResponse } from "@angular/common/http";
import { ApiErrorService } from "../../services/api-error.service";
import { AdminKeyService } from "../../services/admin-key.service";
import {
  AnalysisQuestion,
  QuestionAnalysis,
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
export class QuestionBankComponent implements OnDestroy, OnInit {
  private questionBankService = inject(QuestionBankService);
  private apiErrors = inject(ApiErrorService);
  private adminKey = inject(AdminKeyService);

  readonly busy = signal(false);
  readonly loading = signal(false);
  readonly loadError = signal(false);
  readonly message = signal("");
  readonly messageKind = signal<"info" | "success" | "error">("info");
  readonly bank = signal<QuestionBank | null>(null);
  readonly draft = signal<AnalysisQuestion[]>([]);
  readonly suggestions = signal<SkillSuggestion[]>([]);
  readonly analysisErrors = signal<QuestionAnalysis["errors"]>([]);
  readonly page = signal(0);
  readonly pageSize = 50;
  readonly lastStatusChange = signal<{
    questionId: string;
    previousStatus: "ACTIVE" | "INACTIVE";
  } | null>(null);

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
  private loadRequestId = 0;
  private searchTimer: ReturnType<typeof setTimeout> | null = null;
  private questionReturnFocusTarget: HTMLElement | null = null;

  @ViewChild("questionDialog") private questionDialog?: ElementRef<HTMLElement>;

  async ngOnInit(): Promise<void> {
    if (!this.adminKey.hasKey()) {
      this.showKeyModal.set(true);
    } else {
      await this.loadBank();
    }
  }

  ngOnDestroy(): void {
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.loadRequestId++;
  }

  submitKey(): void {
    const key = this.keyInput().trim();
    if (!key) {
      this.keyError.set("Please enter an API key.");
      return;
    }
    this.adminKey.setKey(key);
    this.showKeyModal.set(false);
    this.keyError.set("");
    void this.loadBank();
  }

  async loadBank(): Promise<void> {
    const requestId = ++this.loadRequestId;
    this.loading.set(true);
    this.loadError.set(false);
    if (this.messageKind() === "error") this.clearMessage();
    try {
      const bank = await this.questionBankService.getBank({
        page: this.page(),
        size: this.pageSize,
        search: this.searchTerm,
        skill: this.skillFilter,
        difficulty: this.difficultyFilter,
        origin: this.originFilter,
      });
      if (requestId !== this.loadRequestId) return;
      this.bank.set(bank);
    } catch (error: unknown) {
      if (requestId !== this.loadRequestId) return;
      this.loadError.set(true);
      if (error instanceof HttpErrorResponse && error.status === 401) {
        this.bank.set(null);
        this.keyError.set("Invalid key — try again.");
        this.keyInput.set("");
        this.showKeyModal.set(true);
      } else {
        this.setMessage(this.apiErrors.message(error, "Question bank unavailable."), "error");
      }
    } finally {
      if (requestId === this.loadRequestId) this.loading.set(false);
    }
  }

  async applyFilters(): Promise<void> {
    if (this.searchTimer) {
      clearTimeout(this.searchTimer);
      this.searchTimer = null;
    }
    this.page.set(0);
    await this.loadBank();
  }

  scheduleSearch(): void {
    if (this.searchTimer) clearTimeout(this.searchTimer);
    this.loadRequestId++;
    this.loading.set(true);
    this.loadError.set(false);
    this.searchTimer = setTimeout(() => {
      this.searchTimer = null;
      void this.applyFilters();
    }, 250);
  }

  clearFilters(): void {
    this.searchTerm = "";
    this.skillFilter = "ALL";
    this.difficultyFilter = "ALL";
    this.originFilter = "ALL";
    void this.applyFilters();
  }

  get hasActiveFilters(): boolean {
    return Boolean(this.searchTerm) || this.skillFilter !== "ALL" ||
      this.difficultyFilter !== "ALL" || this.originFilter !== "ALL";
  }

  async changePage(delta: number): Promise<void> {
    const next = (this.bank()?.page ?? this.page()) + delta;
    const totalPages = this.bank()?.totalPages ?? 0;
    if (next < 0 || next >= totalPages) return;
    this.page.set(next);
    await this.loadBank();
  }

  get skillOptions(): string[] {
    const skills = new Set(
      this.bank()?.skills?.map((skill) => skill.id) ??
        (this.bank()?.questions ?? []).map((question) => question.primarySkill ?? "BEHAVIORAL"),
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

  indexingLabel(status: QuestionSummary["indexingStatus"]): string {
    return status === "INDEXED" ? "Ready" : status === "PROCESSING" ? "Indexing" : status === "FAILED" ? "Failed" : "Queued";
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

  async importQuestions(): Promise<void> {
    await this.analyzeQuestions(false);
  }

  async retryAnalysis(): Promise<void> {
    await this.analyzeQuestions(true);
  }

  private async analyzeQuestions(retry: boolean): Promise<void> {
    if (!this.ownerFile) {
      if (!retry) this.setMessage("Choose a TXT or JSON file first.", "error");
      return;
    }
    const name = this.ownerFile.name.toLowerCase();
    if (
      this.ownerFile.size > 65536 ||
      (!name.endsWith(".txt") && !name.endsWith(".json"))
    ) {
      this.setMessage("Choose a UTF-8 TXT or JSON file no larger than 64 KiB.", "error");
      return;
    }
    this.busy.set(true);
    this.clearMessage();
    this.analysisErrors.set([]);
    try {
      const payload = await this.questionBankService.analyzeQuestions(this.ownerFile);
      this.draft.set(payload.questions);
      this.analysisErrors.set(payload.errors);
      this.suggestions.set(payload.newSkills.map((skill) => ({ ...skill, approved: true })));
      this.setMessage(
        retry
          ? "Analysis retried. Review the results before importing."
          : "Review the detected skills, then import valid questions.",
        "info",
      );
    } catch (error: unknown) {
      this.setMessage(
        this.apiErrors.message(error, retry ? "Analysis retry failed." : "Import failed."),
        "error",
      );
    } finally {
      this.busy.set(false);
    }
  }

  async commitDraft(): Promise<void> {
    const questions = this.draft();
    if (!questions.length) return;
    this.busy.set(true);
    this.clearMessage();
    try {
      const payload = await this.questionBankService.importDraft({
        questions,
        approvedSkills: this.suggestions().filter((skill) => skill.approved),
      });
      this.setMessage(
        `Imported ${payload.createdCount} new, ${payload.updatedCount} updated, and ${payload.skippedCount} skipped questions.`,
        "success",
      );
      this.draft.set([]);
      this.suggestions.set([]);
      this.analysisErrors.set([]);
      await this.loadBank();
    } catch (error: unknown) {
      this.setMessage(this.apiErrors.message(error, "Import failed."), "error");
    } finally {
      this.busy.set(false);
    }
  }

  clearDraft(): void {
    this.draft.set([]);
    this.suggestions.set([]);
    this.analysisErrors.set([]);
    this.setMessage("Import review discarded. Your selected file is still ready to analyze.", "info");
  }

  toggleSuggestion(skill: SkillSuggestion): void {
    skill.approved = !skill.approved;
    this.suggestions.set([...this.suggestions()]);
  }

  updateSuggestionAliases(skill: SkillSuggestion, value: string): void {
    skill.aliases = value.split(",").map((alias) => alias.trim()).filter(Boolean);
    this.suggestions.set([...this.suggestions()]);
  }

  async toggleStatus(question: QuestionSummary): Promise<void> {
    this.busy.set(true);
    const previousStatus = question.status;
    const nextStatus = previousStatus === "ACTIVE" ? "INACTIVE" : "ACTIVE";
    try {
      await this.questionBankService.setStatus(question.id, nextStatus);
      this.lastStatusChange.set({ questionId: question.id, previousStatus });
      this.setMessage(`Question ${nextStatus === "ACTIVE" ? "activated" : "deactivated"}.`, "success", false);
      await this.loadBank();
    } catch (error: unknown) {
      this.setMessage(this.apiErrors.message(error, "Status update failed."), "error");
    } finally {
      this.busy.set(false);
    }
  }

  async undoStatusChange(): Promise<void> {
    const change = this.lastStatusChange();
    if (!change) return;
    this.busy.set(true);
    try {
      await this.questionBankService.setStatus(change.questionId, change.previousStatus);
      this.lastStatusChange.set(null);
      this.setMessage(`Question ${change.previousStatus === "ACTIVE" ? "activated" : "deactivated"} again.`, "success");
      await this.loadBank();
    } catch (error: unknown) {
      this.setMessage(this.apiErrors.message(error, "Could not undo the status change."), "error");
    } finally {
      this.busy.set(false);
    }
  }

  async copyQuestion(question: QuestionSummary): Promise<void> {
    try {
      if (!navigator.clipboard?.writeText) throw new Error("Clipboard access is unavailable.");
      await navigator.clipboard.writeText(question.stem);
      this.setMessage("Question copied to clipboard.", "success");
    } catch {
      this.setMessage("Could not copy this question. Select its text in the details view instead.", "error");
    }
  }

  analysisIssue(question: AnalysisQuestion): string {
    if (question.status === "DUPLICATE") return "Already in the question bank.";
    if (question.status === "RETRYABLE") return "Analysis was temporarily unavailable. Retry the analysis.";
    if (question.errorCode === "QUESTION_SKILL_AMBIGUOUS") return "Could not identify one technical skill. Make the skill clear in the question.";
    if (question.errorCode === "INVALID_QUESTION_STEM") return "Question is too short or looks like a placeholder.";
    return question.errorCode ? "This question needs changes before it can be imported." : "Ready to import.";
  }

  selectedFile(event: Event): File | null {
    return (event.target as HTMLInputElement).files?.[0] ?? null;
  }

  inputValue(event: Event): string {
    return (event.target as HTMLInputElement).value;
  }

  openQuestionDetail(question: QuestionSummary, event: Event): void {
    this.questionReturnFocusTarget = event.currentTarget as HTMLElement;
    this.selectedQuestion = question;
    setTimeout(() => this.questionDialog?.nativeElement.querySelector<HTMLElement>("[data-dialog-close]")?.focus());
  }

  closeQuestionDetail(): void {
    this.selectedQuestion = null;
    const target = this.questionReturnFocusTarget;
    this.questionReturnFocusTarget = null;
    setTimeout(() => target?.focus());
  }

  questionDialogKeydown(event: KeyboardEvent): void {
    if (event.key === "Escape") {
      event.preventDefault();
      this.closeQuestionDetail();
      return;
    }
    this.trapTab(event, this.questionDialog?.nativeElement);
  }

  keyDialogKeydown(event: KeyboardEvent, dialog: HTMLElement): void {
    this.trapTab(event, dialog);
  }

  private trapTab(event: KeyboardEvent, dialog?: HTMLElement): void {
    if (event.key !== "Tab" || !dialog) return;
    const focusable = Array.from(dialog.querySelectorAll<HTMLElement>(
      'button:not(:disabled), input:not(:disabled), textarea:not(:disabled), select:not(:disabled), [tabindex]:not([tabindex="-1"])',
    )).filter((element) => element.offsetWidth > 0 || element.offsetHeight > 0);
    if (!focusable.length) {
      event.preventDefault();
      dialog.focus();
      return;
    }
    const first = focusable[0];
    const last = focusable[focusable.length - 1];
    if (event.shiftKey && (document.activeElement === first || document.activeElement === dialog)) {
      event.preventDefault();
      last.focus();
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault();
      first.focus();
    }
  }

  private setMessage(text: string, kind: "info" | "success" | "error", clearUndo = true): void {
    if (clearUndo) this.lastStatusChange.set(null);
    this.message.set(text);
    this.messageKind.set(kind);
  }

  private clearMessage(): void {
    this.message.set("");
    this.messageKind.set("info");
    this.lastStatusChange.set(null);
  }

  async exportQuestions(format: "json" | "csv"): Promise<void> {
    try {
      const content = await this.questionBankService.exportQuestions(format, {
        search: this.searchTerm,
        skill: this.skillFilter,
        difficulty: this.difficultyFilter,
        origin: this.originFilter,
      });
      const link = document.createElement("a");
      link.href = URL.createObjectURL(new Blob([content], {
        type: format === "json" ? "application/json" : "text/csv",
      }));
      link.download = `intervu-question-bank.${format}`;
      link.click();
      URL.revokeObjectURL(link.href);
      this.setMessage(`${format.toUpperCase()} export downloaded.`, "success");
    } catch (error: unknown) {
      this.setMessage(this.apiErrors.message(error, `Could not export ${format.toUpperCase()}. Try again.`), "error");
    }
  }
}
