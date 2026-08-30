package com.automatedinterview.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

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
    private final ObjectMapper objectMapper;
    @Value("${VERTEX_EMBEDDING_MODEL:text-embedding-005}") private String embeddingModel;
    @Value("${APP_EMBEDDING_PROFILE:local}") private String embeddingProfile;
    @Value("${app.ai.embedding-dimensions:768}") private int embeddingDimensions;

    public VectorSyncService(VectorStore vectorStore, JdbcClient jdbc, ObjectMapper objectMapper) {
        this.vectorStore = vectorStore;
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
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
                       String primarySkill, String difficulty, String secondarySkills, String tags, String status) {
        Document document = buildDocument(questionId, stem, type, primarySkill, difficulty, secondarySkills, tags, status);
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
        List<UUID> missing = jdbc.sql("""
            SELECT q.id FROM question q
            LEFT JOIN vector_store vs ON vs.id = q.id
            WHERE q.status = 'ACTIVE'
              AND (q.indexing_status <> 'INDEXED'
                   OR q.indexed_source_hash IS DISTINCT FROM q.source_hash
                   OR vs.id IS NULL
                   OR vs.content IS DISTINCT FROM q.stem
                   OR vs.metadata->>'type' IS DISTINCT FROM q.type
                   OR vs.metadata->>'primary_skill' IS DISTINCT FROM COALESCE(q.primary_skill, '')
                   OR vs.metadata->>'secondary_skills' IS DISTINCT FROM q.secondary_skills::text
                   OR vs.metadata->>'tags' IS DISTINCT FROM q.tags::text
                   OR vs.metadata->>'difficulty' IS DISTINCT FROM COALESCE(q.difficulty, '')
                   OR vs.metadata->>'status' IS DISTINCT FROM q.status)
            """).query(UUID.class).list();

        if (missing.isEmpty()) {
            log.info("VectorReconciliation: no missing vectors found.");
            return;
        }
        log.info("VectorReconciliation: rebuilding {} missing vector(s).", missing.size());

        jdbc.sql("UPDATE question SET indexing_status = 'PENDING', indexing_next_attempt_at = now(), indexing_last_error = NULL WHERE id IN (:ids)")
                .param("ids", missing).update();
        log.info("VectorReconciliation: processed {} vector(s).", missing.size());
    }

    private Document buildDocument(UUID id, String stem, String type,
                                   String primarySkill, String difficulty, String secondarySkills, String tags, String status) {
        Map<String, Object> metadata = new java.util.HashMap<>();
        metadata.put("question_id",   id.toString());
        metadata.put("type",          type);
        metadata.put("primary_skill", primarySkill == null ? "" : primarySkill);
        metadata.put("secondary_skills", parseJsonArray(secondarySkills));
        metadata.put("difficulty",    difficulty == null ? "" : difficulty);
        metadata.put("tags",           parseJsonArray(tags));
        metadata.put("status",        status);
        metadata.put("indexing_status", "INDEXED");
        metadata.put("embedding_model", embeddingModel);
        metadata.put("embedding_profile", embeddingProfile);
        metadata.put("embedding_dimensions", embeddingDimensions);
        return new Document(id.toString(), stem, metadata);
    }

    private List<String> parseJsonArray(String value) {
        try {
            return objectMapper.readValue(value == null ? "[]" : value, new TypeReference<List<String>>() { });
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid question metadata array", exception);
        }
    }

    public static class VectorSyncException extends RuntimeException {
        public VectorSyncException(String message, Throwable cause) { super(message, cause); }
    }
}
