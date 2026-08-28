ALTER TABLE skill ADD COLUMN active boolean NOT NULL DEFAULT true;
ALTER TABLE skill ADD COLUMN source varchar(32) NOT NULL DEFAULT 'seed';

ALTER TABLE question ADD COLUMN secondary_skills jsonb NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE question ADD CONSTRAINT question_secondary_skills_array
    CHECK (jsonb_typeof(secondary_skills) = 'array');

CREATE INDEX question_secondary_skills_idx
    ON question USING gin (secondary_skills);
