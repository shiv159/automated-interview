package com.automatedinterview.ai;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class QuestionIndexingWorker {
    private static final Logger log = LoggerFactory.getLogger(QuestionIndexingWorker.class);
    private static final long MAX_DELAY_MILLIS = 3_600_000L;
    private final JdbcClient jdbc;
    private final VectorSyncService vectorSync;
    private final int batchSize;
    private final int maxAttempts;
    private final long leaseMillis;

    public QuestionIndexingWorker(JdbcClient jdbc, VectorSyncService vectorSync,
            @Value("${APP_AI_INDEXING_BATCH_SIZE:32}") int batchSize,
            @Value("${APP_AI_INDEXING_MAX_ATTEMPTS:8}") int maxAttempts,
            @Value("${APP_AI_INDEXING_LEASE_MS:600000}") long leaseMillis) {
        this.jdbc = jdbc;
        this.vectorSync = vectorSync;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.leaseMillis = leaseMillis;
    }

    @Scheduled(fixedDelayString = "${APP_AI_INDEXING_POLL_MS:5000}")
    public void processDueQuestions() {
        for (Question question : claimBatch()) {
            try {
                vectorSync.upsert(question.id(), question.stem(), question.type(), question.skill(), question.difficulty(), "ACTIVE");
                markIndexed(question);
            } catch (Exception exception) {
                markFailed(question, exception);
                log.warn("Question indexing failed id={} attempt={}", question.id(), question.attempts());
            }
        }
    }

    @Transactional
    List<Question> claimBatch() {
        return jdbc.sql("""
            WITH claimed AS (
                SELECT id FROM question
                WHERE status = 'ACTIVE'
                  AND (
                    (indexing_status = 'PENDING' AND COALESCE(indexing_next_attempt_at, now()) <= now())
                    OR (indexing_status = 'FAILED' AND indexing_attempts < :maxAttempts
                        AND COALESCE(indexing_next_attempt_at, now()) <= now())
                    OR (indexing_status = 'PROCESSING' AND indexing_next_attempt_at <= now())
                  )
                ORDER BY updated_at, id
                FOR UPDATE SKIP LOCKED
                LIMIT :batchSize
            )
            UPDATE question q
            SET indexing_status = 'PROCESSING',
                indexing_attempts = q.indexing_attempts + 1,
                indexing_next_attempt_at = now() + (:leaseMillis * interval '1 millisecond'),
                indexing_last_error = NULL
            FROM claimed c
            WHERE q.id = c.id
            RETURNING q.id, q.stem, q.type, COALESCE(q.primary_skill, ''),
                      COALESCE(q.difficulty, ''), q.source_hash, q.indexing_attempts
            """)
            .param("maxAttempts", maxAttempts).param("batchSize", batchSize).param("leaseMillis", leaseMillis)
            .query((rs, row) -> new Question(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                    rs.getString(4), rs.getString(5), rs.getString(6), rs.getInt(7))).list();
    }

    @Transactional
    void markIndexed(Question question) {
        int updated = jdbc.sql("""
            UPDATE question SET indexing_status = 'INDEXED', indexing_next_attempt_at = NULL,
                indexing_last_error = NULL, indexed_source_hash = source_hash, indexed_at = now()
            WHERE id = :id AND status = 'ACTIVE' AND indexing_status = 'PROCESSING' AND source_hash = :sourceHash
            """).param("id", question.id()).param("sourceHash", question.sourceHash()).update();
        if (updated == 0) vectorSync.delete(question.id());
    }

    @Transactional
    void markFailed(Question question, Exception exception) {
        String message = exception.getClass().getSimpleName();
        jdbc.sql("""
            UPDATE question
            SET indexing_status = 'FAILED',
                indexing_next_attempt_at = CASE WHEN indexing_attempts >= :maxAttempts THEN NULL
                    ELSE now() + (:delayMillis * interval '1 millisecond') END,
                indexing_last_error = :error
            WHERE id = :id AND indexing_status = 'PROCESSING'
            """).param("id", question.id()).param("maxAttempts", maxAttempts)
            .param("delayMillis", retryDelayMillis(question.attempts())).param("error", message).update();
    }

    static long retryDelayMillis(int attempts) {
        long multiplier = 1L << Math.min(Math.max(attempts - 1, 0), 10);
        return Math.min(MAX_DELAY_MILLIS, 60_000L * multiplier);
    }

    record Question(UUID id, String stem, String type, String skill, String difficulty, String sourceHash, int attempts) { }
}
