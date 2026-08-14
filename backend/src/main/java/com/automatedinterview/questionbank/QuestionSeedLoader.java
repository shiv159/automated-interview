package com.automatedinterview.questionbank;

import com.automatedinterview.ai.VectorSyncService;
import com.automatedinterview.catalog.SkillCatalog;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
@Order(10)
@ConditionalOnProperty(name = "app.question-bank.seed-enabled", havingValue = "true")
public class QuestionSeedLoader implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(QuestionSeedLoader.class);

    private final JdbcClient jdbc;
    private final VectorSyncService vectorSync;

    public QuestionSeedLoader(JdbcClient jdbc, VectorSyncService vectorSync) {
        this.jdbc = jdbc;
        this.vectorSync = vectorSync;
    }

    @Override
    public void run(String... args) {
        List<String> difficulties = List.of("EASY", "MEDIUM", "HARD");
        for (SkillCatalog.Skill skill : SkillCatalog.SKILLS) {
            for (String difficulty : difficulties) {
                for (int index = 1; index <= 2; index++) {
                    String stem = "Explain a " + difficulty.toLowerCase() + " " + skill.displayName()
                            + " design problem and how you would solve it (seed " + index + ").";
                    UUID id = UUID.nameUUIDFromBytes((skill.id() + difficulty + index).getBytes(StandardCharsets.UTF_8));
                    upsert(id, stem, skill.id(), difficulty, "[\"CORRECTNESS\",\"DEPTH\",\"CLARITY\"]", "SEED", "TECHNICAL");
                }
            }
        }
        upsert(UUID.nameUUIDFromBytes("behavioral-1".getBytes(StandardCharsets.UTF_8)),
                "Tell me about a time you solved a difficult problem with a team.",
                null, null, "[\"situation\",\"action\",\"result\",\"reflection\"]", "SEED", "BEHAVIORAL");
        upsert(UUID.nameUUIDFromBytes("behavioral-2".getBytes(StandardCharsets.UTF_8)),
                "Describe a time you learned a new technology under pressure.",
                null, null, "[\"situation\",\"action\",\"result\",\"reflection\"]", "SEED", "BEHAVIORAL");
    }

    private void upsert(UUID id, String stem, String skill, String difficulty,
                        String criteria, String origin, String type) {
        String hash = hash(stem);
        String ideal = "A clear, structured answer covering the relevant "
                + (skill == null ? "situation, personal action, result, and reflection" : skill) + ".";

        // 1. Relational upsert (JDBC) — domain table is the source of truth.
        //    ON CONFLICT DO NOTHING preserves seed questions that the owner has customised.
        jdbc.sql("""
            INSERT INTO question (id, content_hash, stem, type, primary_skill, difficulty, tags, rubric,
                                  ideal_answer, origin, status, source_hash, enrichment_provenance)
            VALUES (:id, :hash, :stem, :type, :skill, :difficulty,
                    CAST(:tags AS jsonb), CAST(:rubric AS jsonb), :ideal, :origin,
                    'ACTIVE', :hash, '{"source":"seed","version":"2026-08-04.v1"}'::jsonb)
            ON CONFLICT (content_hash) DO NOTHING
            """)
            .param("id", id).param("hash", hash).param("stem", stem).param("type", type)
            .param("skill", skill).param("difficulty", difficulty)
            .param("tags", "[\"" + (skill == null ? "behavioral" : skill.toLowerCase()) + "\"]")
            .param("rubric", criteria).param("ideal", ideal).param("origin", origin)
            .update();

        // 2. Vector sync — runs after the domain row is committed.
        //    VectorSyncService.upsert() performs delete-then-add.
        //    On failure, VectorReconciliationJob will repair the gap on the next startup.
        UUID actualId = jdbc.sql("SELECT id FROM question WHERE content_hash = :hash")
                .param("hash", hash).query(UUID.class).single();
        try {
            vectorSync.upsert(actualId, stem, type,
                    skill == null ? "" : skill,
                    difficulty == null ? "" : difficulty,
                    "ACTIVE");
        } catch (Exception e) {
            log.error("QuestionSeedLoader: vector sync failed for question {}. " +
                    "VectorReconciliationJob will repair on next startup.", actualId, e);
        }
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
