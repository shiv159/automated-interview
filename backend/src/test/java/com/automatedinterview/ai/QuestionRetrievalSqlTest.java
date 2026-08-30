package com.automatedinterview.ai;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class QuestionRetrievalSqlTest {
    @Test
    void lexicalQueryTypesNullableSkillAndDifficultyParameters() {
        String query = QuestionRetrievalService.lexicalQuery();

        assertTrue(query.contains("CAST(:skill AS text) IS NULL"));
        assertTrue(query.contains("jsonb_build_array(CAST(:skill AS text))"));
        assertTrue(query.contains("CAST(:difficulty AS text) IS NULL"));
    }
}
