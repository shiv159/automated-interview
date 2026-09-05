package com.automatedinterview.ai;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.indexing.enabled", havingValue = "true")
public class QuestionIndexingRecoveryJob {
    private final QuestionIndexingStateRepository state;
    private final QuestionIndexingPublisher publisher;

    public QuestionIndexingRecoveryJob(QuestionIndexingStateRepository state, QuestionIndexingPublisher publisher) {
        this.state = state;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${app.kafka.indexing.recovery-ms}")
    public void recover() {
        for (var candidate : state.recoveryCandidates()) {
            state.queueRecovery(candidate.id());
            publisher.publishNow(new QuestionIndexingEvent(candidate.id(), candidate.operation()));
        }
    }
}
