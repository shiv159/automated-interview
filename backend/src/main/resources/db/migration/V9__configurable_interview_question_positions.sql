ALTER TABLE session_question DROP CONSTRAINT IF EXISTS session_question_position_check;
ALTER TABLE session_question ADD CONSTRAINT session_question_position_check CHECK (position BETWEEN 1 AND 10);
