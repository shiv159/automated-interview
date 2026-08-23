# Feature Completeness Implementation Plan

Status: implemented locally; deployment verification pending  
Created: 2026-08-23  
Scope: Frontend/backend feature and functionality improvements identified in the application review

## Objective

Make the candidate review, interview, report, and question-bank journeys function consistently across Angular, Spring Boot, PostgreSQL, and the OpenAPI contract.

The implementation must address the confirmed frontend/backend mismatches first, then remove hardcoded behavior where it affects correctness or reuse. Security, performance, visual styling, and infrastructure concerns are out of scope.

## Target outcomes

- Report scores use one documented scale and render correctly.
- Report expansions show real rubric/evaluation data.
- Question-bank details expose the data they claim to expose.
- Question-bank filters and statistics reflect backend catalog values.
- Interview context and progress are driven by session/question data.
- Dictation, document preview, validation, and error states provide usable behavior.
- Imports support explicit, predictable question classification.
- Frontend and backend contract tests cover each corrected behavior.

## Decisions

1. Keep individual answer scores on a 0–10 scale.
2. Represent aggregate technical, behavioral, interview, and readiness scores on a 0–100 scale. Display aggregate scores as `/100`.
3. Keep the current three-question MVP as the default, but return the actual question count and progress from the backend so the UI is not coupled to it.
4. Add an optional role title to session creation. If omitted, display a neutral “Interview practice” label rather than a Java-specific title.
5. Prefer explicit import metadata for new question imports. Preserve TXT import compatibility with improved classification and clear rejection messages.
6. Keep document extraction server-side; add a preview endpoint rather than duplicating PDF/DOCX parsing in the browser.

## Phase 1 — Contract and shared domain model

### Tasks

1. Update `contracts/openapi.yaml`.
   - Document complete response schemas for sessions, questions, answers, reports, and question-bank summaries.
   - Add `roleTitle` as an optional session-create field.
   - Document aggregate score units and individual evaluation score units.
   - Add rubric/evaluation fields to report and question-detail responses.
   - Document problem response codes used by the UI.

2. Update database/session model.
   - Add nullable `role_title` to `interview_session` through a Flyway migration.
   - Store the submitted title with the session.
   - Return it from `SessionResponse` and interview/report responses where needed.

3. Create shared frontend interfaces instead of `any` for:
   - `Session`, `Question`, `Evaluation`, `Report`.
   - `QuestionSummary`, `CoverageBucket`, and question detail data.
   - API problem responses.

### Acceptance criteria

- The contract explicitly states whether every score is 0–10 or 0–100.
- A session can be created with or without a role title.
- Frontend types represent all fields consumed by templates.
- Contract tests validate the new response fields and score units.

## Phase 2 — Report correctness and completeness

### Tasks

1. Fix aggregate score handling.
   - Keep backend aggregate scores at 0–100.
   - Change report UI labels, progress-bar calculations, and accessibility text to `/100`.
   - Ensure individual evaluation rows remain `/10`.

2. Return rubric details.
   - Decide whether category scores will be produced by the evaluator. If not, rename the UI section to `Evaluation details` rather than exposing the current `criteria_scores` placeholder.
   - If category scores are added, extend the report query to return meaningful category/value pairs, not only `['score']`.
   - Include criteria scores, question type, skill, and question stem in each evaluation.
   - Parse JSON fields into typed response structures rather than exposing ambiguous strings where practical.

3. Replace static report expansion content.
   - Render per-criterion scores and actual evaluation details.
   - Show the question stem and the evaluator’s strengths/improvements.
   - Handle missing rubric data with a clear “No rubric details available” state.

4. Align expiration data.
   - Return the persisted session expiration instead of `now + 7200`.
   - Show an actionable expired-session message in the frontend.

### Acceptance criteria

- A report with an interview score of 82 renders as `82/100` with an 82% bar.
- A report with an individual answer score of 8 renders as `8/10`.
- Expanding an evaluation shows actual rubric values from the database.
- Report expiration matches the session expiration returned by the session API.

## Phase 3 — Question-bank data and filtering

### Tasks

1. Complete question-bank responses.
   - Add `rubric`, `idealAnswer`, and tags to the existing question summaries for this release.
   - Add explicit display labels while preserving canonical IDs.

2. Fix filtering.
   - Replace the hardcoded `SQL` value with the canonical `SQL_RELATIONAL` ID, or map display labels to IDs.
   - Load skill filter options from a backend catalog endpoint or the existing coverage data.
   - Filter behavioral questions by `type`, not only by missing `primarySkill`.

3. Fix statistics.
   - Return `skillAreaCount` as a distinct count from the backend, or compute unique canonical skills in the frontend.
   - Keep total and active counts server-authoritative.

4. Improve import classification.
   - Define the exact structured import format, extension, content type, input schema, enrichment behavior, and duplicate behavior before implementation.
   - Keep line-based TXT imports for compatibility.
   - Expand behavioral recognition and return a specific classification error describing the missing/ambiguous field.

5. Improve question-bank error states.
   - Show load failures instead of silently setting the bank to null.
   - Preserve selected filters after a successful import.

### Acceptance criteria

- SQL filtering returns SQL_RELATIONAL questions.
- Skill-area statistics equal the number of distinct skills/types represented.
- Opening a question shows its actual rubric and ideal answer.
- A valid behavioral question with noncanonical wording can be imported through structured metadata.
- Ambiguous imports identify what the owner must correct.

## Phase 4 — Interview configuration and interaction behavior

### Tasks

1. Make interview context dynamic.
   - Return role title, question type, display category, deterministic guidance, and total question count in `QuestionResponse`.
   - Remove the Java-specific title and generic system-design/STAR labels from the template.

2. Make progress dynamic.
   - Calculate percentage from `position / totalQuestions`.
   - Render the backend-reported total rather than hardcoded `3`.
   - Ensure completed state displays 100% before navigation to the report.

3. Improve question guidance.
   - Derive guidance from question type and rubric.
   - Use behavioral guidance only for behavioral questions and technical guidance appropriate to the question category.

4. Fix dictation transcript handling.
   - Maintain finalized and interim transcript buffers.
   - Replace interim text instead of appending it repeatedly.
   - Preserve manually typed text when dictation starts/stops.
   - Add explicit dictation error handling for permission, abort, and unsupported-browser cases.

5. Improve answer flow.
   - Disable submission while evaluating, but preserve the answer on failure.
   - Display structured backend errors such as evaluation unavailable or answer already accepted.
   - Reset the local timer only when explicitly requested and clarify that it is local practice timing.

### Acceptance criteria

- A non-Java role displays its configured title.
- The first, intermediate, and final progress values are mathematically correct.
- Technical and behavioral guidance matches the question returned by the backend.
- Interim speech results do not duplicate finalized text.
- A failed evaluation leaves the answer available for retry.

## Phase 5 — Candidate setup and document usability

### Tasks

1. Add optional role title input.
   - Add the field to the candidate setup form.
   - Include it in `FormData`.
   - Validate length and display a neutral fallback when omitted.

2. Validate experience locally.
   - Reject empty, non-integer, negative, and greater-than-30 values before API submission.
   - Show field-level feedback while retaining backend validation as the source of truth.

3. Implement document preview.
   - Add the stateless `POST /api/v1/documents/preview` multipart endpoint before session creation.
   - Specify normalized text, truncation, detected document type, and empty/unsupported status in its response.
   - Reuse `DocumentTextExtractor` and `DocumentNormalizer`.
   - Return a clear unsupported/empty-document state.
   - Keep the current client-side TXT preview as the fast path.

4. Improve candidate flow state.
   - Replace the static “1 of 2” indicator with actual setup/analysis/interview state.
   - Preserve selected files and validation messages when submission fails.
   - Show backend problem-code messages through the shared error mapper.

### Acceptance criteria

- Invalid experience values are caught before the request is sent.
- PDF/DOCX preview shows extracted text or an explicit extraction error.
- The role title reaches the backend and appears in the interview.
- Failed analysis does not discard the selected files.

## Phase 6 — Shared error handling and lifecycle states

### Tasks

1. Add a shared frontend API error mapper.
   - Read `error.code`, `error.detail`, and HTTP status.
   - Map session expiry, provider outage, unavailable question bank, invalid documents, invalid answers, and import conflicts to actionable messages.

2. Standardize loading, empty, and error states across:
   - Candidate review.
   - Interview.
   - Report.
   - Question bank.

3. Add route-state handling.
   - Redirect or show recovery actions for expired/deleted sessions.
   - Prevent report navigation until the backend reports `REPORT_READY`.

### Acceptance criteria

- Users never see only generic `Http failure response` text for known backend problems.
- Every major API-backed page has distinct loading, empty, and error behavior.
- Expired and deleted sessions provide a clear route back to a new review.

## Phase 7 — Verification

### Backend tests

- Session creation with and without role title.
- Report aggregate score units and rubric serialization.
- Report expiration matches persisted session expiration.
- Question-bank detail fields and distinct skill-area count.
- SQL_RELATIONAL filtering.
- Structured and line-based question import classification.
- Invalid answer, unavailable provider, expired session, and unavailable question-bank responses.

### Frontend tests

- Report score rendering at representative values: 0, 50, 82, 100.
- Rubric expansion renders backend criteria.
- SQL filter selects SQL_RELATIONAL.
- Question detail displays rubric and ideal answer.
- Dynamic interview progress for different totals.
- Dictation finalized/interim transcript behavior with mocked recognition events.
- Experience validation and role-title submission.
- Error-code mapping for representative API problems.

### End-to-end checks

1. Load demo materials.
2. Create a session with a custom role title.
3. View matched/missing skills.
4. Start the interview and verify dynamic role, guidance, and progress.
5. Submit three answers.
6. Verify report score scales, rubric details, and expiration.
7. Export the report.
8. Import a technical and behavioral question.
9. Open question details, filter by SQL, toggle owner-question status, and export filtered results.
10. Verify expired/deleted-session recovery.

## Dependencies and order

The order is intentional:

1. Contract and persistence changes must land before frontend consumption.
2. Report and question-bank response changes require backend DTO/query updates first.
3. Dynamic interview UI depends on richer `QuestionResponse` data.
4. Shared error handling should be added before final end-to-end verification.
5. Document preview depends on the existing extraction service and should be implemented after the session contract is stable.

## Risks and mitigations

- Score-unit changes may break existing consumers. Mitigation: document units in OpenAPI and add backward-compatible field names or version the response if external consumers exist.
- Adding role title extraction could introduce unreliable inferred values. Mitigation: use an explicit optional user field with a neutral fallback.
- Structured imports may conflict with existing TXT workflows. Mitigation: support both formats and test both paths.
- Preview extraction may be expensive or fail for malformed documents. Mitigation: return explicit empty/unsupported states and keep TXT preview local.
- Dynamic interview counts may expose assumptions in the database position constraint. Mitigation: either retain the three-question constraint for the first release or migrate it together with configurable interview templates.

## Definition of done

- All confirmed findings from the review are resolved or explicitly marked as deferred.
- Frontend and backend contracts agree on names, shapes, and score units.
- The full candidate-to-report journey works with a non-Java role.
- Question-bank filtering, detail, import, status, and export behavior work with canonical backend data.
- Automated tests cover each corrected mismatch and the primary recovery states.
- `npm test`, `npm run build`, backend tests, contract tests, and the end-to-end smoke flow complete successfully.

## Deployed verification addendum — 2026-08-23

Tested URL: `https://automated-interview-frontend-527840416057.us-central1.run.app/`

The deployed candidate journey completed successfully:

- `GET /api/health` → 200
- `GET /api/v1/question-bank` → 200
- `POST /api/v1/sessions` → 201
- `GET /api/v1/sessions/{id}` → 200
- `POST /api/v1/sessions/{id}/interview` → 200
- Three answer submissions → 200
- `GET /api/v1/sessions/{id}/report` → 200

Cloud Run logs for `automated-interview-backend` contained matching successful request entries and no application errors for the tested flow.

Confirmed deployed defects to prioritize:

1. The interview page displays `JAVA FULL-STACK ENGINEER` for the demo session and shows `66% complete` on question 1 and question 2. The role and progress are hardcoded in the deployed UI.
2. The SQL filter displays no rows even though the question bank contains a `SQL_RELATIONAL` question. This is a live frontend/backend identifier mismatch.
3. The question detail modal displays `Evaluation criteria are applied during the live interview.` instead of the question's rubric or ideal answer.
4. The report displays aggregate interview score as `/10`; the aggregate API value is intended to be 0–100, so non-zero scores will be misrepresented and the progress bar will be scaled incorrectly.
5. The report's expanded evaluation content is static and does not show rubric/category scores.

The deployed test did not mutate question-bank status or create a new import. Import and status-toggle changes remain covered by backend/contract tests and should be verified in a controlled staging run.

## Release-gate clarifications

- Extend the existing question-bank list response with rubric and ideal-answer fields for the first fix release; do not leave the detail endpoint as an unresolved alternative.
- Returning the existing `criteria_scores` placeholder is not sufficient. Either make the evaluator return meaningful category scores or rename the UI section to `Evaluation details`.
- Define interview guidance as a deterministic mapping from question type, skill, and rubric for v1.
- Define document preview as a stateless `POST /api/v1/documents/preview` multipart endpoint before session creation.
- Define the structured import format, extension, content type, input schema, enrichment behavior, and duplicate behavior before implementation.
- E2E verification must specify the database fixture and AI evaluation profile and must inspect response bodies, not only HTTP status codes.

## Implementation progress — 2026-08-23

Completed in the current worktree:

- Added session `role_title` migration, request transport, persistence, and response fields.
- Added dynamic interview role title, total question count, deterministic guidance, and progress calculation.
- Changed aggregate interview score display to `/100`; individual answer scores remain `/10`.
- Returned report rubric criteria and rendered real evaluation details instead of the static expansion copy.
- Returned question-bank rubric and ideal-answer fields and corrected canonical skill/statistics handling.
- Added shared frontend API problem-code mapping.
- Added stateless document preview endpoint and PDF/DOCX frontend preview integration.
- Added optional role-title and local experience validation.
- Added interim/final dictation transcript handling.
- Added structured JSON question import support with `contracts/question-import.schema.json`, while preserving TXT imports.
- Completed OpenAPI response schemas for sessions, answers, imports, problems, and document previews; empty previews now return an actionable extraction error.
- Added frontend and contract regression checks for the corrected feature behavior.
- Removed the remaining hardcoded progress-bar width fallback so progress is entirely driven by the question response.

Remaining verification gates:

- Run the updated backend against PostgreSQL/pgvector and exercise migration V3, document preview, and JSON import through HTTP.
- Run the updated end-to-end browser flow against the newly deployed revision.
- Verify question-bank status toggle and import in a controlled environment without leaving test mutations.

## Implementation review

**Completed**: 2026-08-23

### What landed

The planned frontend/backend feature corrections are implemented in the worktree, including dynamic interview context/progress, report score and evaluation details, question-bank metadata/filtering/statistics, document preview, role-title handling, structured JSON imports, shared API error messages, and regression checks.

### Verification result

- Frontend build: passed.
- Frontend tests: 4 passed.
- Contract tests: 4 passed.
- Backend Maven suite: exited successfully; the pgvector Testcontainers test was skipped because Docker is unavailable in the current environment.
- `git diff --check`: passed with only line-ending conversion warnings.

### Follow-up

The deployed Cloud Run revision has not been replaced by this worktree because deployment is an external state change. After deployment, run the browser flow against the new revision and exercise V3 migration, document preview, JSON import, and reversible question-status toggling.
