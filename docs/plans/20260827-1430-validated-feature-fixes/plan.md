# Validated Feature Fixes Plan

Status: draft
Created: 2026-08-27
Scope: Confirmed frontend/backend inconsistencies from the current review

## Objective

Make candidate skill analysis, interview progress, coaching reports, error recovery, skill support, imports, and report exports match the data calculated by the backend. Preserve the existing three-question interview and evaluation accordion.

## Out of scope

- Public owner question-bank access and demo-file visual state from the earlier review.
- Voice dictation changes: the current implementation already separates finalized and interim transcripts.
- Session expiry correction: the current report returns persisted `session.expiresAt`.
- Replacing generic interview copy unless a specific dynamic-context defect is reproduced.

## Design decisions

1. Keep individual answer scores on a 0–10 scale; keep aggregate scores on a 0–100 scale.
2. Treat supported and unsupported job requirements as separate data. Never calculate a fully authoritative-looking profile percentage when unsupported requirements are present without disclosing the limitation.
3. Keep canonical skill IDs stable. Move aliases/display metadata behind a single backend source of truth used by AI prompts, analysis validation, imports, and question retrieval.
4. Return typed structured JSON for criterion scores and import validation errors rather than opaque JSON strings where practical.
5. Preserve TXT import compatibility while adding structured, line-addressable errors.

## Phase 1 — Contract and model updates

### Tasks

1. Extend `contracts/openapi.yaml` for:
   - `unsupportedJobSkills` and any coverage/score-limitation fields.
   - Resume/job skill claim structures with evidence.
   - Report score breakdown and criterion-level evaluation scores.
   - Structured import errors containing line, field, code, message, and correction hint.
   - All frontend-mapped problem codes.

2. Update frontend interfaces in `session.service.ts`:
   - Add unsupported-skill fields.
   - Replace ambiguous string fields with typed criterion structures where the API changes.
   - Keep compatibility handling for existing deployed responses during rollout.

3. Add database migration(s) only where needed:
   - Store unsupported requirements or analysis coverage metadata if it must survive session reload.
   - Preserve existing `criteria_scores` data and define its JSON shape.

### Checkpoint

- Contract tests validate the new response fields and score units.
- Existing session/report consumers remain deserializable.

## Phase 2 — Candidate review skill evidence

### Tasks

1. Add derived component selectors:
   - `matchedClaims`: job claims whose IDs are in `matchedSkills`.
   - `missingClaims`: job claims whose IDs are in `missingSkills`.
   - `additionalClaims`: resume claims whose IDs are not job-skill IDs.

2. Replace the single job-claim evidence list with three clearly labeled sections:
   - Matched skills.
   - Missing requirements.
   - Additional candidate skills.

3. Preserve each claim’s evidence text and importance where applicable.

4. Add empty states for no matched, missing, or additional skills.

5. Display unsupported requirements separately from missing supported skills.

### Acceptance criteria

- A résumé-only skill is visible and includes its résumé evidence.
- A missing supported requirement is not confused with an unsupported requirement.
- Reloading `/sessions/:id/analysis` produces the same sections from the API snapshot.

## Phase 3 — Report data and explainability

### Tasks

1. Backend evaluator:
   - Define a criterion-score schema, for example `{ criterion, score, feedback }`.
   - Update the evaluator prompt and validated response model.
   - Persist meaningful values in `evaluation.criteria_scores`.
   - Reject malformed criterion results safely and preserve retry behavior.

2. Backend report:
   - Select and deserialize criterion scores.
   - Return question metadata, rubric criteria, criterion scores, strengths, and improvements.
   - Return the existing aggregate scores and persisted expiry unchanged.

3. Frontend report:
   - Add cards for profile match, technical, behavioral, interview, and readiness scores.
   - Add a visible breakdown:
     - `Interview = Technical × 80% + Behavioral × 20%`
     - `Readiness = Profile match × 30% + Interview × 70%`
   - Render criterion scores inside the existing evaluation accordion.
   - Keep graceful fallback for old reports without criterion scores.

### Acceptance criteria

- All backend-returned aggregate scores are visible with `/100` labels.
- Individual evaluation scores remain `/10`.
- Expanding an evaluation shows actual criteria and criterion scores, not placeholder text.
- Displayed readiness recomputes consistently with the backend formula.

## Phase 4 — Error mapping and recovery UX

### Tasks

1. Add user-friendly mappings for:
   - `ATTESTATION_REQUIRED`
   - `NO_SUPPORTED_SKILLS`
   - `INVALID_ANSWER`
   - `AI_PROVIDER_UNAVAILABLE`
   - `SKILL_ANALYSIS_UNCERTAIN`
   - `SKILL_ANALYSIS_INVALID`
   - `SKILL_EVIDENCE_INVALID`
   - `REPORT_NOT_READY`

2. Include recovery actions in message text or adjacent UI:
   - Correct/reselect files.
   - Retry analysis/evaluation.
   - Start a new review.
   - Continue waiting for report readiness.

3. Add tests for both `error.code` and HTTP fallback behavior.

### Acceptance criteria

- Known errors never surface as raw `Http failure response` text.
- Failed answer evaluation keeps the answer available for retry.
- A premature report request explains that the interview is not complete.

## Phase 5 — Skill catalog and unsupported requirements

### Tasks

1. Define a canonical skill data model with:
   - Stable ID.
   - Display name.
   - Aliases.
   - Active status.
   - Optional version/source metadata.

2. Move the current four skills into configuration-backed or database-backed data.

3. Provide a controlled seed/administrative mechanism for adding skills without code changes.

4. Update all consumers to use the same catalog:
   - AI skill-analysis prompt.
   - Skill validation.
   - Alias matching.
   - Question import classification.
   - Question retrieval/filter options.

5. Capture unsupported job requirements during analysis. Do not silently drop them.

6. Define scoring behavior:
   - Supported-only score with explicit coverage warning, or
   - A coverage-adjusted score with documented formula.

### Acceptance criteria

- Adding a seeded/configured skill makes it available to analysis and question imports.
- Canonical IDs and aliases remain backward-compatible.
- A job containing an unsupported requirement completes with a visible limitation when supported requirements are still usable.
- A job containing only unsupported requirements returns a clear actionable state instead of an inaccurate score.

## Phase 6 — Interview progress and input validation

### Tasks

1. Keep dynamic total-question rendering from the backend.

2. Change progress semantics to completed questions:
   - Before question 1: 0%.
   - Before question 2: completed question count / total.
   - After the final answer: 100% before navigation.

3. Add `step="1"` and explicit client validation for empty, fractional, negative, and over-30 experience values.

4. Add tests that verify no request is sent for invalid experience input.

### Acceptance criteria

- Progress is not hardcoded and does not claim question 1 is already complete.
- Backend and UI agree on question count.
- Invalid experience values produce a clear local message.

## Phase 7 — Structured import diagnostics

### Tasks

1. Replace single-code import failures with a batch error response where possible.

2. Track one-based source line/index during TXT and JSON normalization.

3. Attach field names for JSON errors and correction hints for:
   - Invalid skill.
   - Ambiguous skill.
   - Behavioral/technical field conflicts.
   - Duplicate stems.
   - Invalid encoding or file shape.

4. Preserve transaction safety: reject the batch without partial writes unless partial import is explicitly designed and documented.

### Acceptance criteria

- An invalid item identifies its line/index and correction.
- Multiple invalid items can be reported together when safe.
- Existing valid TXT and JSON fixtures continue to pass.

## Phase 8 — Complete report export and print layout

### Tasks

1. Define a report export view model containing all visible report data:
   - Score cards and formula breakdown.
   - Job/resume/matched/missing/additional skills.
   - Unsupported requirements and coverage limitation.
   - Question metadata.
   - Rubric and criterion scores.
   - Strengths and improvements.
   - Session expiry.

2. Export that view model to JSON.

3. Add CSV export with stable columns for score summaries and evaluation rows. Escape commas, quotes, and newlines.

4. Add print CSS:
   - Hide navigation/actions/delete controls.
   - Preserve score cards and expanded evaluation content.
   - Avoid row/card splits across pages where possible.
   - Add print-only headings and footer metadata.

### Acceptance criteria

- JSON and CSV contain every user-visible report field required by the contract.
- CSV opens correctly when answers contain commas or line breaks.
- Browser print preview produces a readable multi-page report.

## Verification plan

### Frontend

- Unit tests for matched/missing/additional skill selectors.
- Error mapper tests for all eight new codes.
- Report score and formula rendering tests.
- Criterion accordion rendering tests.
- Progress tests for totals 1, 3, and non-default totals.
- Experience validation tests.
- JSON/CSV export field and escaping tests.

### Backend

- Session response tests for resume-only and unsupported skills.
- Score formula and report DTO tests.
- Criterion-score evaluator/schema tests.
- Catalog loading, alias matching, and new-skill seed tests.
- Unsupported-only and mixed supported/unsupported analysis tests.
- Structured import diagnostics tests.

### End-to-end

1. Load demo materials and verify the visible analysis sections.
2. Create a session with matched, missing, and résumé-only skills.
3. Verify unsupported requirements are disclosed.
4. Start interview and verify progress values before each answer.
5. Complete three answers in a controlled AI/staging environment.
6. Verify all report scores, formulas, rubric scores, and accordion content.
7. Download JSON/CSV and inspect fields.
8. Open print preview and verify layout.
9. Exercise invalid documents, unsupported skills, invalid answers, unavailable provider, and report-not-ready recovery.

## Delivery order

1. Phase 1 contract/model foundation.
2. Phase 4 error mapping, because it improves every subsequent failure path.
3. Phase 2 candidate evidence and Phase 6 progress/validation.
4. Phase 3 evaluator/report changes.
5. Phase 5 catalog and unsupported-skill behavior.
6. Phase 7 import diagnostics.
7. Phase 8 exports and print layout.
8. Full verification and deployed browser validation.

## Risks

- Changing evaluator output may invalidate existing AI responses. Mitigate with strict schema validation and retry-safe fallback.
- Catalog migration can break stored question IDs or aliases. Preserve canonical IDs and run migration tests against existing fixtures.
- Unsupported-skill scoring changes may alter historical profile percentages. Version or annotate the scoring model.
- Report export scope can grow quickly. Generate exports from one typed report view model to avoid JSON/UI/CSV drift.
- Print rendering varies by browser. Validate Chromium first and keep print CSS conservative.

## Definition of done

- Every confirmed issue in this plan has an implementation and regression test.
- Disproved claims remain unchanged unless a new reproduction appears.
- API contract, backend DTOs, frontend interfaces, UI rendering, and exports agree.
- `npm test`, `npm run build`, contract tests, backend Maven tests, and controlled E2E checks pass.
