package com.automatedinterview.questionbank;

import com.automatedinterview.catalog.SkillCatalog;
import com.automatedinterview.ai.VertexEmbeddingService;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class QuestionImportService {
    private final JdbcClient jdbc;
    private final VertexQuestionEnricher enricher;
    private final VertexEmbeddingService embeddings;
    private final String enrichmentProfile;
    private final String embeddingProfile;

    public QuestionImportService(JdbcClient jdbc, VertexQuestionEnricher enricher, VertexEmbeddingService embeddings,
        @Value("${APP_QUESTION_ENRICHMENT_PROFILE:ai}") String enrichmentProfile,
        @Value("${APP_EMBEDDING_PROFILE:local}") String embeddingProfile) {
        this.jdbc = jdbc; this.enricher = enricher; this.embeddings = embeddings;
        this.enrichmentProfile = enrichmentProfile;
        this.embeddingProfile = embeddingProfile;
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
            String embedding;
            try { embedding = embeddingProfile.equals("ai") ? embeddings.embed(stem) : LocalEmbedding.vector(stem); }
            catch (VertexEmbeddingService.ProviderUnavailable exception) { throw new ImportException("EMBEDDING_UNAVAILABLE", 503); }
            jdbc.sql("""
                INSERT INTO question (id, content_hash, stem, type, primary_skill, difficulty, tags, rubric, ideal_answer, origin, status, source_hash, enrichment_provenance)
                VALUES (:id, :hash, :stem, :type, :skill, :difficulty, CAST(:tags AS jsonb), CAST(:rubric AS jsonb), :ideal, 'OWNER_IMPORT', 'ACTIVE', :hash, '{"source":"owner-import","profile":"ai"}'::jsonb)
                ON CONFLICT (content_hash) DO UPDATE SET stem = EXCLUDED.stem, type = EXCLUDED.type, primary_skill = EXCLUDED.primary_skill,
                    difficulty = EXCLUDED.difficulty, tags = EXCLUDED.tags, rubric = EXCLUDED.rubric, ideal_answer = EXCLUDED.ideal_answer,
                    origin = 'OWNER_IMPORT', status = 'ACTIVE', updated_at = now(), enrichment_provenance = EXCLUDED.enrichment_provenance
                """).param("id", id).param("hash", hash).param("stem", stem).param("type", classification.type()).param("skill", classification.skill())
                .param("difficulty", classification.difficulty()).param("tags", tags).param("rubric", rubric).param("ideal", ideal).update();
            jdbc.sql("""
                INSERT INTO question_embedding(question_id, embedding, source_hash)
                VALUES (:id, CAST(:embedding AS vector), :hash)
                ON CONFLICT (question_id) DO UPDATE SET embedding = EXCLUDED.embedding, source_hash = EXCLUDED.source_hash
                """).param("id", id).param("embedding", embedding).param("hash", hash).update();
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
    public static class ImportException extends RuntimeException { private final String code; private final int status; public ImportException(String code, int status) { this.code = code; this.status = status; } public String code() { return code; } public int status() { return status; } }
}
