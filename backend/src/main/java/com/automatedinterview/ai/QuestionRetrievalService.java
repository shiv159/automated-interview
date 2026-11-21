package com.automatedinterview.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Retrieves the best-matching question for a given query using hybrid search with
 * Reciprocal Rank Fusion (RRF). Dense vector results and sparse BM25/ts_rank results
 * are ranked independently and fused via RRF (k=60) to avoid calibration issues
 * between uncalibrated ts_rank floats and cosine similarity scores.
 */
@Service
public class QuestionRetrievalService {
    private static final double MIN_SCORE = 0.15;
    /** RRF smoothing constant — standard default is 60. */
    private static final int RRF_K = 60;
    private static final String LEXICAL_QUERY = """
            SELECT id, ts_rank(to_tsvector('simple', stem), plainto_tsquery('simple', :query)) AS rank
            FROM question WHERE status = 'ACTIVE' AND indexing_status = 'INDEXED'
              AND type = :type AND (CAST(:skill AS text) IS NULL OR primary_skill = :skill OR secondary_skills @> jsonb_build_array(CAST(:skill AS text)))
              AND (CAST(:difficulty AS text) IS NULL OR difficulty = :difficulty)
              AND to_tsvector('simple', stem) @@ plainto_tsquery('simple', :query)
            ORDER BY rank DESC LIMIT 10
            """;
    private final VectorStore vectorStore;
    private final JdbcClient jdbc;
    private final MeterRegistry meters;

    public QuestionRetrievalService(VectorStore vectorStore, JdbcClient jdbc, MeterRegistry meters) {
        this.vectorStore = vectorStore;
        this.jdbc = jdbc;
        this.meters = meters;
    }

    public UUID select(String query, String type, String skill, String difficulty, List<UUID> excluded) {
        var sample = io.micrometer.core.instrument.Timer.start(meters);
        var b = new FilterExpressionBuilder();
        var filter = b.and(b.eq("type", type), b.and(b.eq("status", "ACTIVE"), b.eq("indexing_status", "INDEXED")));
        if (difficulty != null) filter = b.and(filter, b.eq("difficulty", difficulty));

        // --- Dense retrieval (top-30 by cosine similarity) ---
        List<UUID> denseRanked = new ArrayList<>();
        Map<UUID, Double> denseScores = new HashMap<>();
        for (Document document : vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(30)
                .filterExpression(filter.build()).build())) {
            try {
                UUID id = UUID.fromString(String.valueOf(document.getMetadata().get("question_id")));
                if (!excluded.contains(id) && document.getScore() != null && document.getScore() >= MIN_SCORE) {
                    denseRanked.add(id);
                    denseScores.put(id, document.getScore());
                }
            } catch (RuntimeException ignored) { }
        }

        // --- Sparse / lexical retrieval (top-10 by ts_rank) ---
        List<UUID> sparseRanked = new ArrayList<>();
        jdbc.sql(LEXICAL_QUERY).param("query", query).param("type", type).param("skill", skill).param("difficulty", difficulty)
            .query((rs, row) -> rs.getObject("id", UUID.class)).list()
            .forEach(id -> { if (!excluded.contains(id)) sparseRanked.add(id); });

        // --- Skill constraint filter: apply JSONB membership check on union of candidates ---
        Set<UUID> allCandidates = new java.util.LinkedHashSet<>();
        allCandidates.addAll(denseRanked);
        allCandidates.addAll(sparseRanked);

        if (!allCandidates.isEmpty()) {
            String skillClause = skill == null ? "" : " AND (primary_skill = :skill OR secondary_skills @> jsonb_build_array(CAST(:skill AS text)))";
            Set<UUID> compatible = new java.util.HashSet<>(jdbc.sql("""
                SELECT id FROM question
                WHERE id IN (:candidateIds) AND status = 'ACTIVE' AND indexing_status = 'INDEXED'
                  AND type = :type
                """ + skillClause + " AND (CAST(:difficulty AS text) IS NULL OR difficulty = :difficulty)")
                .param("candidateIds", allCandidates).param("type", type).param("skill", skill)
                .param("difficulty", difficulty).query(UUID.class).list());
            denseRanked.removeIf(id -> !compatible.contains(id));
            sparseRanked.removeIf(id -> !compatible.contains(id));
        }

        // --- Reciprocal Rank Fusion ---
        Map<UUID, Double> rrfScores = new HashMap<>();
        for (int rank = 0; rank < denseRanked.size(); rank++) {
            UUID id = denseRanked.get(rank);
            rrfScores.merge(id, 1.0 / (RRF_K + rank + 1), Double::sum);
        }
        for (int rank = 0; rank < sparseRanked.size(); rank++) {
            UUID id = sparseRanked.get(rank);
            rrfScores.merge(id, 1.0 / (RRF_K + rank + 1), Double::sum);
        }

        UUID result = rrfScores.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        meters.counter("automated_interview.retrieval.requests", "type", type).increment();
        if (result == null) meters.counter("automated_interview.retrieval.empty", "type", type).increment();
        sample.stop(meters.timer("automated_interview.retrieval.latency", "type", type));
        return result;
    }

    static String lexicalQuery() {
        return LEXICAL_QUERY;
    }

    /** Returns true when {@code candidateId} should be excluded because it equals {@code excludedId}. */
    static boolean isExcluded(UUID candidateId, UUID excludedId) {
        return candidateId != null && candidateId.equals(excludedId);
    }
}
