package com.automatedinterview.questionbank;

import com.automatedinterview.catalog.SkillCatalog;
import com.automatedinterview.ai.VertexQuestionEnricher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private static final ObjectMapper JSON = new ObjectMapper();
    private final JdbcClient jdbc;
    private final VertexQuestionEnricher enricher;
    private final String enrichmentProfile;

    public QuestionImportService(JdbcClient jdbc, VertexQuestionEnricher enricher,
        @Value("${APP_QUESTION_ENRICHMENT_PROFILE:ai}") String enrichmentProfile) {
        this.jdbc = jdbc; this.enricher = enricher;
        this.enrichmentProfile = enrichmentProfile;
    }

    @Transactional
    public ImportResponse importFile(MultipartFile file) {
        List<ImportItem> items = normalize(file);
        if (!enrichmentProfile.equals("ai")) throw new ImportException("QUESTION_ENRICHMENT_UNAVAILABLE", 503);
        int created = 0;
        int updated = 0;
        int bankSize = jdbc.sql("SELECT count(*) FROM question").query(Integer.class).single();
        List<QuestionBankController.QuestionSummary> summaries = new ArrayList<>();
        for (ImportItem item : items) {
            String stem = item.stem();
            Classification classification = classify(item);
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
                    origin = 'OWNER_IMPORT', status = 'ACTIVE', updated_at = now(), enrichment_provenance = EXCLUDED.enrichment_provenance,
                    source_hash = EXCLUDED.source_hash, indexing_status = 'PENDING', indexing_attempts = 0,
                    indexing_next_attempt_at = now(), indexing_last_error = NULL, indexed_source_hash = NULL, indexed_at = NULL
                """).param("id", id).param("hash", hash).param("stem", stem).param("type", classification.type()).param("skill", classification.skill())
                .param("difficulty", classification.difficulty()).param("tags", tags).param("rubric", rubric).param("ideal", ideal).update();
            summaries.add(jdbc.sql("""
                SELECT id, stem, origin, status, type, primary_skill, difficulty, tags, rubric, ideal_answer, updated_at
                FROM question WHERE id = :id
                """).param("id", id).query((rs, row) -> new QuestionBankController.QuestionSummary(
                    rs.getObject("id", UUID.class), rs.getString("stem"), rs.getString("origin"), rs.getString("status"),
                     rs.getString("type"), rs.getString("primary_skill"), rs.getString("difficulty"), rs.getString("tags"),
                     rs.getString("rubric"), rs.getString("ideal_answer"),
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
        // Remove inactive vectors immediately; reactivation is queued for the worker.
        if (status.equals("INACTIVE")) {
            jdbc.sql("UPDATE question SET indexing_status = 'FAILED', indexing_next_attempt_at = NULL, indexing_last_error = 'Question deactivated' WHERE id = :id")
                .param("id", id).update();
            jdbc.sql("DELETE FROM vector_store WHERE id = :id").param("id", id).update();
        } else {
            jdbc.sql("UPDATE question SET indexing_status = 'PENDING', indexing_next_attempt_at = now(), indexing_last_error = NULL WHERE id = :id")
                .param("id", id).update();
        }
    }

    private List<ImportItem> normalize(MultipartFile file) {
        try {
            if (file == null || file.getSize() > 65536) throw new ImportException("INVALID_QUESTION_FILE", 400);
            String value;
            try { value = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(file.getBytes())).toString(); }
            catch (CharacterCodingException exception) { throw new ImportException("INVALID_QUESTION_FILE", 400); }
            if (value.startsWith("\ufeff")) value = value.substring(1);
            value = value.replace("\r\n", "\n").replace('\r', '\n');
            if ((file.getOriginalFilename() != null && file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".json")) || value.stripLeading().startsWith("["))
                return normalizeJson(value);
            List<ImportItem> lines = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (String line : value.split("\n", -1)) {
                String normalized = normalizeStem(line);
                if (normalized.isBlank()) continue;
                if (!seen.add(normalized)) throw new ImportException("INVALID_QUESTION_FILE", 400);
                lines.add(new ImportItem(normalized, null, null, null));
            }
            if (lines.isEmpty() || lines.size() > 10) throw new ImportException("INVALID_QUESTION_FILE", 400);
            return lines;
        } catch (ImportException exception) { throw exception; }
        catch (Exception exception) { throw new ImportException("INVALID_QUESTION_FILE", 400); }
    }

    private List<ImportItem> normalizeJson(String value) {
        try {
            JsonNode root = JSON.readTree(value);
            if (root == null || !root.isArray() || root.isEmpty() || root.size() > 10) throw new ImportException("INVALID_QUESTION_FILE", 400);
            List<ImportItem> items = new ArrayList<>(); Set<String> seen = new HashSet<>();
            for (JsonNode node : root) {
                if (!node.isObject() || !node.hasNonNull("stem") || !node.get("stem").isTextual()) throw new ImportException("INVALID_QUESTION_FILE", 400);
                String stem = normalizeStem(node.get("stem").asText());
                if (stem.isBlank() || !seen.add(stem)) throw new ImportException("INVALID_QUESTION_FILE", 400);
                String type = optionalText(node, "type"); String skill = optionalText(node, "primarySkill"); String difficulty = optionalText(node, "difficulty");
                if (type != null && !Set.of("TECHNICAL", "BEHAVIORAL").contains(type)) throw new ImportException("INVALID_QUESTION_FILE", 400);
                if (difficulty != null && !Set.of("EASY", "MEDIUM", "HARD").contains(difficulty)) throw new ImportException("INVALID_QUESTION_FILE", 400);
                if (skill != null && SkillCatalog.SKILLS.stream().noneMatch(item -> item.id().equals(skill))) throw new ImportException("INVALID_QUESTION_FILE", 400);
                if ("BEHAVIORAL".equals(type) && (skill != null || difficulty != null)) throw new ImportException("QUESTION_FIELD_CONFLICT", 422);
                items.add(new ImportItem(stem, type, skill, difficulty));
            }
            return items;
        } catch (ImportException exception) { throw exception; }
        catch (Exception exception) { throw new ImportException("INVALID_QUESTION_FILE", 400); }
    }

    private String optionalText(JsonNode node, String field) {
        if (!node.has(field) || node.get(field).isNull()) return null;
        if (!node.get(field).isTextual() || node.get(field).asText().isBlank()) throw new ImportException("INVALID_QUESTION_FILE", 400);
        return node.get(field).asText();
    }

    private String normalizeStem(String value) {
        String normalized = Normalizer.normalize(value.strip().replaceAll("\\s+", " "), Normalizer.Form.NFC);
        if (normalized.indexOf('\0') >= 0 || normalized.chars().anyMatch(character -> Character.isISOControl(character) && character != '\t')) throw new ImportException("INVALID_QUESTION_FILE", 400);
        if (!normalized.isBlank() && (normalized.codePointCount(0, normalized.length()) < 10 || normalized.codePointCount(0, normalized.length()) > 1000)) throw new ImportException("INVALID_QUESTION_FILE", 400);
        return normalized;
    }

    private Classification classify(ImportItem item) {
        String stem = item.stem();
        if (item.type() != null) {
            if (item.type().equals("BEHAVIORAL")) return new Classification("BEHAVIORAL", null, null);
            List<String> matches = SkillCatalog.matchingSkillIds(stem.toLowerCase(Locale.ROOT));
            String skill = item.primarySkill() != null ? item.primarySkill() : matches.size() == 1 ? matches.get(0) : null;
            if (skill == null) throw new ImportException("QUESTION_SKILL_AMBIGUOUS", 422);
            return new Classification("TECHNICAL", skill, item.difficulty() == null ? detectDifficulty(stem) : item.difficulty());
        }
        return classifyLegacy(stem);
    }

    private Classification classifyLegacy(String stem) {
        String lower = stem.toLowerCase(Locale.ROOT);
        if (List.of("tell me about a time", "describe a time", "give an example of when", "how did you handle").stream().anyMatch(lower::startsWith))
            return new Classification("BEHAVIORAL", null, null);
        List<String> matches = SkillCatalog.matchingSkillIds(lower);
        if (matches.size() != 1) throw new ImportException("QUESTION_SKILL_AMBIGUOUS", 422);
        return new Classification("TECHNICAL", matches.get(0), detectDifficulty(stem));
    }

    private String detectDifficulty(String stem) { String lower = stem.toLowerCase(Locale.ROOT); return lower.contains("hard") ? "HARD" : lower.contains("easy") ? "EASY" : "MEDIUM"; }

    private String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(); for (byte item : bytes) result.append("%02x".formatted(item)); return result.toString();
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private record ImportItem(String stem, String type, String primarySkill, String difficulty) { }
    private record Classification(String type, String skill, String difficulty) { }
    public record ImportResponse(int createdCount, int updatedCount, List<QuestionBankController.QuestionSummary> questions) { }
    public static class ImportException extends RuntimeException { private final String code; private final int status; public ImportException(String code, int status) { this(code, status, null); } public ImportException(String code, int status, Throwable cause) { super(cause); this.code = code; this.status = status; } public String code() { return code; } public int status() { return status; } }
}
