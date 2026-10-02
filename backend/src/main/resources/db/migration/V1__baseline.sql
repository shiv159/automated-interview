CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE skill (
    id varchar(64) PRIMARY KEY,
    display_name varchar(160) NOT NULL,
    aliases jsonb NOT NULL,
    catalog_version varchar(32) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    source varchar(32) NOT NULL DEFAULT 'seed',
    created_at timestamptz NOT NULL DEFAULT now()
);

INSERT INTO skill (id, display_name, aliases, catalog_version) VALUES
('CORE_JAVA', 'Core Java', '["core java","java se","jdk","jvm","java"]'::jsonb, '2026-08-04.v1'),
('SPRING_BOOT', 'Spring Boot', '["spring boot","spring framework","spring mvc","spring data","jpa","hibernate","spring"]'::jsonb, '2026-08-04.v1'),
('SQL_RELATIONAL', 'SQL / Relational Databases', '["relational databases","relational database","rdbms","postgresql","postgres","mysql","oracle database","sql server","sql"]'::jsonb, '2026-08-04.v1'),
('ANGULAR', 'Angular', '["angular framework","angularjs","angular"]'::jsonb, '2026-08-04.v1')
ON CONFLICT (id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    aliases = EXCLUDED.aliases,
    catalog_version = EXCLUDED.catalog_version;

CREATE TABLE question (
    id uuid PRIMARY KEY,
    content_hash varchar(64) NOT NULL UNIQUE,
    stem text NOT NULL,
    type varchar(16) NOT NULL CHECK (type IN ('TECHNICAL', 'BEHAVIORAL')),
    primary_skill varchar(64) REFERENCES skill(id),
    secondary_skills jsonb NOT NULL DEFAULT '[]'::jsonb,
    difficulty varchar(16),
    tags jsonb NOT NULL DEFAULT '[]'::jsonb,
    rubric jsonb NOT NULL DEFAULT '[]'::jsonb,
    ideal_answer text,
    origin varchar(32) NOT NULL CHECK (origin IN ('SEED', 'OWNER_IMPORT')),
    status varchar(16) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    source_hash varchar(64),
    enrichment_provenance jsonb NOT NULL DEFAULT '{}'::jsonb,
    indexing_status varchar(16) NOT NULL DEFAULT 'PENDING',
    indexing_attempts integer NOT NULL DEFAULT 0,
    indexing_next_attempt_at timestamptz,
    indexing_last_error text,
    indexed_source_hash varchar(64),
    indexed_at timestamptz,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT question_indexing_status_check
        CHECK (indexing_status IN ('PENDING', 'PROCESSING', 'INDEXED', 'FAILED')),
    CONSTRAINT question_secondary_skills_array CHECK (jsonb_typeof(secondary_skills) = 'array'),
    CHECK ((type = 'BEHAVIORAL' AND primary_skill IS NULL AND difficulty IS NULL)
        OR (type = 'TECHNICAL' AND primary_skill IS NOT NULL AND difficulty IS NOT NULL))
);

CREATE INDEX question_retrieval_idx ON question(status, type, primary_skill, difficulty);
CREATE INDEX question_indexing_queue_idx ON question(indexing_status, indexing_next_attempt_at, updated_at);
CREATE INDEX question_stem_fts_idx ON question USING gin (to_tsvector('simple', stem));
CREATE INDEX question_secondary_skills_idx ON question USING gin (secondary_skills);

CREATE TABLE vector_store (
    id uuid PRIMARY KEY,
    content text,
    metadata json,
    embedding vector(768)
);

CREATE INDEX vector_store_embedding_idx
    ON vector_store USING hnsw (embedding vector_cosine_ops);

CREATE TABLE interview_session (
    id uuid PRIMARY KEY,
    token_hash varchar(64) NOT NULL,
    state varchar(32) NOT NULL CHECK (state IN ('READY', 'INTERVIEWING', 'REPORT_READY', 'DELETED')),
    years_experience integer NOT NULL CHECK (years_experience BETWEEN 0 AND 30),
    difficulty varchar(16) NOT NULL,
    profile_match numeric(6, 2) NOT NULL,
    role_title varchar(160),
    unsupported_requirements jsonb NOT NULL DEFAULT '[]'::jsonb,
    soft_skill_requirements jsonb NOT NULL DEFAULT '[]'::jsonb,
    domain_requirements jsonb NOT NULL DEFAULT '[]'::jsonb,
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
    position integer NOT NULL,
    status varchar(16) NOT NULL CHECK (status IN ('LOCKED', 'ACTIVE', 'EVALUATING', 'EVALUATED')),
    type varchar(16) NOT NULL,
    primary_skill varchar(64),
    difficulty varchar(16),
    stem text NOT NULL,
    criteria jsonb NOT NULL,
    ideal_answer text,
    source_hash varchar(64) NOT NULL,
    accepted_at timestamptz,
    CONSTRAINT session_question_position_check CHECK (position BETWEEN 1 AND 10),
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
