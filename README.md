# Automated Interview Platform

Greenfield Angular/Spring Boot application for the automated interview MVP.

## Local foundation

1. Copy `.env.example` to `.env`. Run `gcloud auth application-default login` once; Compose mounts the local ADC file into the backend and refreshes access tokens automatically. Never commit `.env` or Google credential files.
2. Start PostgreSQL/pgvector and the backend:

```powershell
docker compose up --build
```

3. Start the Angular development server:

```powershell
cd frontend
npm ci
npm start -- --host 127.0.0.1 --port 4200
```

The database is available only on `127.0.0.1:5432`; the backend is internal to
Compose and the frontend is available on `127.0.0.1:4200`.

Health check: `http://127.0.0.1:4200/api/health`

Candidate routes are `/`, `/sessions/:id/analysis`,
`/sessions/:id/interview`, and `/sessions/:id/report`. The owner question-bank
route is `/question-bank`.

The `.env.example` profiles are set to the Vertex target runtime. For
provider-free development, set `APP_ANSWER_EVALUATION_PROFILE` and
`APP_EMBEDDING_PROFILE` to `local` (question import remains AI-backed because
its structured enrichment is provider-required). Provider failures fail closed with
stable API problem codes and never create partial sessions or evaluations.

For local Vertex setup:

```powershell
gcloud auth application-default login
gcloud auth application-default set-quota-project intervu-ai-20260704-8f3c
gcloud services enable aiplatform.googleapis.com --project intervu-ai-20260704-8f3c
```

`VERTEX_ACCESS_TOKEN` is retained only as a fallback for deployments that provide
their own short-lived token. The backend prefers Application Default Credentials,
which avoids expired tokens in `.env`.

Verification commands:

```powershell
npm test --prefix contracts
npm run build --prefix frontend
mvn test -q -f backend/pom.xml
docker compose ps
```

The backend test suite includes a Docker-backed pgvector smoke test. Playwright
validation covers the candidate and owner pages at desktop and 360px viewports;
the browser scripts used for local validation are kept outside the application
bundle and do not access provider credentials.

The repository is being implemented in the PRD milestone order: contracts,
runtime foundation, document analysis, retrieval, question import, interview
evaluation, Angular journeys, and final verification.
