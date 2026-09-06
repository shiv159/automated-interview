package com.automatedinterview.ai;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(name = "app.kafka.indexing.enabled", havingValue = "true")
public class QuestionIndexingConsumer {
    private static final Logger log = LoggerFactory.getLogger(QuestionIndexingConsumer.class);
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
            log.error("Received malformed Kafka indexing event payloadLength={} cause={}", payload == null ? 0 : payload.length(), exception.getMessage(), exception);
            throw new IllegalArgumentException("Invalid question indexing event", exception);
        }

        log.info("Received Kafka indexing event questionId={} operation={}", event.questionId(), event.operation());

        if (event.operation() == QuestionIndexingEvent.Operation.DELETE) {
            process(event.questionId(), acknowledgment, true);
            return;
        }
        process(event.questionId(), acknowledgment, false);
    }

    private void process(UUID questionId, Acknowledgment acknowledgment, boolean delete) {
        var snapshot = state.find(questionId);
        if (snapshot.isEmpty()) {
            log.warn("Skipping Kafka indexing event because question was not found questionId={} delete={}", questionId, delete);
            acknowledgment.acknowledge();
            return;
        }
        if (!state.claim(questionId)) {
            log.info("Skipping Kafka indexing event because question is already claimed questionId={} delete={}", questionId, delete);
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
            log.info("Completed Kafka indexing event questionId={} operation={} status=INDEXED", questionId, delete ? "DELETE" : "UPSERT");
        } catch (RuntimeException exception) {
            log.error("Kafka indexing event failed questionId={} operation={} cause={}", questionId, delete ? "DELETE" : "UPSERT", exception.getMessage(), exception);
            state.markRetryable(question.id(), exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage());
            throw exception;
        }
    }
}
