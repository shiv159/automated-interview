import { Component, inject, signal, Input, OnInit } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { Router, RouterLink } from "@angular/router";
import { SessionService, Session } from "../../services/session.service";
import { ApiErrorService } from "../../services/api-error.service";

@Component({
  selector: "app-candidate-review",
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: "./candidate-review.component.html",
  styleUrl: "./candidate-review.component.scss",
})
export class CandidateReviewComponent implements OnInit {
  @Input() id?: string;
  private sessionService = inject(SessionService);
  private apiErrors = inject(ApiErrorService);
  private router = inject(Router);
  jobFile: File | null = null;
  resumeFile: File | null = null;
  yearsExperience = 3;
  roleTitle = "";
  syntheticDataAttested = false;
  dragTarget = "";
  previewKind = "";
  previewText = "";
  previewTruncated = false;
  skillFilter = "ALL";
  readonly busy = signal(false);
  readonly message = signal("");
  readonly result = signal<Session | null>(null);
  async ngOnInit() {
    if (this.id) {
      this.busy.set(true);
      try {
        this.result.set(await this.sessionService.getSession(this.id));
      } catch (e) {
        this.message.set(this.apiErrors.message(e, "Session unavailable."));
      } finally {
        this.busy.set(false);
      }
    }
  }
  evidenceText(claim: any): string {
    return claim?.evidence ?? "";
  }
  loadDemo() {
    this.jobFile = new File(
      ["Role: Java / Spring engineer\nSkills: Java, Spring Boot, SQL"],
      "demo-java-spring-role.txt",
      { type: "text/plain" },
    );
    this.resumeFile = new File(
      ["Candidate: Demo Applicant\nExperience: 4 years Java and Spring"],
      "demo-resume.txt",
      { type: "text/plain" },
    );
    this.syntheticDataAttested = true;
    this.message.set("Demo materials loaded — ready to review.");
  }
  setFile(kind: "job" | "resume", event: Event) {
    const file = (event.target as HTMLInputElement).files?.[0] ?? null;
    if (kind === "job") this.jobFile = file;
    else this.resumeFile = file;
  }
  onDrop(kind: "job" | "resume", event: DragEvent) {
    event.preventDefault();
    this.dragTarget = "";
    const file = event.dataTransfer?.files?.[0] ?? null;
    if (kind === "job") this.jobFile = file;
    else this.resumeFile = file;
  }
  removeFile(kind: "job" | "resume") {
    if (kind === "job") this.jobFile = null;
    else this.resumeFile = null;
    if (this.previewKind === kind) this.previewKind = "";
  }
  async previewFile(kind: "job" | "resume") {
    const file = kind === "job" ? this.jobFile : this.resumeFile;
    if (!file) return;
    this.previewKind = kind;
    this.previewTruncated = false;
    try {
      if (file.type.includes("text") || file.name.endsWith(".txt")) {
        const text = await file.text();
        this.previewTruncated = text.length > 900;
        this.previewText = text.slice(0, 900);
      } else {
        const preview = await this.sessionService.previewDocument(file);
        this.previewText = preview.text;
        this.previewTruncated = preview.truncated;
      }
    } catch (e) {
      this.previewText = this.apiErrors.message(
        e,
        "Document preview unavailable.",
      );
    }
  }
  fileSize(file: File): string {
    return file.size < 1024 * 1024
      ? `${Math.max(1, Math.round(file.size / 1024))} KB`
      : `${(file.size / 1024 / 1024).toFixed(1)} MB`;
  }
  get filteredClaims(): any[] {
    const session = this.result();
    if (!session) return [];
    return session.jobSkills.filter(
      (claim: any) =>
        this.skillFilter === "ALL" ||
        (this.skillFilter === "MATCHED"
          ? session.matchedSkills.includes(claim.skillId)
          : session.missingSkills.includes(claim.skillId)),
    );
  }
  get matchedClaims(): any[] {
    const session = this.result();
    return session
      ? session.jobSkills.filter((claim) =>
          session.matchedSkills.includes(claim.skillId),
        )
      : [];
  }
  get missingClaims(): any[] {
    const session = this.result();
    return session
      ? session.jobSkills.filter((claim) =>
          session.missingSkills.includes(claim.skillId),
        )
      : [];
  }
  get additionalClaims(): any[] {
    const session = this.result();
    if (!session) return [];
    const jobIds = new Set(session.jobSkills.map((claim) => claim.skillId));
    return session.resumeSkills.filter((claim) => !jobIds.has(claim.skillId));
  }
  private validCandidateFile(file: File): boolean {
    return file.size <= 2 * 1024 * 1024 && /\.(pdf|docx|txt)$/i.test(file.name);
  }
  async startReview() {
    this.message.set("");
    this.result.set(null);
    if (!this.jobFile || !this.resumeFile || !this.syntheticDataAttested) {
      this.message.set(
        "Choose both documents and confirm the synthetic-data attestation.",
      );
      return;
    }
    if (
      !this.validCandidateFile(this.jobFile) ||
      !this.validCandidateFile(this.resumeFile)
    ) {
      this.message.set(
        "Use PDF, DOCX, or TXT files no larger than 2 MiB each.",
      );
      return;
    }
    if (
      !Number.isInteger(this.yearsExperience) ||
      this.yearsExperience < 0 ||
      this.yearsExperience > 30
    ) {
      this.message.set("Enter between 0 and 30 years of experience.");
      return;
    }
    this.busy.set(true);
    try {
      const session = await this.sessionService.createSession(
        this.jobFile,
        this.resumeFile,
        this.yearsExperience,
        this.syntheticDataAttested,
        this.roleTitle,
      );
      this.result.set(session);
      this.router.navigate(["/sessions", session.id, "analysis"]);
      this.message.set("Your materials were analyzed.");
    } catch (e: any) {
      this.message.set(this.apiErrors.message(e, "Session creation failed."));
    } finally {
      this.busy.set(false);
    }
  }
  async startInterview() {
    const session = this.result();
    if (!session) return;
    this.busy.set(true);
    try {
      await this.sessionService.startInterview(session.id);
      this.router.navigate(["/sessions", session.id, "interview"]);
    } catch (e: any) {
      this.message.set(this.apiErrors.message(e, "Interview unavailable."));
    } finally {
      this.busy.set(false);
    }
  }
}
