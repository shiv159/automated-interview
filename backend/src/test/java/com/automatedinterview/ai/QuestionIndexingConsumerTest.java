package com.automatedinterview.ai;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class QuestionIndexingConsumerTest {
    @Mock QuestionIndexingStateRepository state;
    @Mock VectorSyncService vectors;
    @Mock Acknowledgment acknowledgment;

    private QuestionIndexingConsumer consumer;
    private UUID questionId;

    @BeforeEach
    void setUp() {
        consumer = new QuestionIndexingConsumer(state, vectors, new ObjectMapper());
        questionId = UUID.randomUUID();
    }

    @Test
    void upsertsActiveQuestionBeforeAcknowledging() {
        var snapshot = snapshot("ACTIVE");
        when(state.find(questionId)).thenReturn(Optional.of(snapshot));
        when(state.claim(questionId)).thenReturn(true);

        consumer.consume(event(QuestionIndexingEvent.Operation.UPSERT), acknowledgment);

        verify(vectors).upsert(questionId, "Explain Kafka", "TECHNICAL", "SPRING_BOOT", "MEDIUM", "[]", "[]", "ACTIVE");
        verify(state).markIndexed(questionId, "source-hash");
        verify(acknowledgment).acknowledge();
    }

    @Test
    void deletesInactiveQuestionBeforeAcknowledging() {
        var snapshot = snapshot("INACTIVE");
        when(state.find(questionId)).thenReturn(Optional.of(snapshot));
        when(state.claim(questionId)).thenReturn(true);

        consumer.consume(event(QuestionIndexingEvent.Operation.DELETE), acknowledgment);

        verify(vectors).delete(questionId);
        verify(state).markIndexed(questionId, "source-hash");
        verify(acknowledgment).acknowledge();
        verify(vectors, never()).upsert(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void leavesMessageUnacknowledgedWhenVectorSyncFails() {
        var snapshot = snapshot("ACTIVE");
        when(state.find(questionId)).thenReturn(Optional.of(snapshot));
        when(state.claim(questionId)).thenReturn(true);
        when(state.find(questionId)).thenReturn(Optional.of(snapshot));
        org.mockito.Mockito.doThrow(new VectorSyncService.VectorSyncException("failed", new RuntimeException()))
            .when(vectors).upsert(questionId, "Explain Kafka", "TECHNICAL", "SPRING_BOOT", "MEDIUM", "[]", "[]", "ACTIVE");

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
            () -> consumer.consume(event(QuestionIndexingEvent.Operation.UPSERT), acknowledgment));

        verify(state).markRetryable(questionId, "failed");
        verify(acknowledgment, never()).acknowledge();
    }

    private QuestionIndexingStateRepository.QuestionSnapshot snapshot(String status) {
        return new QuestionIndexingStateRepository.QuestionSnapshot(
            questionId, "Explain Kafka", "TECHNICAL", "SPRING_BOOT", "[]", "MEDIUM", "[]", status, "source-hash", "PENDING");
    }

    private String event(QuestionIndexingEvent.Operation operation) {
        return "{\"questionId\":\"" + questionId + "\",\"operation\":\"" + operation + "\"}";
    }
}
