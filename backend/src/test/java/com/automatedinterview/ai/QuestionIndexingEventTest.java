package com.automatedinterview.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuestionIndexingEventTest {
    @Test
    void eventCarriesStableQuestionIdentityAndOperation() {
        UUID questionId = UUID.randomUUID();

        QuestionIndexingEvent event = new QuestionIndexingEvent(
            questionId,
            QuestionIndexingEvent.Operation.UPSERT
        );

        assertEquals(questionId, event.questionId());
        assertEquals(QuestionIndexingEvent.Operation.UPSERT, event.operation());
    }
}
