package com.automatedinterview.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.UUID;

class QuestionIndexingWorkerTest {
    @Test
    void retryDelayUsesCappedExponentialBackoff() {
        assertEquals(60_000L, QuestionIndexingWorker.retryDelayMillis(1));
        assertEquals(120_000L, QuestionIndexingWorker.retryDelayMillis(2));
        assertEquals(3_600_000L, QuestionIndexingWorker.retryDelayMillis(8));
    }

    @Test
    void sourceQuestionIsExcludedBySourceId() {
        UUID sourceId = UUID.randomUUID();
        assertTrue(QuestionRetrievalService.isExcluded(sourceId, sourceId));
        assertFalse(QuestionRetrievalService.isExcluded(UUID.randomUUID(), sourceId));
    }
}
