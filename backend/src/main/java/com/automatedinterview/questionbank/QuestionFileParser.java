package com.automatedinterview.questionbank;

import com.automatedinterview.questionbank.QuestionImportService.ImportException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
final class QuestionFileParser {
    private static final int MAX_FILE_BYTES = 65_536;
    private final ObjectMapper json;

    QuestionFileParser(ObjectMapper json) {
        this.json = json;
    }

    List<ParsedQuestion> parseStrict(MultipartFile file, int maxQuestions) {
        String value = normalizeFile(file);
        if (isJson(file, value)) return parseStrictJson(value, maxQuestions);

        List<ParsedQuestion> questions = new ArrayList<>();
        List<QuestionImportService.ImportDiagnostic> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String[] lines = value.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            if (lines[index].strip().isBlank()) continue;
            String candidate = removeTextListPrefix(lines[index]);
            ImportException stemError = validateTextQuestionStem(candidate.strip());
            if (stemError != null) {
                errors.add(stemError.withContext(null, index + 1, stemError.field(), stemError.hint()).diagnostic());
                continue;
            }
            String stem;
            try {
                stem = normalizeStem(candidate);
            } catch (ImportException exception) {
                errors.add(exception.withContext(null, index + 1, exception.field(), exception.hint()).diagnostic());
                continue;
            }
            if (stem.isBlank()) continue;
            stemError = validateTextQuestionStem(stem);
            if (stemError != null) {
                errors.add(stemError.withContext(null, index + 1, stemError.field(), stemError.hint()).diagnostic());
                continue;
            }
            if (!seen.add(stem)) {
                errors.add(new ImportException("INVALID_QUESTION_FILE", 422, "Duplicate question stem.", null,
                    index + 1, "stem", "Remove the duplicate line or change its stem.").diagnostic());
                continue;
            }
            questions.add(new ParsedQuestion(stem, null, null, null, List.of()));
        }
        if (!errors.isEmpty()) throw ImportException.batch(errors);
        validateCount(questions.size(), maxQuestions);
        return List.copyOf(questions);
    }

    LenientBatch parseLenient(MultipartFile file, int maxQuestions) {
        String value = normalizeFile(file);
        if (isJson(file, value)) return parseLenientJson(value, maxQuestions);

        List<ParsedQuestion> questions = new ArrayList<>();
        List<QuestionImportService.ImportDiagnostic> errors = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String[] lines = value.split("\n", -1);
        for (int index = 0; index < lines.length; index++) {
            try {
                if (lines[index].strip().isBlank()) continue;
                String candidate = removeTextListPrefix(lines[index]);
                ImportException stemError = validateTextQuestionStem(candidate.strip());
                if (stemError != null) throw stemError;
                String stem = normalizeStem(candidate);
                if (stem.isBlank()) continue;
                stemError = validateTextQuestionStem(stem);
                if (stemError != null) throw stemError;
                if (!seen.add(stem)) {
                    throw new ImportException("INVALID_QUESTION_FILE", 422, "Duplicate question stem.", null,
                        index + 1, "stem", "Remove the duplicate line.");
                }
                questions.add(new ParsedQuestion(stem, null, null, null, List.of()));
            } catch (ImportException exception) {
                errors.add(exception.withContext(null, index + 1, exception.field(), exception.hint()).diagnostic());
            }
        }
        validateCount(questions.size(), maxQuestions);
        return new LenientBatch(List.copyOf(questions), List.copyOf(errors));
    }

    String normalizeStem(String value) {
        String normalized = Normalizer.normalize(value.strip().replaceAll("\\s+", " "), Normalizer.Form.NFC);
        if (normalized.indexOf('\0') >= 0 || normalized.chars().anyMatch(character -> Character.isISOControl(character) && character != '\t')) {
            throw new ImportException("INVALID_QUESTION_FILE", 400);
        }
        int length = normalized.codePointCount(0, normalized.length());
        if (!normalized.isBlank() && (length < 10 || length > 1000)) {
            throw new ImportException("INVALID_QUESTION_FILE", 400);
        }
        return normalized;
    }

    static ImportException validateTextQuestionStem(String stem) {
        if (stem == null || stem.codePointCount(0, stem.length()) < 20) {
            return new ImportException("INVALID_QUESTION_STEM", 422, "Question stem is too short.", null,
                null, "stem", "Provide a question with at least 20 characters.");
        }
        String trimmed = stem.strip();
        if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            return new ImportException("INVALID_QUESTION_STEM", 422, "Question stem looks like a placeholder.", null,
                null, "stem", "Replace the placeholder with a real interview question.");
        }
        return null;
    }

    static String removeTextListPrefix(String value) {
        return value.replaceFirst("^\\s*(?:[Qq]\\s*\\d+[:.)]|\\d+[.)]|[-*•])\\s*", "").strip();
    }

    private List<ParsedQuestion> parseStrictJson(String value, int maxQuestions) {
        try {
            JsonNode root = json.readTree(value);
            if (root == null || !root.isArray() || root.isEmpty() || root.size() > maxQuestions) {
                throw new ImportException("INVALID_QUESTION_FILE", 400);
            }
            List<ParsedQuestion> questions = new ArrayList<>();
            List<QuestionImportService.ImportDiagnostic> errors = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (int index = 0; index < root.size(); index++) {
                int item = index + 1;
                try {
                    JsonNode node = root.get(index);
                    if (!node.isObject()) throw invalid(item, null, "Each item must be an object.");
                    if (!node.hasNonNull("stem") || !node.get("stem").isTextual()) {
                        throw invalid(item, "stem", "Provide a non-empty text question stem.");
                    }
                    String stem = normalizeStem(node.get("stem").asText());
                    if (stem.isBlank()) {
                        throw new ImportException("INVALID_QUESTION_FILE", 422, "Stem must not be blank.", item,
                            null, "stem", "Provide a question with at least 10 characters.");
                    }
                    if (!seen.add(stem)) {
                        throw new ImportException("INVALID_QUESTION_FILE", 422, "Duplicate question stem.", item,
                            null, "stem", "Remove the duplicate item or change its stem.");
                    }
                    String type = optionalText(node, "type", item);
                    String skill = optionalText(node, "primarySkill", item);
                    String difficulty = optionalText(node, "difficulty", item);
                    List<String> secondary = optionalStringArray(node, "secondarySkills", item);
                    if (type != null && !Set.of("TECHNICAL", "BEHAVIORAL").contains(type)) {
                        throw invalid(item, "type", "Use TECHNICAL or BEHAVIORAL.");
                    }
                    if (difficulty != null && !Set.of("EASY", "MEDIUM", "HARD").contains(difficulty)) {
                        throw invalid(item, "difficulty", "Use EASY, MEDIUM, or HARD.");
                    }
                    if ("BEHAVIORAL".equals(type) && (skill != null || difficulty != null || !secondary.isEmpty())) {
                        String field = skill != null ? "primarySkill" : !secondary.isEmpty() ? "secondarySkills" : "difficulty";
                        throw new ImportException("QUESTION_FIELD_CONFLICT", 422,
                            "Behavioral questions cannot include skill or difficulty fields.", item, null, field,
                            "Remove the conflicting field.");
                    }
                    if (skill != null && secondary.contains(skill)) {
                        throw invalid(item, "secondarySkills", "Do not repeat the primary skill as a secondary skill.");
                    }
                    questions.add(new ParsedQuestion(stem, type, skill, difficulty, secondary));
                } catch (ImportException exception) {
                    errors.add(exception.withContext(item, exception.line(), exception.field(), exception.hint()).diagnostic());
                }
            }
            if (!errors.isEmpty()) throw ImportException.batch(errors);
            return List.copyOf(questions);
        } catch (ImportException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ImportException("INVALID_QUESTION_FILE", 400);
        }
    }

    private LenientBatch parseLenientJson(String value, int maxQuestions) {
        try {
            JsonNode root = json.readTree(value);
            if (root == null || !root.isArray() || root.isEmpty() || root.size() > maxQuestions) {
                throw new ImportException("INVALID_QUESTION_FILE", 400);
            }
            List<ParsedQuestion> questions = new ArrayList<>();
            List<QuestionImportService.ImportDiagnostic> errors = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (int index = 0; index < root.size(); index++) {
                int item = index + 1;
                try {
                    JsonNode node = root.get(index);
                    if (!node.isObject() || !node.hasNonNull("stem") || !node.get("stem").isTextual()) {
                        throw invalid(item, "stem", "Provide a text question stem.");
                    }
                    String stem = normalizeStem(node.get("stem").asText());
                    if (stem.isBlank() || !seen.add(stem)) {
                        throw invalid(item, "stem", "Provide a unique question stem.");
                    }
                    String type = optionalText(node, "type", item);
                    String skill = optionalText(node, "primarySkill", item);
                    String difficulty = optionalText(node, "difficulty", item);
                    List<String> secondary = optionalStringArray(node, "secondarySkills", item);
                    if (type != null && !Set.of("TECHNICAL", "BEHAVIORAL").contains(type)) {
                        throw invalid(item, "type", "Use TECHNICAL or BEHAVIORAL.");
                    }
                    if (difficulty != null && !Set.of("EASY", "MEDIUM", "HARD").contains(difficulty)) {
                        throw invalid(item, "difficulty", "Use EASY, MEDIUM, or HARD.");
                    }
                    if ("BEHAVIORAL".equals(type) && (skill != null || difficulty != null || !secondary.isEmpty())) {
                        throw invalid(item, "type", "Behavioral questions cannot have technical skill fields.");
                    }
                    questions.add(new ParsedQuestion(stem, type, skill, difficulty, secondary));
                } catch (ImportException exception) {
                    errors.add(exception.withContext(item, exception.line(), exception.field(), exception.hint()).diagnostic());
                }
            }
            return new LenientBatch(List.copyOf(questions), List.copyOf(errors));
        } catch (ImportException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ImportException("INVALID_QUESTION_FILE", 400);
        }
    }

    private String normalizeFile(MultipartFile file) {
        try {
            if (file == null || file.getSize() > MAX_FILE_BYTES) throw new ImportException("INVALID_QUESTION_FILE", 400);
            String value = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(file.getBytes())).toString();
            if (value.startsWith("\ufeff")) value = value.substring(1);
            return value.replace("\r\n", "\n").replace('\r', '\n');
        } catch (CharacterCodingException exception) {
            throw new ImportException("INVALID_QUESTION_FILE", 400);
        } catch (ImportException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ImportException("INVALID_QUESTION_FILE", 400);
        }
    }

    private boolean isJson(MultipartFile file, String value) {
        return (file.getOriginalFilename() != null && file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".json"))
            || value.stripLeading().startsWith("[");
    }

    private void validateCount(int count, int maxQuestions) {
        if (count == 0 || count > maxQuestions) throw new ImportException("INVALID_QUESTION_FILE", 400);
    }

    private String optionalText(JsonNode node, String field, int item) {
        if (!node.has(field) || node.get(field).isNull()) return null;
        if (!node.get(field).isTextual() || node.get(field).asText().isBlank()) {
            throw invalid(item, field, "Provide a non-empty text value or remove the field.");
        }
        return node.get(field).asText();
    }

    private List<String> optionalStringArray(JsonNode node, String field, int item) {
        if (!node.has(field) || node.get(field).isNull()) return List.of();
        if (!node.get(field).isArray()) throw invalid(item, field, "Provide an array of canonical skill IDs.");
        List<String> values = new ArrayList<>();
        for (JsonNode value : node.get(field)) {
            if (!value.isTextual() || value.asText().isBlank() || !values.add(value.asText())) {
                throw invalid(item, field, "Provide unique, non-empty skill IDs.");
            }
        }
        return List.copyOf(values);
    }

    private ImportException invalid(int item, String field, String hint) {
        return new ImportException("INVALID_QUESTION_FILE", 400, "Invalid question import data.", item, null, field, hint);
    }

    record ParsedQuestion(String stem, String type, String primarySkill, String difficulty, List<String> secondarySkills) { }
    record LenientBatch(List<ParsedQuestion> questions, List<QuestionImportService.ImportDiagnostic> errors) { }

}
