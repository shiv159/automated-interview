CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE skill (
    id varchar(64) PRIMARY KEY,
    display_name varchar(160) NOT NULL,
    aliases jsonb NOT NULL,
    catalog_version varchar(32) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE question (
    id uuid PRIMARY KEY,
    content_hash varchar(64) NOT NULL UNIQUE,
    stem text NOT NULL,
    type varchar(16) NOT NULL CHECK (type IN ('TECHNICAL', 'BEHAVIORAL')),
    primary_skill varchar(64) REFERENCES skill(id),
    difficulty varchar(16),
    tags jsonb NOT NULL DEFAULT '[]'::jsonb,
    rubric jsonb NOT NULL DEFAULT '[]'::jsonb,
    ideal_answer text,
    origin varchar(32) NOT NULL CHECK (origin IN ('SEED', 'OWNER_IMPORT')),
    status varchar(16) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    source_hash varchar(64),
    enrichment_provenance jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK ((type = 'BEHAVIORAL' AND primary_skill IS NULL AND difficulty IS NULL)
        OR (type = 'TECHNICAL' AND primary_skill IS NOT NULL AND difficulty IS NOT NULL))
);

CREATE INDEX question_retrieval_idx ON question(status, type, primary_skill, difficulty);

CREATE TABLE question_embedding (
    question_id uuid PRIMARY KEY REFERENCES question(id) ON DELETE CASCADE,
    embedding vector NOT NULL,
    source_hash varchar(64) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE interview_session (
    id uuid PRIMARY KEY,
    token_hash varchar(64) NOT NULL,
    state varchar(32) NOT NULL CHECK (state IN ('READY', 'INTERVIEWING', 'REPORT_READY', 'DELETED')),
    years_experience integer NOT NULL CHECK (years_experience BETWEEN 0 AND 30),
    difficulty varchar(16) NOT NULL,
    profile_match numeric(6, 2) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    expires_at timestamptz NOT NULL
);

CREATE INDEX session_owner_idx ON interview_session(id, token_hash);

CREATE TABLE session_skill (
    session_id uuid NOT NULL REFERENCES interview_session(id) ON DELETE CASCADE,
    document_type varchar(16) NOT NULL CHECK (document_type IN ('JOB', 'RESUME')),
    skill_id varchar(64) NOT NULL REFERENCES skill(id),
    importance varchar(16),
    matched boolean NOT NULL,
    evidence text NOT NULL,
    PRIMARY KEY (session_id, document_type, skill_id)
);

CREATE TABLE session_question (
    id uuid PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES interview_session(id) ON DELETE CASCADE,
    question_id uuid NOT NULL REFERENCES question(id),
    position integer NOT NULL CHECK (position BETWEEN 1 AND 3),
    status varchar(16) NOT NULL CHECK (status IN ('LOCKED', 'ACTIVE', 'EVALUATING', 'EVALUATED')),
    type varchar(16) NOT NULL,
    primary_skill varchar(64),
    difficulty varchar(16),
    stem text NOT NULL,
    criteria jsonb NOT NULL,
    ideal_answer text,
    source_hash varchar(64) NOT NULL,
    accepted_at timestamptz,
    UNIQUE(session_id, position),
    UNIQUE(session_id, id)
);

CREATE TABLE evaluation (
    id uuid PRIMARY KEY,
    session_question_id uuid NOT NULL UNIQUE REFERENCES session_question(id) ON DELETE CASCADE,
    criteria_scores jsonb NOT NULL,
    strengths jsonb NOT NULL,
    improvements jsonb NOT NULL,
    score numeric(5, 2) NOT NULL,
    adapter varchar(32) NOT NULL,
    model varchar(160),
    created_at timestamptz NOT NULL DEFAULT now()
);

