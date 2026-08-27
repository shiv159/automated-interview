ALTER TABLE interview_session ADD COLUMN soft_skill_requirements jsonb NOT NULL DEFAULT '[]'::jsonb;
ALTER TABLE interview_session ADD COLUMN domain_requirements jsonb NOT NULL DEFAULT '[]'::jsonb;
