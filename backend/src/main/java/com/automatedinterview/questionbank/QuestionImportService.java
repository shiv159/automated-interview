package com.automatedinterview.questionbank;

import com.automatedinterview.catalog.SkillCatalog;
import com.automatedinterview.ai.VectorSyncService;
import com.automatedinterview.ai.VertexQuestionEnricher;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.security.MessageDigest;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class QuestionImportService {
    private static final Logger log = LoggerFactory.getLogger(QuestionImportService.class);
    private final JdbcClient jdbc;
    private final VertexQuestionEnricher enricher;
    private final VectorSyncService vectorSync;
    private final String enrichmentProfile;

    public QuestionImportService(JdbcClient jdbc, VertexQuestionEnricher enricher, VectorSyncService vectorSync,
        @Value("${APP_QUESTION_ENRICHMENT_PROFILE:ai}") String enrichmentProfile) {
        this.jdbc = jdbc; this.enricher = enricher; this.vectorSync = vectorSync;
        this.enrichmentProfile = enrichmentProfile;
    }

    @Transactional
    public ImportResponse importFile(MultipartFile file) {
        List<String> stems = normalize(file);
        if (!enrichmentProfile.equals("ai")) throw new ImportException("QUESTION_ENRICHMENT_UNAVAILABLE", 503);
        int created = 0;
        int updated = 0;
        int bankSize = jdbc.sql("SELECT count(*) FROM question").query(Integer.class).single();
        List<QuestionBankController.QuestionSummary> summaries = new ArrayList<>();
        for (String stem : stems) {
            Classification classification = classify(stem);
            VertexQuestionEnricher.Enrichment enrichment;
            try { enrichment = enricher.enrich(stem, classification.type(), classification.skill()); }
            catch (VertexQuestionEnricher.ProviderUnavailable exception) { throw new ImportException("QUESTION_ENRICHMENT_UNAVAILABLE", 503); }
            String hash = hash(stem);
            boolean exists = jdbc.sql("SELECT count(*) FROM question WHERE content_hash = :hash").param("hash", hash).query(Long.class).single() > 0;
            if (!exists && bankSize + created >= 1000) throw new ImportException("QUESTION_BANK_LIMIT_REACHED", 409);
            UUID id = exists ? jdbc.sql("SELECT id FROM question WHERE content_hash = :hash").param("hash", hash).query(UUID.class).single() : UUID.randomUUID();
            String tags = "[" + enrichment.tags().stream().map(tag -> "\"" + tag.replace("\"", "\\\"") + "\"").reduce((a,b) -> a + "," + b).orElse("") + "]";
            String rubric = classification.type().equals("BEHAVIORAL") ? "[\"SITUATION\",\"ACTION\",\"RESULT\",\"REFLECTION\"]" : "[\"CORRECTNESS\",\"DEPTH\",\"CLARITY\"]";
            String ideal = enrichment.idealAnswer();
            // 1. Insert/update the relational domain record.
            jdbc.sql("""
                INSERT INTO question (id, content_hash, stem, type, primary_skill, difficulty, tags, rubric, ideal_answer, origin, status, source_hash, enrichment_provenance)
                VALUES (:id, :hash, :stem, :type, :skill, :difficulty, CAST(:tags AS jsonb), CAST(:rubric AS jsonb), :ideal, 'OWNER_IMPORT', 'ACTIVE', :hash, '{"source":"owner-import","profile":"ai"}'::jsonb)
                ON CONFLICT (content_hash) DO UPDATE SET stem = EXCLUDED.stem, type = EXCLUDED.type, primary_skill = EXCLUDED.primary_skill,
                    difficulty = EXCLUDED.difficulty, tags = EXCLUDED.tags, rubric = EXCLUDED.rubric, ideal_answer = EXCLUDED.ideal_answer,
                    origin = 'OWNER_IMPORT', status = 'ACTIVE', updated_at = now(), enrichment_provenance = EXCLUDED.enrichment_provenance
                """).param("id", id).param("hash", hash).param("stem", stem).param("type", classification.type()).param("skill", classification.skill())
                .param("difficulty", classification.difficulty()).param("tags", tags).param("rubric", rubric).param("ideal", ideal).update();
            // 2. Sync the vector projection.
            try {
                vectorSync.upsert(id, stem, classification.type(),
                        classification.skill() == null ? "" : classification.skill(),
                        classification.difficulty() == null ? "" : classification.difficulty(),
                        "ACTIVE");
            } catch (VectorSyncService.VectorSyncException e) {
                throw new ImportException("VECTOR_SYNC_UNAVAILABLE", 503, e);
            }
            summaries.add(jdbc.sql("""
                SELECT id, stem, origin, status, type, primary_skill, difficulty, tags, updated_at
                FROM question WHERE id = :id
                """).param("id", id).query((rs, row) -> new QuestionBankController.QuestionSummary(
                    rs.getObject("id", UUID.class), rs.getString("stem"), rs.getString("origin"), rs.getString("status"),
                    rs.getString("type"), rs.getString("primary_skill"), rs.getString("difficulty"), rs.getString("tags"),
                    rs.getTimestamp("updated_at").toInstant())).single());
            if (exists) updated++; else created++;
        }
        return new ImportResponse(created, updated, summaries);
    }

    public void deactivate(UUID id, String status) {
        if (!Set.of("ACTIVE", "INACTIVE").contains(status)) throw new ImportException("INVALID_STATUS", 400);
        int changed = jdbc.sql("UPDATE question SET status = :status, updated_at = now() WHERE id = :id AND origin = 'OWNER_IMPORT'")
            .param("status", status).param("id", id).update();
        if (changed == 0) throw new ImportException("SEED_QUESTION_IMMUTABLE", 409);
        // Sync the vector projection: delete on deactivation, re-add on reactivation.
        if (status.equals("INACTIVE")) {
            vectorSync.delete(id);
        } else {
            // Re-fetch the domain row to rebuild the vector with current content.
            jdbc.sql("SELECT id, stem, type, COALESCE(primary_skill,'') AS primary_skill, COALESCE(difficulty,'') AS difficulty, status FROM question WHERE id = :id")
                .param("id", id).query((rs, row) -> {
                    try {
                        vectorSync.upsert(
                            rs.getObject("id", UUID.class), rs.getString("stem"), rs.getString("type"),
                            rs.getString("primary_skill"), rs.getString("difficulty"), rs.getString("status"));
                    } catch (VectorSyncService.VectorSyncException e) {
                        throw new ImportException("VECTOR_SYNC_UNAVAILABLE", 503, e);
                    }
                    return null;
                }).list();
        }
    }

    private List<String> normalize(MultipartFile file) {
        try {
            if (file == null || file.getSize() > 65536) throw new ImportException("INVALID_QUESTION_FILE", 400);
            String value;
            try { value = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(file.getBytes())).toString(); }
            catch (CharacterCodingException exception) { throw new ImportException("INVALID_QUESTION_FILE", 400); }
            if (value.startsWith("\ufeff")) value = value.substring(1);
            value = value.replace("\r\n", "\n").replace('\r', '\n');
            List<String> lines = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (String line : value.split("\n", -1)) {
                String normalized = Normalizer.normalize(line.strip().replaceAll("\\s+", " "), Normalizer.Form.NFC);
                if (normalized.isBlank()) continue;
                if (normalized.indexOf('\0') >= 0 || normalized.chars().anyMatch(character -> Character.isISOControl(character) && character != '\t'))
                    throw new ImportException("INVALID_QUESTION_FILE", 400);
                if (normalized.codePointCount(0, normalized.length()) < 10 || normalized.codePointCount(0, normalized.length()) > 1000 || !seen.add(normalized))
                    throw new ImportException("INVALID_QUESTION_FILE", 400);
                lines.add(normalized);
            }
            if (lines.isEmpty() || lines.size() > 10) throw new ImportException("INVALID_QUESTION_FILE", 400);
            return lines;
        } catch (ImportException exception) { throw exception; }
        catch (Exception exception) { throw new ImportException("INVALID_QUESTION_FILE", 400); }
    }

    private Classification classify(String stem) {
        String lower = stem.toLowerCase(Locale.ROOT);
        if (List.of("tell me about a time", "describe a time", "give an example of when", "how did you handle").stream().anyMatch(lower::startsWith))
            return new Classification("BEHAVIORAL", null, null);
        List<String> matches = SkillCatalog.matchingSkillIds(lower);
        if (matches.size() != 1) throw new ImportException("QUESTION_SKILL_AMBIGUOUS", 422);
        String difficulty = lower.contains("hard") ? "HARD" : lower.contains("easy") ? "EASY" : "MEDIUM";
        return new Classification("TECHNICAL", matches.get(0), difficulty);
    }

    private String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(); for (byte item : bytes) result.append("%02x".formatted(item)); return result.toString();
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private record Classification(String type, String skill, String difficulty) { }
    public record ImportResponse(int createdCount, int updatedCount, List<QuestionBankController.QuestionSummary> questions) { }
    public static class ImportException extends RuntimeException { private final String code; private final int status; public ImportException(String code, int status) { this(code, status, null); } public ImportException(String code, int status, Throwable cause) { super(cause); this.code = code; this.status = status; } public String code() { return code; } public int status() { return status; } }
}
