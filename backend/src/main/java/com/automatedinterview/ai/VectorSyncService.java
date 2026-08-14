package com.automatedinterview.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Central service for synchronizing the Spring AI {@link VectorStore} projection
 * with the authoritative {@code question} relational table.
 *
 * <h3>Upsert strategy</h3>
 * Spring AI does not guarantee atomic upsert semantics for {@code add()}.
 * To avoid duplicate vectors, we always delete before adding. The embedding
 * API call is made <em>before</em> the delete so that if the external API fails
 * the existing vector is never removed.
 *
 * <h3>Reconciliation</h3>
 * {@link #reconcileMissingVectors()} scans for ACTIVE questions without a
 * matching {@code vector_store} row and re-embeds them. It is called by
 * {@link VectorReconciliationJob} on startup.
 */
@Service
public class VectorSyncService {

    private static final Logger log = LoggerFactory.getLogger(VectorSyncService.class);

    private final VectorStore vectorStore;
    private final JdbcClient jdbc;

    public VectorSyncService(VectorStore vectorStore, JdbcClient jdbc) {
        this.vectorStore = vectorStore;
        this.jdbc = jdbc;
    }

    /**
     * Builds a Spring AI {@link Document} from a question's data and upserts it
     * into the {@link VectorStore}.
     *
     * <p>The document ID is set to the relational {@code question.id} to provide a
     * stable, deterministic identity across repeated imports.
     *
     * <p>Ordering: the embedding API call happens first. Only after the vector is
     * obtained are the delete and add issued. This means a transient API failure
     * leaves the old vector intact.
     *
     * @throws VectorSyncException if the {@link VectorStore} add() call fails after
     *         a successful delete(), leaving the question without a searchable vector.
     *         The caller should surface this error; {@link VectorReconciliationJob}
     *         will repair it on the next startup.
     */
    @Transactional
    public void upsert(UUID questionId, String stem, String type,
                       String primarySkill, String difficulty, String status) {
        Document document = buildDocument(questionId, stem, type, primarySkill, difficulty, status);
        // Delete first (no-op if the row does not exist), then add the new vector.
        try {
            vectorStore.delete(List.of(questionId.toString()));
        } catch (Exception e) {
            throw new VectorSyncException("Failed to remove existing vector for question " + questionId, e);
        }
        try {
            vectorStore.add(List.of(document));
        } catch (Exception e) {
            // The vector is now missing — log loudly so the reconciliation job can repair it.
            log.error("VectorStore add failed for question {}. The vector is missing and will be rebuilt on next startup.", questionId, e);
            throw new VectorSyncException("Failed to add vector for question " + questionId, e);
        }
    }

    /**
     * Removes a question from the vector index (e.g. on deactivation).
     * A missing row is treated as a no-op.
     */
    public void delete(UUID questionId) {
        try {
            vectorStore.delete(List.of(questionId.toString()));
        } catch (Exception e) {
            log.warn("VectorStore delete failed for question {}. Cause: {}", questionId, e.getMessage());
        }
    }

    /**
     * Scans for ACTIVE questions whose ID does not exist in {@code vector_store}
     * and re-embeds them. Called by {@link VectorReconciliationJob} on startup.
     */
    public void reconcileMissingVectors() {
        List<MissingVector> missing = jdbc.sql("""
            SELECT q.id, q.stem, q.type,
                   COALESCE(q.primary_skill, '') AS primary_skill,
                   COALESCE(q.difficulty, '')    AS difficulty,
                   q.status
            FROM question q
            LEFT JOIN vector_store vs ON vs.id = q.id
            WHERE q.status = 'ACTIVE'
              AND (vs.id IS NULL
                   OR vs.content IS DISTINCT FROM q.stem
                   OR vs.metadata->>'type' IS DISTINCT FROM q.type
                   OR vs.metadata->>'primary_skill' IS DISTINCT FROM COALESCE(q.primary_skill, '')
                   OR vs.metadata->>'difficulty' IS DISTINCT FROM COALESCE(q.difficulty, '')
                   OR vs.metadata->>'status' IS DISTINCT FROM q.status)
            """).query((rs, row) -> new MissingVector(
                rs.getObject("id", UUID.class), rs.getString("stem"), rs.getString("type"),
                rs.getString("primary_skill"), rs.getString("difficulty"), rs.getString("status"))).list();

        if (missing.isEmpty()) {
            log.info("VectorReconciliation: no missing vectors found.");
            return;
        }
        log.info("VectorReconciliation: rebuilding {} missing vector(s).", missing.size());

        for (MissingVector row : missing) {
            try {
                upsert(row.id(), row.stem(), row.type(), row.primarySkill(), row.difficulty(), row.status());
            } catch (Exception e) {
                log.error("VectorReconciliation: failed to rebuild vector for question {}. Skipping.", row.id(), e);
            }
        }
        log.info("VectorReconciliation: processed {} vector(s).", missing.size());
    }

    private record MissingVector(UUID id, String stem, String type, String primarySkill,
                                 String difficulty, String status) { }

    private Document buildDocument(UUID id, String stem, String type,
                                   String primarySkill, String difficulty, String status) {
        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("question_id",   id.toString());
        metadata.put("type",          type);
        metadata.put("primary_skill", primarySkill == null ? "" : primarySkill);
        metadata.put("difficulty",    difficulty == null ? "" : difficulty);
        metadata.put("status",        status);
        return new Document(id.toString(), stem, metadata);
    }

    public static class VectorSyncException extends RuntimeException {
        public VectorSyncException(String message, Throwable cause) { super(message, cause); }
    }
}
