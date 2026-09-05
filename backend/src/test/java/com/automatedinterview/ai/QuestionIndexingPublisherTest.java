package com.automatedinterview.ai;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

class QuestionIndexingPublisherTest {
    @Test
    void publishesJsonWithQuestionIdAsKafkaKey() {
        @SuppressWarnings("unchecked")
        KafkaTemplate<String, String> kafka = org.mockito.Mockito.mock(KafkaTemplate.class);
        ApplicationEventPublisher events = org.mockito.Mockito.mock(ApplicationEventPublisher.class);
        UUID questionId = UUID.randomUUID();
        var properties = new KafkaIndexingProperties(true, "question-indexing.v1", "question-indexing.v1.DLT", "group", 60000);
        when(kafka.send(eq("question-indexing.v1"), eq(questionId.toString()), org.mockito.ArgumentMatchers.contains("UPSERT")))
            .thenReturn(CompletableFuture.completedFuture(null));

        var publisher = new QuestionIndexingPublisher(events, kafka, properties, new ObjectMapper());
        publisher.publishNow(new QuestionIndexingEvent(questionId, QuestionIndexingEvent.Operation.UPSERT));

        verify(kafka).send(eq("question-indexing.v1"), eq(questionId.toString()), org.mockito.ArgumentMatchers.contains("UPSERT"));
    }
}
