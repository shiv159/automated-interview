# Automated Interview End-to-End Validation Plan

Status: draft  
Scope: Docker, PostgreSQL/pgvector, Spring Boot, Vertex AI, Angular, and browser journeys  
Environment: local Docker Desktop with Google Application Default Credentials

## Objective

Prove that a synthetic job description and résumé can move through the complete product flow:

```text
Docker startup
  -> Vertex skill analysis
  -> session creation
  -> question selection
  -> answer evaluation and embeddings
  -> report generation
  -> browser rendering and session deletion
```

Do not mark the project complete until every required gate below has passed.

## Required test configuration

```powershell
$env:APP_ANSWER_EVALUATION_PROFILE = "ai"
$env:APP_QUESTION_ENRICHMENT_PROFILE = "ai"
$env:APP_EMBEDDING_PROFILE = "ai"
$env:AI_DATA_RETENTION_ACKNOWLEDGED = "true"

docker compose up -d --build
```

Use only synthetic documents. Create temporary UTF-8 `.txt` fixtures under
`output/playwright/fixtures/`; do not commit them or use real résumé data.

## Gate 1: environment and service readiness

Run:

```powershell
docker compose ps
docker compose config --quiet
Invoke-RestMethod http://127.0.0.1:4200/api/health
```

Pass criteria:

- `db` is healthy.
- `backend` and `web` are running.
- Compose configuration exits successfully.
- Health response is `{"status":"ok","service":"automated-interview"}`.
- Backend logs show Flyway validation and schema version 1 without errors.

## Gate 2: Vertex AI provider smoke test

Test the exact Gemini and embedding operations used by the backend, using the
same project, region, model names, and ADC credentials. Record only status,
latency, model, and embedding dimension; never log access tokens or document text.

Pass criteria:

- Gemini `generateContent` returns HTTP 2xx and valid response text.
- `text-embedding-005` returns HTTP 2xx and exactly 768 values.
- No `401`, `403`, `404`, `417`, quota, or permission errors.

If this gate fails, stop the product E2E run and classify the failure as network,
credential, IAM, model availability, quota, or request-schema before changing code.

## Gate 3: API happy path

Use the backend API with a cookie-preserving HTTP client.

1. `POST /api/v1/sessions` with synthetic job and résumé `.txt` files,
   `yearsExperience=3`, and `syntheticDataAttested=true`.
2. Assert HTTP `201`, a session ID, expiration, difficulty, profile match,
   matched skills, and missing skills.
3. `GET /api/v1/sessions/{sessionId}` using the returned cookie.
4. `POST /api/v1/sessions/{sessionId}/interview`.
5. Submit three valid answers through
   `POST /questions/{instanceId}/answers`.
6. Assert every evaluation has a score from 0 to 10, strengths, and improvements.
7. `GET /report` and assert readiness score, interview score, three evaluations,
   and a readiness label.
8. `DELETE /api/v1/sessions/{sessionId}`.
9. Assert subsequent session access returns the expected not-found/expired error.

Pass criteria: one complete session reaches `REPORT_READY`, all three answer
evaluations are provider-backed, and deletion removes access without leaving a
usable session cookie.

## Gate 4: owner question-bank journey

1. `GET /api/v1/question-bank` and record the seeded active count.
2. Import a synthetic question file containing technical and behavioral stems.
3. Assert structured enrichment, deterministic type/skill classification,
   active status, and embeddings.
4. Deactivate one owner-imported question with
   `PATCH /questions/{id}/status`.
5. Assert the row becomes inactive and is not selected for a new interview.
6. Restore it and verify the owner list reflects the change.

Pass criteria: import is atomic, malformed/ambiguous questions do not create
partial rows, and status changes are reflected in the list and coverage data.

## Gate 5: Playwright candidate flow

Use the bundled Playwright CLI wrapper. Node/npm are available (`node v24.16.0`,
`npm v11.5.1`). Save artifacts under `output/playwright/`.

```powershell
$env:CODEX_HOME = "C:\Users\zayns\.codex"
$env:PWCLI = "$env:CODEX_HOME\skills\playwright\scripts\playwright_cli.sh"
```

Run the following journey against `http://127.0.0.1:4200/`:

1. Open the candidate page and capture a snapshot.
2. Select synthetic job and résumé files.
3. Enter years of experience and check the attestation.
4. Submit and assert the analysis page/route, matched skills, and difficulty.
5. Start the interview and assert question `1 of 3`.
6. Submit three answers and assert the report route.
7. Assert report scores, coaching text, JSON download, print action, and delete action.
8. Reload each route and verify session restoration or the expected deleted-session state.

Repeat at:

- Desktop viewport: 1440x900.
- Mobile viewport: 360x800.

For each viewport capture a screenshot, final URL, console messages, failed
requests, and a trace for failures. Pass requires no uncaught console errors,
no failed application requests, usable controls, and no horizontal overflow.

## Gate 6: Playwright owner flow

Open `/question-bank` and verify:

- seeded rows render;
- coverage buckets are visible;
- synthetic question import succeeds;
- imported row can be deactivated and reactivated;
- owner controls remain usable at desktop and 360px widths.

## Gate 7: negative and resilience checks

Verify the application fails closed for:

- missing attestation;
- unsupported file extension;
- invalid UTF-8 text;
- oversized documents;
- invalid experience range;
- empty/oversized answers;
- expired or missing session cookie;
- ambiguous technical question import;
- temporarily unavailable Vertex provider.

Pass criteria:

- stable problem codes and HTTP statuses are returned;
- no partial session, question, answer, or report is created;
- no credential, résumé text, or provider response is exposed in browser errors or logs.

## Evidence package

Store the final run in `output/playwright/<timestamp>/`:

- command transcript;
- Docker Compose status;
- sanitized backend logs;
- API response assertions;
- desktop and mobile screenshots;
- Playwright trace only when useful or on failure;
- final pass/fail summary.

## Completion criteria

The validation goal is complete only when:

1. Gates 1–4 pass with the real Vertex provider enabled.
2. Gates 5–6 pass on desktop and mobile.
3. Gate 7 passes without data leakage or partial writes.
4. The evidence package is reproducible from a clean Docker restart.
5. Any remaining failure has an identified owner, cause, and next action.
