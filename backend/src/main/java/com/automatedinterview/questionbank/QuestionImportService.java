package com.automatedinterview.questionbank;

import com.automatedinterview.catalog.SkillCatalog;
import com.automatedinterview.catalog.SkillCatalogService;
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
    private final SkillCatalogService catalog;
    private final String enrichmentProfile;

    public QuestionImportService(JdbcClient jdbc, VertexQuestionEnricher enricher, SkillCatalogService catalog,
        @Value("${APP_QUESTION_ENRICHMENT_PROFILE:ai}") String enrichmentProfile) {
        this.jdbc = jdbc; this.enricher = enricher; this.catalog = catalog;
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
        for (int itemIndex = 0; itemIndex < items.size(); itemIndex++) {
            ImportItem item = items.get(itemIndex);
            String stem = item.stem();
            Classification classification;
            try { classification = classify(item); }
            catch (ImportException exception) {
                throw exception.withContext(itemIndex + 1, exception.line(), exception.field(), exception.hint());
            }
            VertexQuestionEnricher.Enrichment enrichment;
            try { enrichment = enricher.enrich(stem, classification.type(), classification.skill()); }
            catch (VertexQuestionEnricher.ProviderUnavailable exception) { throw new ImportException("QUESTION_ENRICHMENT_UNAVAILABLE", 503); }
            String hash = hash(stem);
            boolean exists = jdbc.sql("SELECT count(*) FROM question WHERE content_hash = :hash").param("hash", hash).query(Long.class).single() > 0;
            if (!exists && bankSize + created >= 1000) throw new ImportException("QUESTION_BANK_LIMIT_REACHED", 409);
            UUID id = exists ? jdbc.sql("SELECT id FROM question WHERE content_hash = :hash").param("hash", hash).query(UUID.class).single() : UUID.randomUUID();
            String tags = json(enrichment.tags());
            List<String> enrichmentSecondary = enrichment.secondarySkills().isEmpty() ? item.secondarySkills() : enrichment.secondarySkills();
            try { SkillCatalog.validateSecondarySkills(catalog.activeSkills(), classification.skill(), enrichmentSecondary); }
            catch (IllegalArgumentException exception) { throw new ImportException("INVALID_SECONDARY_SKILLS", 422, exception.getMessage()); }
            String secondarySkills = json(enrichmentSecondary);
            String rubric = classification.type().equals("BEHAVIORAL") ? "[\"SITUATION\",\"ACTION\",\"RESULT\",\"REFLECTION\"]" : "[\"CORRECTNESS\",\"DEPTH\",\"CLARITY\"]";
            String ideal = enrichment.idealAnswer();
            // 1. Insert/update the relational domain record.
            jdbc.sql("""
                INSERT INTO question (id, content_hash, stem, type, primary_skill, secondary_skills, difficulty, tags, rubric, ideal_answer, origin, status, source_hash, enrichment_provenance)
                VALUES (:id, :hash, :stem, :type, :skill, CAST(:secondarySkills AS jsonb), :difficulty, CAST(:tags AS jsonb), CAST(:rubric AS jsonb), :ideal, 'OWNER_IMPORT', 'ACTIVE', :hash, '{"source":"owner-import","profile":"ai"}'::jsonb)
                ON CONFLICT (content_hash) DO UPDATE SET stem = EXCLUDED.stem, type = EXCLUDED.type, primary_skill = EXCLUDED.primary_skill,
                    secondary_skills = EXCLUDED.secondary_skills, difficulty = EXCLUDED.difficulty, tags = EXCLUDED.tags, rubric = EXCLUDED.rubric, ideal_answer = EXCLUDED.ideal_answer,
                    origin = 'OWNER_IMPORT', status = 'ACTIVE', updated_at = now(), enrichment_provenance = EXCLUDED.enrichment_provenance,
                    source_hash = EXCLUDED.source_hash, indexing_status = 'PENDING', indexing_attempts = 0,
                    indexing_next_attempt_at = now(), indexing_last_error = NULL, indexed_source_hash = NULL, indexed_at = NULL
                """).param("id", id).param("hash", hash).param("stem", stem).param("type", classification.type()).param("skill", classification.skill())
                .param("difficulty", classification.difficulty()).param("secondarySkills", secondarySkills).param("tags", tags).param("rubric", rubric).param("ideal", ideal).update();
            summaries.add(jdbc.sql("""
                SELECT id, stem, origin, status, type, primary_skill, secondary_skills, difficulty, tags, rubric, ideal_answer, updated_at
                FROM question WHERE id = :id
                """).param("id", id).query((rs, row) -> new QuestionBankController.QuestionSummary(
                    rs.getObject("id", UUID.class), rs.getString("stem"), rs.getString("origin"), rs.getString("status"),
                     rs.getString("type"), rs.getString("primary_skill"), rs.getString("secondary_skills"), rs.getString("difficulty"), rs.getString("tags"),
                     rs.getString("rubric"), rs.getString("ideal_answer"),
                    rs.getTimestamp("updated_at").toInstant())).single());
            if (exists) updated++; else created++;
        }
        return new ImportResponse(created, updated, 0, summaries, List.of());
    }

    public AnalysisResponse analyzeFile(MultipartFile file) {
        ParseBatch parsed = normalizeLenient(file);
        List<ImportItem> items = parsed.items();
        List<AnalysisQuestion> questions = new ArrayList<>();
        Set<String> known = catalog.activeSkills().stream().map(SkillCatalog.Skill::id).collect(java.util.stream.Collectors.toSet());
        java.util.Map<String, SkillSuggestion> suggestions = new java.util.LinkedHashMap<>();
        for (int index = 0; index < items.size(); index++) {
            ImportItem item = items.get(index);
            int questionIndex = index + 1;
            try {
                VertexQuestionEnricher.Enrichment value = enricher.discover(item.stem(), catalog.activeSkills());
                String primary = normalizeSkillId(value.skill());
                List<String> secondary = value.secondarySkills().stream().map(this::normalizeSkillId).filter(java.util.Objects::nonNull).distinct().filter(skill -> !skill.equals(primary)).toList();
                if (primary != null && !known.contains(primary)) suggestion(suggestions, primary, questionIndex);
                secondary.stream().filter(skill -> !known.contains(skill)).forEach(skill -> suggestion(suggestions, skill, questionIndex));
                questions.add(new AnalysisQuestion(item.stem(), value.type(), primary, secondary, value.difficulty(), value.tags(), value.idealAnswer(), "VALID", null));
            } catch (VertexQuestionEnricher.ProviderUnavailable exception) {
                questions.add(new AnalysisQuestion(item.stem(), null, null, List.of(), null, List.of(), null, "INVALID", "QUESTION_ANALYSIS_UNAVAILABLE"));
            }
        }
        return new AnalysisResponse(questions, List.copyOf(suggestions.values()), parsed.errors());
    }

    private ParseBatch normalizeLenient(MultipartFile file) {
        try {
            if (file == null || file.getSize() > 65536) throw new ImportException("INVALID_QUESTION_FILE", 400);
            String value;
            try { value = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(file.getBytes())).toString(); }
            catch (CharacterCodingException exception) { throw new ImportException("INVALID_QUESTION_FILE", 400); }
            if (value.startsWith("\ufeff")) value = value.substring(1);
            value = value.replace("\r\n", "\n").replace('\r', '\n');
            if ((file.getOriginalFilename() != null && file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".json")) || value.stripLeading().startsWith("[")) {
                JsonNode root = JSON.readTree(value);
                if (root == null || !root.isArray() || root.isEmpty() || root.size() > 10) throw new ImportException("INVALID_QUESTION_FILE", 400);
                List<ImportItem> items = new ArrayList<>(); List<ImportDiagnostic> errors = new ArrayList<>(); Set<String> seen = new HashSet<>();
                for (int index = 0; index < root.size(); index++) {
                    try {
                        JsonNode node = root.get(index);
                        if (!node.isObject() || !node.hasNonNull("stem") || !node.get("stem").isTextual()) throw invalid(index + 1, "stem", "Provide a text question stem.");
                        String stem = normalizeStem(node.get("stem").asText());
                        if (stem.isBlank() || !seen.add(stem)) throw invalid(index + 1, "stem", "Provide a unique question stem.");
                        String type = optionalText(node, "type", index + 1); String skill = optionalText(node, "primarySkill", index + 1); String difficulty = optionalText(node, "difficulty", index + 1);
                        List<String> secondary = optionalStringArray(node, "secondarySkills", index + 1);
                        if (type != null && !Set.of("TECHNICAL", "BEHAVIORAL").contains(type)) throw invalid(index + 1, "type", "Use TECHNICAL or BEHAVIORAL.");
                        if (difficulty != null && !Set.of("EASY", "MEDIUM", "HARD").contains(difficulty)) throw invalid(index + 1, "difficulty", "Use EASY, MEDIUM, or HARD.");
                        if ("BEHAVIORAL".equals(type) && (skill != null || difficulty != null || !secondary.isEmpty())) throw invalid(index + 1, "type", "Behavioral questions cannot have technical skill fields.");
                        items.add(new ImportItem(stem, type, skill, difficulty, secondary));
                    } catch (ImportException exception) { errors.add(exception.withContext(index + 1, exception.line(), exception.field(), exception.hint()).diagnostic()); }
                }
                return new ParseBatch(items, errors);
            }
            List<ImportItem> items = new ArrayList<>(); List<ImportDiagnostic> errors = new ArrayList<>(); Set<String> seen = new HashSet<>();
            String[] lines = value.split("\n", -1);
            for (int index = 0; index < lines.length; index++) {
                try {
                    String stem = normalizeStem(lines[index]);
                    if (stem.isBlank()) continue;
                    if (!seen.add(stem)) throw new ImportException("INVALID_QUESTION_FILE", 422, "Duplicate question stem.", null, index + 1, "stem", "Remove the duplicate line.");
                    items.add(new ImportItem(stem, null, null, null, List.of()));
                } catch (ImportException exception) { errors.add(exception.withContext(null, index + 1, exception.field(), exception.hint()).diagnostic()); }
            }
            if (items.isEmpty() || items.size() > 10) throw new ImportException("INVALID_QUESTION_FILE", 400);
            return new ParseBatch(items, errors);
        } catch (ImportException exception) { throw exception; }
        catch (Exception exception) { throw new ImportException("INVALID_QUESTION_FILE", 400); }
    }

    public ImportResponse importDraft(DraftImportRequest request) {
        if (request == null || request.questions() == null || request.questions().size() > 10) throw new ImportException("INVALID_QUESTION_DRAFT", 400);
        List<ApprovedSkill> approvedSkills = request.approvedSkills() == null ? List.of() : request.approvedSkills();
        Set<String> approvedIds = approvedSkills.stream().map(ApprovedSkill::id).collect(java.util.stream.Collectors.toSet());
        int created = 0, updated = 0, skipped = 0;
        List<QuestionBankController.QuestionSummary> summaries = new ArrayList<>();
        List<ImportDiagnostic> errors = new ArrayList<>();
        Set<String> seenStems = new HashSet<>();
        for (int index = 0; index < request.questions().size(); index++) {
            DraftQuestion item = request.questions().get(index);
            try {
                if (item == null || item.stem() == null || item.secondarySkills() == null || item.tags() == null) {
                    throw new IllegalArgumentException("Question fields are incomplete");
                }
                String stem = normalizeStem(item.stem());
                if (stem.isBlank() || !seenStems.add(stem)) throw new IllegalArgumentException("Question stem must be unique and between 10 and 1000 characters");
                validateDraftFields(item);
                if (!"VALID".equals(item.status())) throw new IllegalArgumentException("Question was not valid during analysis");
                if ("BEHAVIORAL".equals(item.type())) {
                    if (item.primarySkill() != null || !item.secondarySkills().isEmpty() || item.difficulty() != null) throw new IllegalArgumentException("Behavioral question has technical skill fields");
                } else if (!"TECHNICAL".equals(item.type()) || item.primarySkill() == null || item.difficulty() == null
                        || (!catalog.isKnown(item.primarySkill()) && !approvedIds.contains(item.primarySkill()))) {
                    throw new IllegalArgumentException("Technical question requires an approved primary skill and difficulty");
                } else {
                    Set<String> knownIds = new HashSet<>(catalog.activeSkills().stream().map(SkillCatalog.Skill::id).toList());
                    knownIds.addAll(approvedIds);
                    if (item.secondarySkills().stream().anyMatch(skill -> !knownIds.contains(skill))) throw new IllegalArgumentException("Secondary skill is not approved");
                    Set<String> unique = new java.util.HashSet<>(item.secondarySkills());
                    if (unique.size() != item.secondarySkills().size() || unique.contains(item.primarySkill())) throw new IllegalArgumentException("Invalid secondary skill assignment");
                }
                for (ApprovedSkill skill : approvedSkills) {
                    if (skill.id().equals(item.primarySkill()) || item.secondarySkills().contains(skill.id())) {
                        try { catalog.approve(skill.id(), skill.displayName(), skill.aliases(), skill.version()); }
                        catch (IllegalArgumentException exception) { throw new ImportException("INVALID_SKILL_APPROVAL", 422, exception.getMessage()); }
                    }
                }
                boolean exists = jdbc.sql("SELECT count(*) FROM question WHERE content_hash = :hash").param("hash", hash(stem)).query(Long.class).single() > 0;
                UUID id = exists ? jdbc.sql("SELECT id FROM question WHERE content_hash = :hash").param("hash", hash(stem)).query(UUID.class).single() : UUID.randomUUID();
                jdbc.sql("""
                    INSERT INTO question (id, content_hash, stem, type, primary_skill, secondary_skills, difficulty, tags, rubric, ideal_answer, origin, status, source_hash, enrichment_provenance)
                    VALUES (:id, :hash, :stem, :type, :primary, CAST(:secondary AS jsonb), :difficulty, CAST(:tags AS jsonb), CAST(:rubric AS jsonb), :ideal, 'OWNER_IMPORT', 'ACTIVE', :hash, '{"source":"owner-import","profile":"ai-draft"}'::jsonb)
                    ON CONFLICT (content_hash) DO UPDATE SET type = EXCLUDED.type, primary_skill = EXCLUDED.primary_skill,
                      secondary_skills = EXCLUDED.secondary_skills, difficulty = EXCLUDED.difficulty, tags = EXCLUDED.tags,
                      rubric = EXCLUDED.rubric, ideal_answer = EXCLUDED.ideal_answer, status = 'ACTIVE', updated_at = now(),
                      source_hash = EXCLUDED.source_hash, indexing_status = 'PENDING', indexing_attempts = 0,
                      indexing_next_attempt_at = now(), indexing_last_error = NULL, indexed_source_hash = NULL, indexed_at = NULL
                    """).param("id", id).param("hash", hash(stem)).param("stem", stem).param("type", item.type())
                    .param("primary", item.primarySkill()).param("secondary", json(item.secondarySkills())).param("difficulty", item.difficulty())
                    .param("tags", json(item.tags())).param("rubric", json("BEHAVIORAL".equals(item.type()) ? List.of("SITUATION", "ACTION", "RESULT", "REFLECTION") : List.of("CORRECTNESS", "DEPTH", "CLARITY")))
                    .param("ideal", item.idealAnswer()).update();
                summaries.add(loadSummary(id));
                if (exists) updated++; else created++;
            } catch (Exception exception) {
                skipped++;
                errors.add(new ImportDiagnostic("INVALID_QUESTION", 422, index + 1, null, null, exception.getMessage(), "Correct the row and upload it again."));
            }
        }
        return new ImportResponse(created, updated, skipped, summaries, errors);
    }

    private void validateDraftFields(DraftQuestion item) {
        if (!Set.of("TECHNICAL", "BEHAVIORAL").contains(item.type())) throw new IllegalArgumentException("Type must be TECHNICAL or BEHAVIORAL");
        if (item.tags().isEmpty() || item.tags().size() > 5 || item.tags().stream().anyMatch(tag -> tag == null || tag.isBlank())
                || item.tags().stream().distinct().count() != item.tags().size()) throw new IllegalArgumentException("Tags must contain 1 to 5 unique non-empty values");
        if (item.idealAnswer() == null || item.idealAnswer().codePointCount(0, item.idealAnswer().length()) < 50
                || item.idealAnswer().codePointCount(0, item.idealAnswer().length()) > 2000)
            throw new IllegalArgumentException("Ideal answer must contain 50 to 2000 characters");
        if (item.difficulty() != null && !Set.of("EASY", "MEDIUM", "HARD").contains(item.difficulty()))
            throw new IllegalArgumentException("Difficulty must be EASY, MEDIUM, or HARD");
    }

    private void suggestion(java.util.Map<String, SkillSuggestion> suggestions, String id, int questionIndex) {
        String normalized = normalizeSkillId(id);
        if (normalized == null || normalized.isBlank()) return;
        suggestions.compute(normalized, (key, old) -> old == null
            ? new SkillSuggestion(key, key.replace('_', ' '), List.of(key.toLowerCase(Locale.ROOT).replace('_', ' ')), List.of(questionIndex))
            : new SkillSuggestion(old.id(), old.displayName(), old.aliases(), java.util.stream.Stream.concat(old.questionIndexes().stream(), java.util.stream.Stream.of(questionIndex)).distinct().toList()));
    }

    private String normalizeSkillId(String id) {
        if (id == null) return null;
        String normalized = id.strip().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("_+", "_");
        return normalized.isBlank() ? null : normalized;
    }

    private QuestionBankController.QuestionSummary loadSummary(UUID id) {
        return jdbc.sql("SELECT id, stem, origin, status, type, primary_skill, secondary_skills, difficulty, tags, rubric, ideal_answer, updated_at FROM question WHERE id = :id")
            .param("id", id).query((rs, row) -> new QuestionBankController.QuestionSummary(rs.getObject("id", UUID.class), rs.getString("stem"), rs.getString("origin"), rs.getString("status"), rs.getString("type"), rs.getString("primary_skill"), rs.getString("secondary_skills"), rs.getString("difficulty"), rs.getString("tags"), rs.getString("rubric"), rs.getString("ideal_answer"), rs.getTimestamp("updated_at").toInstant())).single();
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
            List<ImportDiagnostic> errors = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            String[] rawLines = value.split("\n", -1);
            for (int lineIndex = 0; lineIndex < rawLines.length; lineIndex++) {
                String normalized;
                try { normalized = normalizeStem(rawLines[lineIndex]); }
                catch (ImportException exception) { errors.add(exception.withContext(null, lineIndex + 1, exception.field(), exception.hint()).diagnostic()); continue; }
                if (normalized.isBlank()) continue;
                if (!seen.add(normalized)) { errors.add(new ImportException("INVALID_QUESTION_FILE", 422, "Duplicate question stem.", null, lineIndex + 1, "stem", "Remove the duplicate line or change its stem.").diagnostic()); continue; }
                lines.add(new ImportItem(normalized, null, null, null, List.of()));
            }
            if (!errors.isEmpty()) throw ImportException.batch(errors);
            if (lines.isEmpty() || lines.size() > 10) throw new ImportException("INVALID_QUESTION_FILE", 400);
            return lines;
        } catch (ImportException exception) { throw exception; }
        catch (Exception exception) { throw new ImportException("INVALID_QUESTION_FILE", 400); }
    }

    private List<ImportItem> normalizeJson(String value) {
        try {
            JsonNode root = JSON.readTree(value);
            if (root == null || !root.isArray() || root.isEmpty() || root.size() > 10) throw new ImportException("INVALID_QUESTION_FILE", 400);
            List<ImportItem> items = new ArrayList<>(); List<ImportDiagnostic> errors = new ArrayList<>(); Set<String> seen = new HashSet<>();
            for (int itemIndex = 0; itemIndex < root.size(); itemIndex++) {
                JsonNode node = root.get(itemIndex);
                int itemNumber = itemIndex + 1;
                try {
                    if (!node.isObject()) throw invalid(itemNumber, null, "Each item must be an object.");
                    if (!node.hasNonNull("stem") || !node.get("stem").isTextual()) throw invalid(itemNumber, "stem", "Provide a non-empty text question stem.");
                    String stem = normalizeStem(node.get("stem").asText());
                    if (stem.isBlank()) throw new ImportException("INVALID_QUESTION_FILE", 422, "Stem must not be blank.", itemNumber, null, "stem", "Provide a question with at least 10 characters.");
                    if (!seen.add(stem)) throw new ImportException("INVALID_QUESTION_FILE", 422, "Duplicate question stem.", itemNumber, null, "stem", "Remove the duplicate item or change its stem.");
                    String type = optionalText(node, "type", itemNumber); String skill = optionalText(node, "primarySkill", itemNumber); String difficulty = optionalText(node, "difficulty", itemNumber);
                    List<String> secondary = optionalStringArray(node, "secondarySkills", itemNumber);
                    if (type != null && !Set.of("TECHNICAL", "BEHAVIORAL").contains(type)) throw invalid(itemNumber, "type", "Use TECHNICAL or BEHAVIORAL.");
                    if (difficulty != null && !Set.of("EASY", "MEDIUM", "HARD").contains(difficulty)) throw invalid(itemNumber, "difficulty", "Use EASY, MEDIUM, or HARD.");
                    if ("BEHAVIORAL".equals(type) && (skill != null || difficulty != null || !secondary.isEmpty())) throw new ImportException("QUESTION_FIELD_CONFLICT", 422, "Behavioral questions cannot include skill or difficulty fields.", itemNumber, null, skill != null ? "primarySkill" : !secondary.isEmpty() ? "secondarySkills" : "difficulty", "Remove the conflicting field.");
                    if (skill != null && secondary.contains(skill)) throw invalid(itemNumber, "secondarySkills", "Do not repeat the primary skill as a secondary skill.");
                    items.add(new ImportItem(stem, type, skill, difficulty, secondary));
                } catch (ImportException exception) {
                    errors.add(exception.withContext(itemNumber, exception.line(), exception.field(), exception.hint()).diagnostic());
                }
            }
            if (!errors.isEmpty()) throw ImportException.batch(errors);
            return items;
        } catch (ImportException exception) { throw exception; }
        catch (Exception exception) { throw new ImportException("INVALID_QUESTION_FILE", 400); }
    }

    private String optionalText(JsonNode node, String field, int itemNumber) {
        if (!node.has(field) || node.get(field).isNull()) return null;
        if (!node.get(field).isTextual() || node.get(field).asText().isBlank()) throw invalid(itemNumber, field, "Provide a non-empty text value or remove the field.");
        return node.get(field).asText();
    }

    private ImportException invalid(int item, String field, String hint) {
        return new ImportException("INVALID_QUESTION_FILE", 400, "Invalid question import data.", item, null, field, hint);
    }

    private String normalizeStem(String value) {
        String normalized = Normalizer.normalize(value.strip().replaceAll("\\s+", " "), Normalizer.Form.NFC);
        if (normalized.indexOf('\0') >= 0 || normalized.chars().anyMatch(character -> Character.isISOControl(character) && character != '\t')) throw new ImportException("INVALID_QUESTION_FILE", 400);
        if (!normalized.isBlank() && (normalized.codePointCount(0, normalized.length()) < 10 || normalized.codePointCount(0, normalized.length()) > 1000)) throw new ImportException("INVALID_QUESTION_FILE", 400);
        return normalized;
    }

    private List<String> optionalStringArray(JsonNode node, String field, int itemNumber) {
        if (!node.has(field) || node.get(field).isNull()) return List.of();
        if (!node.get(field).isArray()) throw invalid(itemNumber, field, "Provide an array of canonical skill IDs.");
        List<String> values = new ArrayList<>();
        for (JsonNode value : node.get(field)) {
            if (!value.isTextual() || value.asText().isBlank() || !values.add(value.asText()))
                throw invalid(itemNumber, field, "Provide unique, non-empty skill IDs.");
        }
        return List.copyOf(values);
    }

    private String json(Object value) {
        try { return JSON.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("Unable to serialize question metadata", exception); }
    }

    private Classification classify(ImportItem item) {
        String stem = item.stem();
        if (item.type() != null) {
            if (item.type().equals("BEHAVIORAL")) return new Classification("BEHAVIORAL", null, null);
            List<String> matches = catalog.matchingSkillIds(stem.toLowerCase(Locale.ROOT));
            String skill = item.primarySkill() != null ? item.primarySkill() : matches.size() == 1 ? matches.get(0) : null;
            if (skill == null) throw new ImportException("QUESTION_SKILL_AMBIGUOUS", 422, "Question skill could not be determined.", null, null, "primarySkill", "Set primarySkill to one canonical supported skill ID.");
            if (!catalog.isKnown(skill)) throw new ImportException("UNKNOWN_SKILL", 422, "Primary skill is not approved.", null, null, "primarySkill", "Approve the detected skill before importing.");
            try { SkillCatalog.validateSecondarySkills(catalog.activeSkills(), skill, item.secondarySkills()); }
            catch (IllegalArgumentException exception) { throw new ImportException("INVALID_SECONDARY_SKILLS", 422, exception.getMessage(), null, null, "secondarySkills", "Use unique canonical supported skill IDs other than primarySkill."); }
            return new Classification("TECHNICAL", skill, item.difficulty() == null ? detectDifficulty(stem) : item.difficulty());
        }
        return classifyLegacy(stem);
    }

    private Classification classifyLegacy(String stem) {
        String lower = stem.toLowerCase(Locale.ROOT);
        if (List.of("tell me about a time", "describe a time", "give an example of when", "how did you handle").stream().anyMatch(lower::startsWith))
            return new Classification("BEHAVIORAL", null, null);
        List<String> matches = catalog.matchingSkillIds(lower);
        if (matches.size() != 1) throw new ImportException("QUESTION_SKILL_AMBIGUOUS", 422, "Question skill could not be determined.", null, null, "primarySkill", "Set primarySkill to one canonical supported skill ID or make the stem skill-specific.");
        return new Classification("TECHNICAL", matches.get(0), detectDifficulty(stem));
    }

    private String detectDifficulty(String stem) { String lower = stem.toLowerCase(Locale.ROOT); return lower.contains("hard") ? "HARD" : lower.contains("easy") ? "EASY" : "MEDIUM"; }

    private String hash(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(); for (byte item : bytes) result.append("%02x".formatted(item)); return result.toString();
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

    private record ImportItem(String stem, String type, String primarySkill, String difficulty, List<String> secondarySkills) { }
    private record Classification(String type, String skill, String difficulty) { }
    private record ParseBatch(List<ImportItem> items, List<ImportDiagnostic> errors) { }
    public record AnalysisResponse(List<AnalysisQuestion> questions, List<SkillSuggestion> newSkills, List<ImportDiagnostic> errors) { }
    public record AnalysisQuestion(String stem, String type, String primarySkill, List<String> secondarySkills, String difficulty, List<String> tags, String idealAnswer, String status, String errorCode) { }
    public record SkillSuggestion(String id, String displayName, List<String> aliases, List<Integer> questionIndexes) { }
    public record ApprovedSkill(String id, String displayName, List<String> aliases, String version) { }
    public record DraftQuestion(String stem, String type, String primarySkill, List<String> secondarySkills, String difficulty, List<String> tags, String idealAnswer, String status) { }
    public record DraftImportRequest(List<DraftQuestion> questions, List<ApprovedSkill> approvedSkills) { }
    public record ImportResponse(int createdCount, int updatedCount, int skippedCount, List<QuestionBankController.QuestionSummary> questions, List<ImportDiagnostic> errors) {
        public ImportResponse(int createdCount, int updatedCount, List<QuestionBankController.QuestionSummary> questions) { this(createdCount, updatedCount, 0, questions, List.of()); }
    }
    public static class ImportException extends RuntimeException {
        private final String code; private final int status; private final Integer item; private final Integer line; private final String field; private final String hint;
        public ImportException(String code, int status) { this(code, status, code, null, null, null, null); }
        public ImportException(String code, int status, String message) { this(code, status, message, null, null, null, null); }
        public ImportException(String code, int status, String message, Integer item, Integer line, String field, String hint) { super(message); this.code = code; this.status = status; this.item = item; this.line = line; this.field = field; this.hint = hint; this.diagnostics = List.of(new ImportDiagnostic(code, status, item, line, field, message, hint)); }
        public ImportException(String code, int status, Throwable cause) { super(cause); this.code = code; this.status = status; this.item = null; this.line = null; this.field = null; this.hint = null; this.diagnostics = List.of(new ImportDiagnostic(code, status, null, null, null, cause.getMessage(), null)); }
        public ImportException withContext(Integer item, Integer line, String field, String hint) { return new ImportException(code, status, getMessage(), item, line, field, hint); }
        public ImportDiagnostic diagnostic() { return new ImportDiagnostic(code, status, item, line, field, getMessage(), hint); }
        public static ImportException batch(List<ImportDiagnostic> errors) { ImportDiagnostic first = errors.get(0); return new ImportException(first.code(), first.status(), "The import contains " + errors.size() + " invalid item(s).", first.item(), first.line(), first.field(), first.hint(), errors); }
        public String code() { return code; } public int status() { return status; } public Integer item() { return item; } public Integer line() { return line; } public String field() { return field; } public String hint() { return hint; }
        private final List<ImportDiagnostic> diagnostics;
        private ImportException(String code, int status, String message, Integer item, Integer line, String field, String hint, List<ImportDiagnostic> diagnostics) { super(message); this.code = code; this.status = status; this.item = item; this.line = line; this.field = field; this.hint = hint; this.diagnostics = List.copyOf(diagnostics); }
        public List<ImportDiagnostic> errors() { return diagnostics; }
    }
    public record ImportDiagnostic(String code, int status, Integer item, Integer line, String field, String message, String hint) { }
}
