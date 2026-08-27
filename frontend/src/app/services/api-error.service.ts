import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ApiErrorService {
  message(error: any, fallback: string): string {
    const code = error?.error?.code ?? error?.error?.title;
    const messages: Record<string, string> = {
      SESSION_EXPIRED: 'This session has expired. Start a new review.',
      SESSION_NOT_FOUND: 'This session is no longer available. Start a new review.',
      INVALID_SESSION_TOKEN: 'This session cannot be verified. Start a new review.',
      QUESTION_BANK_UNAVAILABLE: 'No suitable interview questions are available right now. Try again later.',
      EVALUATION_UNAVAILABLE: 'Answer evaluation is temporarily unavailable. Your answer is still available to retry.',
      ANSWER_ALREADY_ACCEPTED: 'This answer has already been accepted.',
      ANSWER_EVALUATION_IN_PROGRESS: 'This answer is still being evaluated.',
      INVALID_DOCUMENT: 'The document could not be read. Check its contents and format.',
      DOCUMENT_TEXT_NOT_EXTRACTABLE: 'No readable text was found in this document.',
      DOCUMENT_LIMIT_EXCEEDED: 'This document is empty or exceeds the supported size/page limit.',
      UNSUPPORTED_DOCUMENT: 'Use a PDF, DOCX, or TXT document.',
      INVALID_EXPERIENCE: 'Enter between 0 and 30 years of experience.',
      INVALID_ROLE_TITLE: 'Role title must be 160 characters or fewer.',
      INVALID_QUESTION_FILE: 'The question file is invalid. Use UTF-8 TXT or a JSON array of question objects.',
      QUESTION_SKILL_AMBIGUOUS: 'The question needs exactly one recognizable technical skill.',
      QUESTION_FIELD_CONFLICT: 'Behavioral questions cannot include technical skill or difficulty metadata.',
      QUESTION_ENRICHMENT_UNAVAILABLE: 'Question enrichment is temporarily unavailable.',
      SEED_QUESTION_IMMUTABLE: 'Seed questions cannot be changed.',
      VECTOR_SYNC_UNAVAILABLE: 'The question was saved but is not searchable yet. Try again later.',
      ATTESTATION_REQUIRED: 'Confirm that both documents are synthetic or non-confidential, then try again.',
      NO_SUPPORTED_SKILLS: 'No supported skills were found. Add recognizable technical requirements or review the supported skills list.',
      INVALID_ANSWER: 'Write a non-empty answer under 4,000 characters, then try again.',
      AI_PROVIDER_UNAVAILABLE: 'AI analysis is temporarily unavailable. Wait a moment and retry; your uploaded files are still selected.',
      SKILL_ANALYSIS_UNCERTAIN: 'The skill analysis was uncertain. Review the documents for clear requirements and retry.',
      SKILL_ANALYSIS_INVALID: 'The skill analysis response was invalid. Retry the analysis or use clearer document text.',
      SKILL_EVIDENCE_INVALID: 'Some skill evidence could not be verified. Review the source document and retry.',
      REPORT_NOT_READY: 'Finish all interview questions before opening the coaching report.'
    };
    const mapped = messages[code];
    const diagnostics = Array.isArray(error?.error?.errors) ? error.error.errors.slice(0, 3).map((item: any) => {
      const location = item.line ? `line ${item.line}` : item.item ? `item ${item.item}` : 'item';
      return `${location}${item.field ? `/${item.field}` : ''}: ${item.hint || item.message || item.code}`;
    }).join(' ') : '';
    const hint = error?.error?.hint ? ` ${error.error.hint}` : '';
    return mapped ? `${mapped}${diagnostics ? ` ${diagnostics}` : hint}` : error?.error?.detail ?? error?.message ?? fallback;
  }
}
