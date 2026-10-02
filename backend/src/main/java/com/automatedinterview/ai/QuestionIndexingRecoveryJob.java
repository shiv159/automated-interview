package com.automatedinterview.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.kafka.indexing.enabled", havingValue = "true")
public class QuestionIndexingRecoveryJob {
    private static final Logger log = LoggerFactory.getLogger(QuestionIndexingRecoveryJob.class);
    private final QuestionIndexingStateRepository state;
    private final QuestionIndexingPublisher publisher;

    public QuestionIndexingRecoveryJob(QuestionIndexingStateRepository state, QuestionIndexingPublisher publisher) {
        this.state = state;
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${app.kafka.indexing.recovery-ms}")
    public void recover() {
        try {
            for (var candidate : state.recoveryCandidates()) {
                state.queueRecovery(candidate.id());
                publisher.publishNow(new QuestionIndexingEvent(candidate.id(), candidate.operation()));
            }
        } catch (DataAccessResourceFailureException exception) {
            log.warn("Question indexing recovery skipped because the database is unavailable; will retry later. Cause: {}",
                    exception.getMostSpecificCause() == null ? exception.getMessage()
                            : exception.getMostSpecificCause().getMessage());
        }
    }
}
