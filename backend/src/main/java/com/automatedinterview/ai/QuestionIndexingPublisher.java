package com.automatedinterview.ai;

import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.ObjectMapper;

@Component
public class QuestionIndexingPublisher {
    private static final Logger log = LoggerFactory.getLogger(QuestionIndexingPublisher.class);
    private static final Duration SEND_TIMEOUT = Duration.ofSeconds(10);

    private final ApplicationEventPublisher events;
    private final KafkaTemplate<String, String> kafka;
    private final KafkaIndexingProperties properties;
    private final ObjectMapper json;

    public QuestionIndexingPublisher(ApplicationEventPublisher events,
                                     KafkaTemplate<String, String> kafka,
                                     KafkaIndexingProperties properties,
                                     ObjectMapper json) {
        this.events = events;
        this.kafka = kafka;
        this.properties = properties;
        this.json = json;
    }

    public void requestUpsert(UUID questionId) {
        events.publishEvent(new QuestionIndexingRequested(questionId, QuestionIndexingEvent.Operation.UPSERT));
    }

    public void requestDelete(UUID questionId) {
        events.publishEvent(new QuestionIndexingRequested(questionId, QuestionIndexingEvent.Operation.DELETE));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishAfterCommit(QuestionIndexingRequested request) {
        publishNow(new QuestionIndexingEvent(request.questionId(), request.operation()));
    }

    public void publishNow(QuestionIndexingEvent event) {
        if (!properties.enabled()) {
            log.debug("Kafka indexing disabled; skipping event for {}", event.questionId());
            return;
        }
        try {
            String payload = json.writeValueAsString(event);
            kafka.send(properties.topic(), event.questionId().toString(), payload)
                .get(SEND_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            log.error("Could not publish question indexing event for {}", event.questionId(), exception);
            throw new QuestionIndexingPublishException("Could not publish question indexing event", exception);
        }
    }

    public record QuestionIndexingRequested(UUID questionId, QuestionIndexingEvent.Operation operation) { }

    public static class QuestionIndexingPublishException extends RuntimeException {
        public QuestionIndexingPublishException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
