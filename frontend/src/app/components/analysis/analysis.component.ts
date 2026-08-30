import { Component, Input, OnInit, inject, signal } from "@angular/core";
import { Router, RouterLink } from "@angular/router";
import { ApiErrorService } from "../../services/api-error.service";
import { Session, SessionService, SkillClaim } from "../../services/session.service";

@Component({
  selector: "app-analysis",
  standalone: true,
  imports: [RouterLink],
  templateUrl: "./analysis.component.html",
  styleUrl: "./analysis.component.scss",
})
export class AnalysisComponent implements OnInit {
  @Input() id!: string;
  private readonly sessionService = inject(SessionService);
  private readonly apiErrors = inject(ApiErrorService);
  private readonly router = inject(Router);
  readonly busy = signal(true);
  readonly message = signal("");
  readonly session = signal<Session | null>(null);

  async ngOnInit() {
    if (!this.id) return this.router.navigate(["/"]).then(() => undefined);
    try {
      this.session.set(await this.sessionService.getSession(this.id));
    } catch (e: unknown) {
      this.message.set(this.apiErrors.message(e, "Analysis unavailable."));
    } finally {
      this.busy.set(false);
    }
  }

  claims(kind: "matched" | "missing" | "additional"): SkillClaim[] {
    const session = this.session();
    if (!session) return [];
    if (kind === "matched") return session.jobSkills.filter((c) => session.matchedSkills.includes(c.skillId));
    if (kind === "missing") return session.jobSkills.filter((c) => session.missingSkills.includes(c.skillId));
    const jobIds = new Set(session.jobSkills.map((c) => c.skillId));
    return session.resumeSkills.filter((c) => !jobIds.has(c.skillId));
  }

  async startInterview() {
    const session = this.session();
    if (!session) return;
    this.busy.set(true);
    try {
      await this.sessionService.startInterview(session.id);
      await this.router.navigate(["/sessions", session.id, "interview"]);
    } catch (e: unknown) {
      this.message.set(this.apiErrors.message(e, "Interview unavailable."));
      this.busy.set(false);
    }
  }
}
