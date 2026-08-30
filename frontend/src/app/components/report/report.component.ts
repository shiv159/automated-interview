import { Component, inject, signal, Input, OnInit } from "@angular/core";
import { DecimalPipe } from "@angular/common";
import { Router, RouterLink } from "@angular/router";
import {
  SessionService,
  Report,
  SkillClaim,
} from "../../services/session.service";
import { ApiErrorService } from "../../services/api-error.service";

@Component({
  selector: "app-report",
  standalone: true,
  imports: [DecimalPipe, RouterLink],
  templateUrl: "./report.component.html",
  styleUrl: "./report.component.scss",
})
export class ReportComponent implements OnInit {
  @Input() id!: string;
  private sessionService = inject(SessionService);
  private apiErrors = inject(ApiErrorService);
  private router = inject(Router);
  readonly busy = signal(false);
  readonly message = signal("");
  readonly report = signal<Report | null>(null);
  expanded = new Set<number>();
  readonly profileWeight = 0.3;
  readonly interviewWeight = 0.7;
  readonly technicalWeight = 0.8;
  readonly behavioralWeight = 0.2;
  async ngOnInit() {
    if (!this.id) {
      this.router.navigate(["/"]);
      return;
    }
    this.busy.set(true);
    try {
      this.report.set(await this.sessionService.getReport(this.id));
    } catch (e: any) {
      this.message.set(this.apiErrors.message(e, "Report unavailable."));
    } finally {
      this.busy.set(false);
    }
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
  downloadReport() {
    const value = this.report();
    if (!value) return;
    this.download(
      `interview-report-${value.sessionId}.json`,
      JSON.stringify(value, null, 2),
      "application/json",
    );
    const headers = [
      "recordType",
      "sessionId",
      "roleTitle",
      "profileMatch",
      "technicalScore",
      "behavioralScore",
      "interviewScore",
      "readinessScore",
      "readinessLabel",
      "skillId",
      "skillSource",
      "skillImportance",
      "skillMatched",
      "skillEvidence",
      "position",
      "type",
      "primarySkill",
      "stem",
      "criteria",
      "criteriaScores",
      "score",
      "strengths",
      "improvements",
    ];
    const rows: string[][] = [headers];
    const addSkill = (source: string, claim: SkillClaim) =>
      rows.push(
        [
          "skill",
          value.sessionId,
          value.roleTitle,
          value.profileMatch,
          value.technicalScore,
          value.behavioralScore,
          value.interviewScore,
          value.readinessScore,
          value.readinessLabel,
          claim.skillId,
          source,
          claim.importance,
          claim.matched,
          claim.evidence,
          "",
          "",
          "",
          "",
          "",
          "",
          "",
          "",
          "",
        ].map(String),
      );
    for (const claim of value.jobSkills ?? []) addSkill("job", claim);
    for (const claim of value.resumeSkills ?? []) addSkill("resume", claim);
    for (const skill of value.unsupportedJobSkills ?? [])
      rows.push(
        [
          "unsupported",
          value.sessionId,
          value.roleTitle,
          value.profileMatch,
          value.technicalScore,
          value.behavioralScore,
          value.interviewScore,
          value.readinessScore,
          value.readinessLabel,
          skill,
          "job",
          "",
          "",
          "",
          "",
          "",
          "",
          "",
          "",
          "",
          "",
          "",
          "",
        ].map(String),
      );
    for (const evaluation of value.evaluations)
      rows.push(
        [
          "evaluation",
          value.sessionId,
          value.roleTitle,
          value.profileMatch,
          value.technicalScore,
          value.behavioralScore,
          value.interviewScore,
          value.readinessScore,
          value.readinessLabel,
          "",
          "",
          "",
          "",
          "",
          evaluation.position,
          evaluation.type,
          evaluation.primarySkill ?? "",
          evaluation.stem,
          evaluation.criteria,
          JSON.stringify(evaluation.criteriaScores ?? []),
          evaluation.score,
          this.formatTags(evaluation.strengths),
          this.formatTags(evaluation.improvements),
        ].map(String),
      );
    this.download(
      `interview-report-${value.sessionId}.csv`,
      rows
        .map((row) =>
          row.map((cell) => `"${cell.replaceAll('"', '""')}"`).join(","),
        )
        .join("\n"),
      "text/csv",
    );
  }
  private download(name: string, content: string, type: string) {
    const link = document.createElement("a");
    link.href = URL.createObjectURL(new Blob([content], { type }));
    link.download = name;
    link.click();
    URL.revokeObjectURL(link.href);
  }
  printReport() {
    window.print();
  }
  toggleEvaluation(position: number) {
    this.expanded.has(position)
      ? this.expanded.delete(position)
      : this.expanded.add(position);
  }
  isExpanded(position: number) {
    return this.expanded.has(position);
  }
  ringDash(value: number, max: number) {
    const percent = Math.max(0, Math.min(100, (value / max) * 100));
    return `${percent} ${100 - percent}`;
  }
  async deleteSession() {
    if (!this.id) return;
    this.busy.set(true);
    try {
      await this.sessionService.deleteSession(this.id);
      this.report.set(null);
      this.router.navigate(["/"]);
    } catch (e: any) {
      this.message.set(this.apiErrors.message(e, "Failed to delete session."));
    } finally {
      this.busy.set(false);
    }
  }
}
