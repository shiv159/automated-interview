-- Spring AI PgVectorStore uses 'vector_store' as its default table.
-- We create it via Flyway (initialize-schema: false in application.yml) so that
-- Spring AI does not attempt to own schema creation at startup.

CREATE TABLE IF NOT EXISTS vector_store (
    id       uuid         PRIMARY KEY,
    content  text,
    metadata json,
    embedding vector(768)
);

-- Create an HNSW index for fast cosine similarity search.
CREATE INDEX IF NOT EXISTS vector_store_embedding_idx
    ON vector_store USING hnsw (embedding vector_cosine_ops);

-- -----------------------------------------------------------------------
-- Backfill: migrate existing question_embedding rows whose dimension = 768.
-- Rows with a different dimension (e.g. 64-dim local vectors) are skipped
-- here and will be rebuilt by VectorReconciliationJob at application startup.
-- -----------------------------------------------------------------------
INSERT INTO vector_store (id, content, metadata, embedding)
SELECT
    q.id,
    q.stem,
    json_build_object(
        'question_id',   q.id::text,
        'type',          q.type,
        'primary_skill', COALESCE(q.primary_skill, ''),
        'difficulty',    COALESCE(q.difficulty, ''),
        'status',        q.status
    ),
    qe.embedding
FROM question q
JOIN question_embedding qe ON qe.question_id = q.id
WHERE vector_dims(qe.embedding) = 768
ON CONFLICT (id) DO NOTHING;

-- Drop the now-superseded question_embedding table.
-- All vector data lives in vector_store going forward.
DROP TABLE IF EXISTS question_embedding;
