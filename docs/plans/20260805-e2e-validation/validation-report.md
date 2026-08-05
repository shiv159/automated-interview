# Validation Report

Run date: 2026-08-05  
Environment: Docker Desktop, PostgreSQL/pgvector, Vertex AI ADC, `http://127.0.0.1:4200`

## Passed

- Docker Compose services started; database healthy; application health returned `200`.
- `docker compose config --quiet` passed.
- Contract tests: 2 passed, 0 failed.
- Backend Maven tests passed, including the Testcontainers pgvector smoke test.
- Angular production build passed.
- Vertex Gemini smoke test returned `200` with `OK`.
- Vertex `text-embedding-005` smoke test returned `200` with 768 dimensions.
- Candidate API flow passed: session creation, interview start, three answer evaluations, report, delete, and post-delete `404`.
- Candidate Playwright flow passed on desktop: analysis, interview questions, report, delete, and zero console errors.
- Owner question-bank browser page rendered at 360px with 26 seeded active questions and coverage buckets.
- Missing attestation returned `400 ATTESTATION_REQUIRED`.
- The session persistence defect was fixed by binding `Instant` as `Timestamp` for PostgreSQL.

## Failed or incomplete

- AI question-bank import returned `503 QUESTION_ENRICHMENT_UNAVAILABLE` on repeated attempts.
- One behavioral answer evaluation returned `503 EVALUATION_UNAVAILABLE` once and succeeded on retry, indicating a transient provider/response-path issue.
- Owner import and owner status-toggle validation could not be completed because import did not create an owner row.
- A direct Gemini enrichment request returned valid JSON, so the remaining import issue is inside the backend enrichment path or its strict validation, not basic Vertex connectivity.

## Current disposition

The core candidate journey is operational with real Vertex generation, embeddings, PostgreSQL, and browser interaction. The validation goal remains open until question import is reliable and the transient evaluation behavior is understood or given an explicit retry policy.

## Next fixes

1. Add a focused test around `VertexQuestionEnricher.enrich` using a captured sanitized provider response and assert each validation rule independently.
2. Log sanitized enrichment status/validation reasons, not response content or credentials.
3. Decide whether provider calls need bounded retry with backoff for transient `5xx`/network failures.
4. Re-run owner import, deactivate/reactivate an owner row, and repeat the mobile owner journey.
