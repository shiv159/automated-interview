ALTER TABLE question
    ADD COLUMN indexing_status varchar(16) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN indexing_attempts integer NOT NULL DEFAULT 0,
    ADD COLUMN indexing_next_attempt_at timestamptz,
    ADD COLUMN indexing_last_error text,
    ADD COLUMN indexed_source_hash varchar(64),
    ADD COLUMN indexed_at timestamptz;

ALTER TABLE question
    ADD CONSTRAINT question_indexing_status_check
    CHECK (indexing_status IN ('PENDING', 'PROCESSING', 'INDEXED', 'FAILED'));

CREATE INDEX question_indexing_queue_idx
    ON question (indexing_status, indexing_next_attempt_at, updated_at);

UPDATE question
SET indexing_next_attempt_at = now()
WHERE indexing_status = 'PENDING' AND indexing_next_attempt_at IS NULL;
