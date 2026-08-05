package com.automatedinterview.questionbank;

import com.automatedinterview.ai.VertexEmbeddingService;
import com.automatedinterview.catalog.SkillCatalog;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

@Component
@Order(10)
public class QuestionSeedLoader implements CommandLineRunner {
    private final JdbcClient jdbc;
    private final VertexEmbeddingService embeddings;
    private final String embeddingProfile;

    public QuestionSeedLoader(JdbcClient jdbc, VertexEmbeddingService embeddings,
        @Value("${APP_EMBEDDING_PROFILE:local}") String embeddingProfile) {
        this.jdbc = jdbc; this.embeddings = embeddings; this.embeddingProfile = embeddingProfile;
    }

    @Override
    public void run(String... args) {
        List<String> difficulties = List.of("EASY", "MEDIUM", "HARD");
        for (SkillCatalog.Skill skill : SkillCatalog.SKILLS) {
            for (String difficulty : difficulties) {
                for (int index = 1; index <= 2; index++) {
                    String stem = "Explain a " + difficulty.toLowerCase() + " " + skill.displayName() + " design problem and how you would solve it (seed " + index + ").";
                    upsert(UUID.nameUUIDFromBytes((skill.id() + difficulty + index).getBytes(StandardCharsets.UTF_8)), stem, skill.id(), difficulty, "[\"CORRECTNESS\",\"DEPTH\",\"CLARITY\"]", "SEED");
                }
            }
        }
        upsert(UUID.nameUUIDFromBytes("behavioral-1".getBytes(StandardCharsets.UTF_8)), "Tell me about a time you solved a difficult problem with a team.", null, null, "[\"situation\",\"action\",\"result\",\"reflection\"]", "SEED");
        upsert(UUID.nameUUIDFromBytes("behavioral-2".getBytes(StandardCharsets.UTF_8)), "Describe a time you learned a new technology under pressure.", null, null, "[\"situation\",\"action\",\"result\",\"reflection\"]", "SEED");
    }

    private void upsert(UUID id, String stem, String skill, String difficulty, String criteria, String origin) {
        String hash = hash(stem);
        String ideal = "A clear, structured answer covering the relevant " + (skill == null ? "situation, personal action, result, and reflection" : skill) + ".";
        String embedding = embeddingProfile.equals("ai") ? embeddings.embed(stem) : LocalEmbedding.vector(stem);
        jdbc.sql("""
            INSERT INTO question (id, content_hash, stem, type, primary_skill, difficulty, tags, rubric, ideal_answer, origin, status, source_hash, enrichment_provenance)
            VALUES (:id, :hash, :stem, :type, :skill, :difficulty, CAST(:tags AS jsonb), CAST(:rubric AS jsonb), :ideal, :origin, 'ACTIVE', :hash, '{"source":"seed","version":"2026-08-04.v1"}'::jsonb)
            ON CONFLICT (content_hash) DO NOTHING
            """).param("id", id).param("hash", hash).param("stem", stem).param("type", skill == null ? "BEHAVIORAL" : "TECHNICAL")
            .param("skill", skill).param("difficulty", difficulty).param("tags", "[\"" + (skill == null ? "behavioral" : skill.toLowerCase()) + "\"]")
            .param("rubric", criteria).param("ideal", ideal).param("origin", origin).update();
        jdbc.sql("""
            INSERT INTO question_embedding (question_id, embedding, source_hash)
            SELECT id, CAST(:embedding AS vector), content_hash FROM question WHERE content_hash = :hash
            ON CONFLICT (question_id) DO UPDATE SET embedding = EXCLUDED.embedding, source_hash = EXCLUDED.source_hash
            """).param("embedding", embedding).param("hash", hash).update();
    }

    private String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : bytes) result.append("%02x".formatted(item));
            return result.toString();
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }
}
