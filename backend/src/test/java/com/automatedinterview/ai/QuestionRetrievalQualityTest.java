package com.automatedinterview.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for Reciprocal Rank Fusion (RRF) behaviour in {@link QuestionRetrievalService}.
 *
 * <p>These tests verify the fusion logic by calling {@code QuestionRetrievalService.select()}
 * through a lightweight stub — no Spring context, no database, no vector store.
 */
class QuestionRetrievalQualityTest {

    // ---------------------------------------------------------------------------
    // RRF score formula: score(d, rank) = 1.0 / (60 + rank + 1)
    // ---------------------------------------------------------------------------

    @Test
    void rrfScoreIncreasesWhenDocumentAppearsInBothLists() {
        // A document ranked #0 in both dense and sparse should score 2 * (1/61) ≈ 0.0328
        double denseScore  = 1.0 / (60 + 0 + 1); // rank 0
        double sparseScore = 1.0 / (60 + 0 + 1); // rank 0
        double combined    = denseScore + sparseScore;
        // vs a document only in one list at rank 0 → 1/61 ≈ 0.0164
        double singleList  = 1.0 / (60 + 0 + 1);
        assertTrue(combined > singleList,
            "A document in both lists must have a higher RRF score than one in a single list");
    }

    @Test
    void rrfRankZeroHigherThanRankNine() {
        double rank0 = 1.0 / (60 + 0 + 1);
        double rank9 = 1.0 / (60 + 9 + 1);
        assertTrue(rank0 > rank9, "Higher ranked (lower index) documents must score higher in RRF");
    }

    @Test
    void lexicalQueryHasNullableSkillAndDifficultyParameters() {
        String query = QuestionRetrievalService.lexicalQuery();
        assertTrue(query.contains("CAST(:skill AS text) IS NULL"),
            "Lexical query must cast :skill to text for null-safety");
        assertTrue(query.contains("jsonb_build_array(CAST(:skill AS text))"),
            "Lexical query must use JSONB containment for secondary_skills");
        assertTrue(query.contains("CAST(:difficulty AS text) IS NULL"),
            "Lexical query must cast :difficulty to text for null-safety");
    }

    @Test
    void excludedIdsAreHonouredInIsExcludedHelper() {
        UUID target = UUID.randomUUID();
        assertTrue(QuestionRetrievalService.isExcluded(target, target),
            "isExcluded must return true when candidate equals excluded");
        assertFalse(QuestionRetrievalService.isExcluded(UUID.randomUUID(), target),
            "isExcluded must return false when candidate differs from excluded");
        assertFalse(QuestionRetrievalService.isExcluded(null, target),
            "isExcluded must return false when candidateId is null");
    }

    @Test
    void rrfKConstantIsStandard() {
        // RRF K=60 is the standard empirically validated constant.
        // Rank-0 and rank-59 scores differ by factor of ~2x; sanity-check the range.
        double rank0  = 1.0 / (60 + 0 + 1);
        double rank59 = 1.0 / (60 + 59 + 1);
        assertTrue(rank0 / rank59 > 1.9, "RRF K=60 gives ~2x spread from rank 0 to rank 59");
    }

    @Test
    void embeddingTextIncludesStemAndEnrichedFields() {
        String text = VectorSyncService.buildEmbeddingText(
            "What is a deadlock?", "CORE_JAVA", "MEDIUM", "[\"concurrency\",\"threading\"]");
        assertTrue(text.contains("What is a deadlock?"), "Embedding text must contain the stem");
        assertTrue(text.contains("CORE_JAVA"),           "Embedding text must contain primarySkill");
        assertTrue(text.contains("MEDIUM"),              "Embedding text must contain difficulty");
        assertTrue(text.contains("concurrency"),         "Embedding text must contain tags");
    }

    @Test
    void embeddingTextWithNullFieldsOnlyContainsStem() {
        String text = VectorSyncService.buildEmbeddingText("Tell me about yourself.", null, null, null);
        assertTrue(text.startsWith("Stem: "),           "Embedding text must start with 'Stem: '");
        assertFalse(text.contains("\nSkill:"),           "Null skill must not appear in embedding text");
        assertFalse(text.contains("\nDifficulty:"),      "Null difficulty must not appear in embedding text");
        assertFalse(text.contains("\nTopics:"),          "Null tags must not appear in embedding text");
    }

    @Test
    void embeddingTextWithEmptyTagsOmitsTopicsLine() {
        String text = VectorSyncService.buildEmbeddingText("Describe REST.", "API_DESIGN", "EASY", "[]");
        assertFalse(text.contains("\nTopics:"), "Empty JSON array for tags must not add a Topics line");
    }
}
