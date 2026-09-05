package com.automatedinterview.ai;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "app.kafka.indexing.enabled", havingValue = "true")
public class QuestionIndexingConsumer {
    private final QuestionIndexingStateRepository state;
    private final VectorSyncService vectors;
    private final ObjectMapper json;

    public QuestionIndexingConsumer(QuestionIndexingStateRepository state, VectorSyncService vectors, ObjectMapper json) {
        this.state = state;
        this.vectors = vectors;
        this.json = json;
    }

    @KafkaListener(topics = "${app.kafka.indexing.topic}", groupId = "${app.kafka.indexing.group-id}")
    public void consume(String payload, Acknowledgment acknowledgment) {
        QuestionIndexingEvent event;
        try {
            event = json.readValue(payload, QuestionIndexingEvent.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid question indexing event", exception);
        }

        if (event.operation() == QuestionIndexingEvent.Operation.DELETE) {
            process(event.questionId(), acknowledgment, true);
            return;
        }
        process(event.questionId(), acknowledgment, false);
    }

    private void process(UUID questionId, Acknowledgment acknowledgment, boolean delete) {
        var snapshot = state.find(questionId);
        if (snapshot.isEmpty()) {
            acknowledgment.acknowledge();
            return;
        }
        if (!state.claim(questionId)) {
            acknowledgment.acknowledge();
            return;
        }

        var question = state.find(questionId).orElseThrow();
        try {
            if (delete || "INACTIVE".equals(question.status())) {
                vectors.delete(question.id());
            } else {
                vectors.upsert(question.id(), question.stem(), question.type(), question.primarySkill(),
                    question.difficulty(), question.secondarySkills(), question.tags(), question.status());
            }
            state.markIndexed(question.id(), question.sourceHash());
            acknowledgment.acknowledge();
        } catch (RuntimeException exception) {
            state.markRetryable(question.id(), exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            throw exception;
        }
    }
}
