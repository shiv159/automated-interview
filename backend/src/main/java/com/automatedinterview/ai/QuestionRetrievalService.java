package com.automatedinterview.ai;

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

@Service
public class QuestionRetrievalService {
    private static final double MIN_SCORE = 0.15;
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
        // primary_skill is scalar metadata, but secondary_skills is an array. Do not use
        // eq() for the array; the authoritative JSONB membership check below handles it.
        if (difficulty != null) filter = b.and(filter, b.eq("difficulty", difficulty));
        Map<UUID, Double> scores = new HashMap<>();
        for (Document document : vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(30)
                .filterExpression(filter.build()).build())) {
            try {
                UUID id = UUID.fromString(String.valueOf(document.getMetadata().get("question_id")));
                if (!excluded.contains(id) && document.getScore() != null && document.getScore() >= MIN_SCORE)
                    scores.put(id, document.getScore());
            } catch (RuntimeException ignored) { }
        }
        if (!scores.isEmpty()) {
            String skillClause = skill == null ? "" : " AND (primary_skill = :skill OR secondary_skills @> jsonb_build_array(CAST(:skill AS text)))";
            List<UUID> compatible = jdbc.sql("""
                SELECT id FROM question
                WHERE id IN (:candidateIds) AND status = 'ACTIVE' AND indexing_status = 'INDEXED'
                  AND type = :type
                """ + skillClause + " AND (CAST(:difficulty AS text) IS NULL OR difficulty = :difficulty)")
                .param("candidateIds", scores.keySet()).param("type", type).param("skill", skill)
                .param("difficulty", difficulty).query(UUID.class).list();
            scores.keySet().retainAll(Set.copyOf(compatible));
        }
        jdbc.sql(LEXICAL_QUERY).param("query", query).param("type", type).param("skill", skill).param("difficulty", difficulty)
            .query((rs, row) -> Map.entry(rs.getObject("id", UUID.class), rs.getDouble("rank"))).list()
            .forEach(entry -> { if (!excluded.contains(entry.getKey())) scores.merge(entry.getKey(), entry.getValue() * .15, Double::sum); });
        UUID result = scores.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        meters.counter("automated_interview.retrieval.requests", "type", type).increment();
        if (result == null) meters.counter("automated_interview.retrieval.empty", "type", type).increment();
        sample.stop(meters.timer("automated_interview.retrieval.latency", "type", type));
        return result;
    }

    static String lexicalQuery() {
        return LEXICAL_QUERY;
    }

    public String context(String query, UUID excludedId) {
        var b = new FilterExpressionBuilder();
        var filter = b.and(b.eq("status", "ACTIVE"), b.eq("indexing_status", "INDEXED"));
        return vectorStore.similaritySearch(SearchRequest.builder().query(query).topK(3).filterExpression(filter.build()).build())
                .stream().filter(doc -> !isExcluded(metadataQuestionId(doc), excludedId))
                .map(Document::getText).filter(text -> text != null && !text.isBlank()).reduce((a, bText) -> a + "\n---\n" + bText).orElse("");
    }

    static boolean isExcluded(UUID candidateId, UUID excludedId) {
        return candidateId != null && candidateId.equals(excludedId);
    }

    private static UUID metadataQuestionId(Document document) {
        try { return UUID.fromString(String.valueOf(document.getMetadata().get("question_id"))); }
        catch (RuntimeException ignored) { return null; }
    }
}
