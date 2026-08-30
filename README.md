# Automated Interview Platform

Greenfield Angular/Spring Boot application for the automated interview MVP.

## High-Level Application Flow

The Automated Interview Platform automates the end-to-end technical interview lifecycle—from question curation and resume analysis to live interactive interviewing and AI-driven candidate evaluation.

```mermaid
flowchart TD
    subgraph Admin ["1. Question Bank Management"]
        QB_Upload[Upload Questions / Documents] --> AI_Enrich[AI Question Enrichment & Tagging]
        AI_Enrich --> Embed[Generate Vector Embeddings]
        Embed --> PG_Vector[(PostgreSQL + pgvector)]
    end

    subgraph Candidate_Flow ["2. Candidate Journey"]
        Upload_Resume[Upload Resume / Job Description] --> Doc_Analysis[Document Analysis & Skill Extraction]
        Doc_Analysis --> Init_Session[Initialize Interview Session]
        PG_Vector -->|Semantic Retrieval| Match_Q[Retrieve Tailored Questions]
        Init_Session --> Match_Q
        Match_Q --> Live_Interview[Conduct Interactive Interview]
        Live_Interview --> Candidate_Answers[Submit Candidate Answers]
    end

    subgraph Evaluation ["3. Evaluation & Reporting"]
        Candidate_Answers --> AI_Eval[AI Answer Evaluation & Rubric Scoring]
        AI_Eval --> Report_Gen[Generate Assessment Report]
        Report_Gen --> Final_Report[Candidate Report & Recommendations]
    end
```

### Flow Breakdown

1. **Question Bank Curation & AI Enrichment (`/question-bank`)**:
   - **Ingestion**: Administrators upload questions or bulk documents.
   - **Enrichment**: Vertex AI / Spring AI parses, categorizes (topics, difficulty, competencies), and generates assessment rubrics.
   - **Vector Indexing**: Questions are embedded and stored in PostgreSQL using `pgvector` for semantic similarity search.

2. **Candidate Document Analysis & Session Setup (`/` & `/sessions/:id/analysis`)**:
   - **Resume & Job Description Parsing**: The candidate provides their resume/details.
   - **Skill Extraction**: Document analysis identifies key competencies, experience level, and domain expertise.
   - **Adaptive Question Retrieval**: `pgvector` retrieves the most relevant and calibrated interview questions tailored to the candidate's profile.

3. **Interactive Interview Execution (`/sessions/:id/interview`)**:
   - **Live Interview Flow**: The candidate progresses through tailored technical and behavioral questions sequentially.
   - **Response Submission**: Candidate answers are captured and saved with session state tracking.

4. **AI-Powered Evaluation & Reporting (`/sessions/:id/report`)**:
   - **Rubric Scoring**: Responses are evaluated by AI against standardized grading criteria, scoring accuracy, depth, clarity, and relevance.
   - **Comprehensive Report**: Generates an actionable assessment report with overall scoring, category breakdowns, strengths, areas for improvement, and hiring recommendations.

---

## Database Schema (Simplified)

The core data model manages question cataloging, candidate sessions, vector retrieval (`pgvector`), and evaluations.

```mermaid
erDiagram
    SKILL ||--o{ QUESTION : categorizes
    SKILL ||--o{ SESSION_SKILL : references
    QUESTION ||..|| VECTOR_STORE : embeds
    INTERVIEW_SESSION ||--o{ SESSION_SKILL : "extracted skills"
    INTERVIEW_SESSION ||--o{ SESSION_QUESTION : "assigned questions"
    QUESTION ||--o{ SESSION_QUESTION : instantiates
    SESSION_QUESTION ||--o| EVALUATION : evaluated_by

    SKILL {
        varchar id PK
        varchar display_name
        jsonb aliases
    }

    QUESTION {
        uuid id PK
        varchar primary_skill FK
        text stem
        varchar type
        varchar difficulty
        jsonb rubric
    }

    VECTOR_STORE {
        uuid id PK
        vector embedding
        json metadata
    }

    INTERVIEW_SESSION {
        uuid id PK
        varchar role_title
        varchar state
        numeric profile_match
    }

    SESSION_SKILL {
        uuid session_id PK,FK
        varchar skill_id PK,FK
        boolean matched
    }

    SESSION_QUESTION {
        uuid id PK
        uuid session_id FK
        uuid question_id FK
        integer position
        varchar status
    }

    EVALUATION {
        uuid id PK
        uuid session_question_id FK
        numeric score
        jsonb criteria_scores
    }
```

---

## Local foundation

1. Copy `.env.example` to `.env`. The example file is already wired to `VERTEX_PROJECT_ID=intervu-ai-20260704-8f3c`; keep that value or replace it with your own Vertex project. Run `gcloud auth application-default login` once; Compose mounts the local ADC file into the backend and refreshes access tokens automatically. Never commit `.env` or Google credential files.
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

### Google Cloud deployment secrets

The Cloud Run deployment workflow reads database credentials and the question-bank API key from Google Secret Manager. Create the secrets once in the target project and grant the Cloud Run runtime service account access:

```powershell
gcloud secrets create automated-interview-database-url --replication-policy=automatic
gcloud secrets create automated-interview-database-username --replication-policy=automatic
gcloud secrets create automated-interview-database-password --replication-policy=automatic
gcloud secrets create automated-interview-question-bank-api-key --replication-policy=automatic

"<database-url>" | gcloud secrets versions add automated-interview-database-url --data-file=-
"<database-username>" | gcloud secrets versions add automated-interview-database-username --data-file=-
"<database-password>" | gcloud secrets versions add automated-interview-database-password --data-file=-
"<question-bank-api-key>" | gcloud secrets versions add automated-interview-question-bank-api-key --data-file=-

gcloud secrets add-iam-policy-binding automated-interview-database-url `
  --member="serviceAccount:<cloud-run-runtime-service-account>" `
  --role="roles/secretmanager.secretAccessor"
```

Repeat the final IAM command for the other three secrets. The runtime service account—not the GitHub Actions identity—must have `roles/secretmanager.secretAccessor`. Do not put secret values in workflow YAML, GitHub command arguments, or committed `.env` files.

Candidate routes are `/`, `/sessions/:id/analysis`,
`/sessions/:id/interview`, and `/sessions/:id/report`. The owner question-bank
route is `/question-bank`.

The `.env.example` profiles are set to the Vertex target runtime. For
provider-free development, set `APP_ANSWER_EVALUATION_PROFILE` and
`APP_EMBEDDING_PROFILE` to `local` (question import remains AI-backed because
its structured enrichment is provider-required). Provider failures fail closed with
stable API problem codes and never create partial sessions or evaluations.

If `VERTEX_PROJECT_ID` is missing while an `ai` profile is enabled, backend
startup now fails fast with a targeted validation error before Spring AI can
emit a lower-level autoconfiguration exception.

Spring AI is enabled for the migrated Google GenAI/Vertex adapters with
`SPRING_AI_MODEL_CHAT=google-genai` and `SPRING_AI_MODEL_EMBEDDING_TEXT=google-genai` are enabled by default. Use the three `ai` profiles and
`AI_DATA_RETENTION_ACKNOWLEDGED=true` for live validation.
For a provider-outage drill with no ADC mounted, set
`APP_SPRING_AI_CREDENTIALS_AVAILABLE=false`; the application will boot and
return its stable provider-unavailable API problem instead of failing startup.

For local Vertex setup:

```powershell
gcloud auth application-default login
gcloud auth application-default set-quota-project intervu-ai-20260704-8f3c
gcloud services enable aiplatform.googleapis.com --project intervu-ai-20260704-8f3c
```

Spring AI uses Application Default Credentials for Vertex mode, avoiding
short-lived token configuration in `.env`.

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

Production does not seed demo questions. Upload owner questions through the
question-bank flow: the analyze step classifies each row and proposes new
skills, then the owner approves skills individually before importing valid
rows. Approved skills and question metadata are stored in PostgreSQL; vectors
are rebuilt asynchronously. Set `APP_QUESTION_BANK_SEED_ENABLED=true` only for
local demo environments; keep it `false` in production.
