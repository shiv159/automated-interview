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
- Vertex question import succeeded with HTTP `201`; the imported owner row was
  deactivated and reactivated with HTTP `204`.
- The complete live candidate API journey passed: session `201`, interview
  start `200`, three answer evaluations `200`, report `200` with three
  evaluations, and deletion `204`.
- Docker MCP browser snapshots passed for candidate and owner pages at 1440px
  and 360px with zero browser console errors.

## Failed or incomplete

- A prior run returned `503 QUESTION_ENRICHMENT_UNAVAILABLE`; the provider
  adapter has since been corrected with provider-compatible structured schema
  constraints and deterministic closed validation.
- Provider behavior remains externally variable; live validation should be
  repeated after token rotation and recorded as a new timestamped evidence run.

## Current disposition

The core candidate and owner journeys are operational with real Vertex generation,
embeddings, PostgreSQL, and browser interaction. Deterministic suites remain the
automated gate; live-provider evidence is recorded separately and must be
repeated when provider credentials or models change.

## Next fixes

1. Repeat the live-provider evidence run after the next ADC/token rotation.
2. Keep sanitized status/category logging enabled for provider diagnostics.
3. Do not add automatic provider retries unless the PRD contract is revised.
