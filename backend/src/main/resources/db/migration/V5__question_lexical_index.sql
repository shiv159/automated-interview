CREATE INDEX IF NOT EXISTS question_stem_fts_idx
    ON question USING gin (to_tsvector('simple', stem));
