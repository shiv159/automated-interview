package com.automatedinterview.ai;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class QuestionIndexingStateRepository {
    private final JdbcClient jdbc;

    public QuestionIndexingStateRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<QuestionSnapshot> find(UUID id) {
        return jdbc.sql("""
                SELECT id, stem, type, primary_skill, secondary_skills, difficulty, tags,
                       status, source_hash, indexing_status
                FROM question WHERE id = :id
                """).param("id", id).query((rs, row) -> new QuestionSnapshot(
                    rs.getObject("id", UUID.class), rs.getString("stem"), rs.getString("type"),
                    rs.getString("primary_skill"), rs.getString("secondary_skills"), rs.getString("difficulty"),
                    rs.getString("tags"), rs.getString("status"), rs.getString("source_hash"),
                    rs.getString("indexing_status"))).optional();
    }

    @Transactional
    public boolean claim(UUID id) {
        return jdbc.sql("""
                UPDATE question
                SET indexing_status = 'PROCESSING',
                    indexing_attempts = indexing_attempts + 1,
                    indexing_next_attempt_at = now() + interval '10 minutes',
                    indexing_last_error = NULL
                WHERE id = :id
                  AND ((indexing_status = 'PENDING' AND COALESCE(indexing_next_attempt_at, now()) <= now())
                       OR (indexing_status = 'PROCESSING' AND indexing_next_attempt_at <= now()))
                """).param("id", id).update() == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markIndexed(UUID id, String sourceHash) {
        jdbc.sql("""
                UPDATE question
                SET indexing_status = 'INDEXED', indexing_next_attempt_at = NULL,
                    indexing_last_error = NULL, indexed_source_hash = :sourceHash, indexed_at = now()
                WHERE id = :id AND indexing_status = 'PROCESSING' AND source_hash = :sourceHash
                """).param("id", id).param("sourceHash", sourceHash).update();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markRetryable(UUID id, String message) {
        jdbc.sql("""
                UPDATE question
                SET indexing_status = 'PENDING', indexing_next_attempt_at = now(), indexing_last_error = :message
                WHERE id = :id
                """).param("id", id).param("message", message).update();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID id, String message) {
        jdbc.sql("""
                UPDATE question
                SET indexing_status = 'FAILED', indexing_next_attempt_at = NULL, indexing_last_error = :message
                WHERE id = :id
                """).param("id", id).param("message", message).update();
    }

    public List<RecoveryCandidate> recoveryCandidates() {
        return jdbc.sql("""
                SELECT q.id,
                       CASE WHEN q.status = 'INACTIVE' THEN 'DELETE' ELSE 'UPSERT' END AS operation
                FROM question q
                LEFT JOIN vector_store vs ON vs.id = q.id
                WHERE (q.status = 'ACTIVE' AND q.indexing_status <> 'FAILED' AND (
                         (q.indexing_status IN ('PENDING', 'PROCESSING') AND COALESCE(q.indexing_next_attempt_at, now()) <= now())
                         OR q.indexed_source_hash IS DISTINCT FROM q.source_hash
                         OR vs.id IS NULL
                         OR vs.content IS DISTINCT FROM q.stem
                         OR vs.metadata->>'type' IS DISTINCT FROM q.type
                         OR vs.metadata->>'primary_skill' IS DISTINCT FROM COALESCE(q.primary_skill, '')
                         OR vs.metadata->>'secondary_skills' IS DISTINCT FROM q.secondary_skills::text
                         OR vs.metadata->>'tags' IS DISTINCT FROM q.tags::text
                         OR vs.metadata->>'difficulty' IS DISTINCT FROM COALESCE(q.difficulty, '')
                         OR vs.metadata->>'status' IS DISTINCT FROM q.status
                       ))
                   OR (q.status = 'INACTIVE' AND vs.id IS NOT NULL)
                ORDER BY q.updated_at, q.id
                LIMIT 100
                """).query((rs, row) -> new RecoveryCandidate(
                    rs.getObject("id", UUID.class),
                    QuestionIndexingEvent.Operation.valueOf(rs.getString("operation")))).list();
    }

    @Transactional
    public void queueRecovery(UUID id) {
        jdbc.sql("""
                UPDATE question
                SET indexing_status = 'PENDING', indexing_next_attempt_at = now(), indexing_last_error = NULL
                WHERE id = :id AND indexing_status <> 'FAILED'
                """).param("id", id).update();
    }

    public record QuestionSnapshot(UUID id, String stem, String type, String primarySkill,
                                   String secondarySkills, String difficulty, String tags,
                                   String status, String sourceHash, String indexingStatus) { }

    public record RecoveryCandidate(UUID id, QuestionIndexingEvent.Operation operation) { }
}
