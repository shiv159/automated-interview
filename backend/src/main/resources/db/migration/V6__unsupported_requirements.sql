ALTER TABLE interview_session ADD COLUMN unsupported_requirements jsonb NOT NULL DEFAULT '[]'::jsonb;
