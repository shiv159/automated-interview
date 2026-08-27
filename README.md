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

## Database Architecture & ER Diagram

The platform utilizes PostgreSQL with the `pgvector` extension for relational data, question cataloging, session state tracking, and vector-based semantic retrieval.

```mermaid
erDiagram
    SKILL ||--o{ QUESTION : "categorizes"
    SKILL ||--o{ SESSION_SKILL : "referenced in"
    QUESTION ||--o{ SESSION_QUESTION : "instantiated as"
    QUESTION ||..|| VECTOR_STORE : "indexed in (pgvector)"
    INTERVIEW_SESSION ||--o{ SESSION_SKILL : "extracted skills"
    INTERVIEW_SESSION ||--o{ SESSION_QUESTION : "assigned questions"
    SESSION_QUESTION ||--o| EVALUATION : "evaluated by"

    SKILL {
        varchar id PK "Canonical skill identifier (e.g. CORE_JAVA)"
        varchar display_name "Human readable name"
        jsonb aliases "Recognized skill variations & synonyms"
        varchar catalog_version "Catalog version tag"
        timestamptz created_at
    }

    QUESTION {
        uuid id PK
        varchar content_hash UK "SHA-256 for deduplication"
        text stem "Question prompt text"
        varchar type "TECHNICAL or BEHAVIORAL"
        varchar primary_skill FK "Associated skill ID"
        varchar difficulty "Difficulty tier"
        jsonb tags "Keywords / topics"
        jsonb rubric "Evaluation rubrics"
        text ideal_answer "Reference answer"
        varchar origin "SEED or OWNER_IMPORT"
        varchar status "ACTIVE or INACTIVE"
        varchar indexing_status "PENDING, PROCESSING, INDEXED, FAILED"
        timestamptz created_at
    }

    VECTOR_STORE {
        uuid id PK "Corresponds to Question ID"
        text content "Indexed question text"
        json metadata "Filtering metadata (skill, difficulty, type)"
        vector_768 embedding "HNSW indexed cosine embedding"
    }

    INTERVIEW_SESSION {
        uuid id PK
        varchar token_hash "Secure candidate access token"
        varchar role_title "Target job role"
        varchar state "READY, INTERVIEWING, REPORT_READY, DELETED"
        integer years_experience "Years of experience"
        varchar difficulty "Target interview level"
        numeric profile_match "Match score (0-100%)"
        jsonb soft_skill_requirements "Soft skills extracted"
        jsonb domain_requirements "Domain skills extracted"
        timestamptz created_at
        timestamptz expires_at
    }

    SESSION_SKILL {
        uuid session_id PK,FK
        varchar document_type PK "JOB or RESUME"
        varchar skill_id PK,FK
        varchar importance "Skill weight/priority"
        boolean matched "Matched between job & resume"
        text evidence "Extracted textual evidence"
    }

    SESSION_QUESTION {
        uuid id PK
        uuid session_id FK
        uuid question_id FK
        integer position "Question sequence (1 to 3)"
        varchar status "LOCKED, ACTIVE, EVALUATING, EVALUATED"
        text stem "Snapshot of question stem"
        jsonb criteria "Evaluation rubric snapshot"
        timestamptz accepted_at
    }

    EVALUATION {
        uuid id PK
        uuid session_question_id FK,UK
        jsonb criteria_scores "Criteria-level breakdown"
        jsonb strengths "Identified strong points"
        jsonb improvements "Areas for growth"
        numeric score "Overall normalized score (0-100)"
        varchar adapter "AI adapter used (vertex / local)"
        varchar model "Model identifier"
        timestamptz created_at
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

Production does not seed demo questions. Import owner questions through the
question-bank flow. Set `APP_QUESTION_BANK_SEED_ENABLED=true` only for local
demo environments; keep it `false` in production.
