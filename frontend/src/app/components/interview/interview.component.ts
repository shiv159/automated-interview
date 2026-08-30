import {
  Component,
  inject,
  signal,
  Input,
  OnInit,
  OnDestroy,
} from "@angular/core";
import { FormsModule } from "@angular/forms";
import { Router, RouterLink } from "@angular/router";
import { ApiErrorService } from "../../services/api-error.service";
import { SessionService, Question } from "../../services/session.service";

@Component({
  selector: "app-interview",
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: "./interview.component.html",
  styleUrl: "./interview.component.scss",
})
export class InterviewComponent implements OnInit, OnDestroy {
  @Input() id!: string;
  private sessionService = inject(SessionService);
  private apiErrors = inject(ApiErrorService);
  private router = inject(Router);
  readonly busy = signal(false);
  readonly message = signal("");
  readonly question = signal<Question | null>(null);
  answerText = "";
  elapsedSeconds = 0;
  timerRunning = true;
  focusMode = false;
  listening = false;
  showStar = false;
  private timerId?: number;
  private recognition: any;
  private dictationBase = "";
  private finalizedTranscript = "";
  private interimTranscript = "";

  async ngOnInit() {
    if (!this.id) {
      this.router.navigate(["/"]);
      return;
    }
    this.startTimer();
    this.busy.set(true);
    try {
      this.question.set(await this.sessionService.startInterview(this.id));
    } catch (e: any) {
      this.message.set(this.apiErrors.message(e, "Interview unavailable."));
    } finally {
      this.busy.set(false);
    }
  }
  ngOnDestroy() {
    if (this.timerId) window.clearInterval(this.timerId);
    this.recognition?.stop();
  }
  startTimer() {
    this.timerId = window.setInterval(() => {
      if (this.timerRunning) this.elapsedSeconds++;
    }, 1000);
  }
  get timerLabel() {
    return `${String(Math.floor(this.elapsedSeconds / 60)).padStart(2, "0")}:${String(this.elapsedSeconds % 60).padStart(2, "0")}`;
  }
  get completionPercent() {
    const current = this.question();
    return current
      ? Math.round(
          (this.completedQuestions / Math.max(current.totalQuestions, 1)) * 100,
        )
      : 100;
  }
  get completedQuestions() {
    const current = this.question();
    return current ? current.position - 1 : 0;
  }
  resetTimer() {
    this.elapsedSeconds = 0;
  }
  get wordCount() {
    return this.answerText.trim()
      ? this.answerText.trim().split(/\s+/).length
      : 0;
  }
  get pacingLabel() {
    return this.wordCount < 40
      ? "Short"
      : this.wordCount <= 200
        ? "Good length"
        : "Detailed";
  }
  onAnswerChange(value: string) {
    this.answerText = value;
    if (!this.listening) this.dictationBase = value;
  }

  private renderDictation() {
    this.answerText = [
      this.dictationBase,
      this.finalizedTranscript,
      this.interimTranscript,
    ]
      .filter(Boolean)
      .join(" ")
      .replace(/\s+/g, " ")
      .trim();
  }
  toggleDictation() {
    const SpeechRecognition =
      (window as any).SpeechRecognition ||
      (window as any).webkitSpeechRecognition;
    if (!SpeechRecognition) {
      this.message.set("Speech input is not supported in this browser.");
      return;
    }
    if (this.listening) {
      this.recognition?.stop();
      this.listening = false;
      this.interimTranscript = "";
      this.renderDictation();
      return;
    }
    this.dictationBase = this.answerText.trim();
    this.finalizedTranscript = "";
    this.interimTranscript = "";
    this.recognition = new SpeechRecognition();
    this.recognition.continuous = true;
    this.recognition.interimResults = true;
    this.recognition.onresult = (event: any) => {
      let finalText = "";
      let interimText = "";
      for (let i = event.resultIndex; i < event.results.length; i++) {
        const text = event.results[i][0].transcript;
        if (event.results[i].isFinal) finalText += text;
        else interimText += text;
      }
      this.finalizedTranscript =
        `${this.finalizedTranscript} ${finalText}`.trim();
      this.interimTranscript = interimText.trim();
      this.renderDictation();
    };
    this.recognition.onerror = () => {
      this.listening = false;
      this.interimTranscript = "";
      this.renderDictation();
      this.message.set(
        "Speech input stopped. Check browser microphone permissions and try again.",
      );
    };
    this.recognition.onend = () => {
      this.listening = false;
      this.interimTranscript = "";
      this.renderDictation();
    };
    try {
      this.recognition.start();
      this.listening = true;
    } catch {
      this.message.set("Speech input could not start. Try again.");
    }
  }

  starDone(label: string) {
    const words = this.answerText.toLowerCase();
    return label === "Situation"
      ? /situation|when|context/.test(words)
      : label === "Task"
        ? /task|goal|needed/.test(words)
        : label === "Action"
          ? /i |implemented|built|created/.test(words)
          : /result|improved|reduced|increased/.test(words);
  }
  async submitAnswer() {
    const current = this.question();
    if (!current || !this.answerText.trim()) {
      this.message.set("Write an answer before submitting.");
      return;
    }
    this.busy.set(true);
    try {
      const response = await this.sessionService.submitAnswer(
        this.id,
        current.instanceId,
        this.answerText,
      );
      this.answerText = "";
      if (response.nextQuestion) this.question.set(response.nextQuestion);
      else {
        this.question.set(null);
        this.router.navigate(["/sessions", this.id, "report"]);
      }
    } catch (e: any) {
      this.message.set(this.apiErrors.message(e, "Answer unavailable."));
    } finally {
      this.busy.set(false);
    }
  }
}
