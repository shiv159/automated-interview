# Implementation Evidence

This file records content-free deterministic verification. Live Vertex evidence
belongs under `output/playwright/<timestamp>/` and must not contain provider
bodies, credentials, candidate documents, answers, prompts, rubrics, or ideal
answers.

| Gate | Command | Exit code | Artifact | Status |
| --- | --- | ---: | --- | --- |
| Contracts | `npm test --prefix contracts` | 0 | command transcript | PASS |
| Frontend build | `npm run build --prefix frontend` | 0 | `frontend/dist/` | PASS |
| Frontend provider-free tests | `npm test --prefix frontend` | 0 | Node test transcript | PASS |
| Backend/Testcontainers | `mvn test -q -f backend/pom.xml` | 0 | Maven transcript; Docker-dependent smoke test skipped when Docker is unavailable | PASS (unit tests) / INCOMPLETE (pgvector smoke test) |
| Maven Wrapper | `backend/mvnw.cmd -q -DskipTests package` | 0 | Maven Wrapper transcript | PASS |
| Compose config | `docker compose config --quiet` | 0 | command transcript | PASS |
| Live Vertex question import | Docker Compose + API | 201 | sanitized API transcript | PASS |
| Live Vertex owner status flow | Docker Compose + API | 204 | sanitized API transcript | PASS |
| Live Vertex candidate journey | Docker Compose + API | 201/200/200/204 | sanitized API transcript | PASS |
| Docker MCP candidate/owner pages | browser snapshots at 1440px and 360px | 0 errors | MCP snapshots/screenshots | PASS |

## Evidence rules

- Use only synthetic fixtures from `fixtures/`.
- Record command, timestamp, exit code, service status, counts, durations, and
  sanitized problem codes.
- Redact tokens and never record request/response bodies or uploaded text.
