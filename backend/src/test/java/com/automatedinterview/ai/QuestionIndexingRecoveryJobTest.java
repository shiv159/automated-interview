package com.automatedinterview.ai;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuestionIndexingRecoveryJobTest {
    @Test
    void republishesPendingAndResidualVectorCandidates() {
        var state = org.mockito.Mockito.mock(QuestionIndexingStateRepository.class);
        var publisher = org.mockito.Mockito.mock(QuestionIndexingPublisher.class);
        UUID upsertId = UUID.randomUUID();
        UUID deleteId = UUID.randomUUID();
        when(state.recoveryCandidates()).thenReturn(List.of(
            new QuestionIndexingStateRepository.RecoveryCandidate(upsertId, QuestionIndexingEvent.Operation.UPSERT),
            new QuestionIndexingStateRepository.RecoveryCandidate(deleteId, QuestionIndexingEvent.Operation.DELETE)));

        new QuestionIndexingRecoveryJob(state, publisher).recover();

        verify(publisher).publishNow(new QuestionIndexingEvent(upsertId, QuestionIndexingEvent.Operation.UPSERT));
        verify(publisher).publishNow(new QuestionIndexingEvent(deleteId, QuestionIndexingEvent.Operation.DELETE));
        verify(state).queueRecovery(upsertId);
        verify(state).queueRecovery(deleteId);
    }
}
