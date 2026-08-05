package com.automatedinterview.analysis;

import com.automatedinterview.catalog.SkillCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.text.Normalizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class VertexSkillAnalyzer {
    private static final Logger log = LoggerFactory.getLogger(VertexSkillAnalyzer.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String projectId;
    private final String location;
    private final String model;
    private final com.automatedinterview.ai.VertexAccessTokenProvider credentials;

    public VertexSkillAnalyzer(
        @Value("${VERTEX_PROJECT_ID:}") String projectId,
        @Value("${VERTEX_LOCATION:us-central1}") String location,
        @Value("${VERTEX_CHAT_MODEL:gemini-2.5-flash-lite}") String model,
        com.automatedinterview.ai.VertexAccessTokenProvider credentials) {
        this.projectId = projectId;
        this.location = location;
        this.model = model;
        this.credentials = credentials;
    }

    public List<SkillClaim> analyze(String documentType, String document) {
        if (projectId.isBlank() || !credentials.isAvailable()) throw new SkillProviderException(true);
        if (document == null || document.isBlank()) return List.of();
        List<List<SkillClaim>> perChunkClaims = new ArrayList<>();
        for (String chunk : chunks(document)) {
            perChunkClaims.add(analyzeChunk(documentType, chunk));
        }
        return aggregateClaims(perChunkClaims).stream()
            .map(claim -> new SkillClaim(claim.skillId(), claim.importance(), clip(claim.evidence()), false)).toList();
    }

    private List<SkillClaim> analyzeChunk(String documentType, String document) {
        try {
            String token = credentials.token();
            String skills = SkillCatalog.SKILLS.stream()
                .map(skill -> skill.id() + "=" + String.join(", ", skill.aliases()))
                .reduce((left, right) -> left + "; " + right).orElse("");
            String prompt = """
                Analyze this synthetic %s for supported technical skills.
                Return only JSON: {\"status\":\"ACCEPT|UNCERTAIN\",\"skills\":[{\"skillId\":\"CORE_JAVA|SPRING_BOOT|SQL_RELATIONAL|ANGULAR\",\"importance\":\"REQUIRED|PREFERRED\",\"evidence\":\"exact quote\"}]}.
                Use only this catalog and exact evidence from the document. Do not invent evidence.
                Catalog: %s
                Document:\n%s
                """.formatted(documentType, skills, document);
            var requestNode = mapper.createObjectNode();
            ObjectNode generationConfig = mapper.createObjectNode().put("temperature", 0).put("responseMimeType", "application/json");
            generationConfig.set("responseSchema", responseSchema());
            requestNode.set("generationConfig", generationConfig);
            requestNode.set("contents", mapper.createArrayNode().add(mapper.createObjectNode()
                .put("role", "user")
                .set("parts", mapper.createArrayNode().add(mapper.createObjectNode().put("text", prompt)))));
            String requestJson = requestNode.toString();
            String endpoint = "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/%s:generateContent"
                .formatted(location, projectId, location, model);
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(60))
                .expectContinue(false)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("Vertex skill analysis failed: status={}", response.statusCode());
                throw new SkillProviderException(true);
            }
            JsonNode root = mapper.readTree(response.body());
            String text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
            JsonNode output = mapper.readTree(stripCodeFence(text));
            return validatedClaims(output, document);
        } catch (SkillProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("Vertex skill analysis response validation failed: category={}", exception.getClass().getSimpleName());
            throw new SkillProviderException(true);
        }
    }

    static List<SkillClaim> validatedClaims(JsonNode output, String document) {
        if (output == null || !output.isObject() || !output.path("skills").isArray()) throw new IllegalArgumentException("invalid_skills");
        String status = output.path("status").asText("ACCEPT");
        if ("UNCERTAIN".equals(status)) throw new SkillProviderException(false);
        if (!"ACCEPT".equals(status)) throw new IllegalArgumentException("invalid_status");
        Set<String> seen = new HashSet<>();
        List<SkillClaim> claims = new ArrayList<>();
        for (JsonNode item : output.path("skills")) {
            String skillId = item.path("skillId").asText("");
            String importance = item.path("importance").asText("");
            String evidence = item.path("evidence").asText("");
            if (!isKnownSkillStatic(skillId) || !Set.of("REQUIRED", "PREFERRED").contains(importance)
                || evidence.isBlank() || evidence.codePointCount(0, evidence.length()) < 3 || !seen.add(skillId))
                throw new IllegalArgumentException("invalid_skill_claim");
            String normalizedDocument = typographicNormalize(document);
            String normalizedEvidence = typographicNormalize(Normalizer.normalize(evidence, Normalizer.Form.NFC));
            int start = normalizedDocument.indexOf(normalizedEvidence);
            if (start < 0 || normalizedEvidence.indexOf('\n') >= 0 || normalizedEvidence.indexOf('\r') >= 0) throw new IllegalArgumentException("evidence_not_found");
            int end = start + normalizedEvidence.length();
            if (normalizedDocument.substring(0, start).lastIndexOf('\n') >= 0 && normalizedDocument.substring(start, end).contains("\n"))
                throw new IllegalArgumentException("evidence_crosses_line");
            if (normalizedDocument.substring(start, end).contains("\n")) throw new IllegalArgumentException("evidence_crosses_line");
            claims.add(new SkillClaim(skillId, importance, document.substring(start, end), false));
        }
        return claims;
    }

    private ObjectNode responseSchema() {
        ObjectNode schema = mapper.createObjectNode().put("type", "OBJECT");
        schema.set("required", strings("status", "skills"));
        ObjectNode properties = schema.putObject("properties");
        properties.set("status", mapper.createObjectNode().put("type", "STRING").set("enum", strings("ACCEPT", "UNCERTAIN")));
        ObjectNode skills = mapper.createObjectNode().put("type", "ARRAY");
        ObjectNode item = skills.putObject("items").put("type", "OBJECT");
        item.set("required", strings("skillId", "importance", "evidence"));
        ObjectNode itemProperties = item.putObject("properties");
        itemProperties.set("skillId", mapper.createObjectNode().put("type", "STRING").set("enum", strings("CORE_JAVA", "SPRING_BOOT", "SQL_RELATIONAL", "ANGULAR")));
        itemProperties.set("importance", mapper.createObjectNode().put("type", "STRING").set("enum", strings("REQUIRED", "PREFERRED")));
        itemProperties.set("evidence", mapper.createObjectNode().put("type", "STRING"));
        properties.set("skills", skills);
        return schema;
    }

    private ArrayNode strings(String... values) {
        ArrayNode array = mapper.createArrayNode();
        for (String value : values) array.add(value);
        return array;
    }

    static List<SkillClaim> aggregateClaims(List<List<SkillClaim>> perChunkClaims) {
        Map<String, SkillClaim> aggregated = new LinkedHashMap<>();
        for (List<SkillClaim> claims : perChunkClaims) {
            for (SkillClaim claim : claims) {
                SkillClaim prior = aggregated.get(claim.skillId());
                if (prior == null)
                    aggregated.put(claim.skillId(), claim);
            }
        }
        return new ArrayList<>(aggregated.values());
    }

    static String clipEvidence(String line, int matchStart, int matchEnd) {
        int length = line.codePointCount(0, line.length());
        if (length <= 300) return line;
        int matchLength = matchEnd - matchStart;
        int start = Math.max(0, matchStart - (300 - matchLength) / 2);
        int end = Math.min(length, start + 300);
        start = Math.max(0, end - 300);
        int startIndex = line.offsetByCodePoints(0, start);
        int endIndex = line.offsetByCodePoints(0, end);
        return line.substring(startIndex, endIndex);
    }

    static List<String> chunks(String document) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : document.split("\\n", -1)) {
            int candidate = current.isEmpty() ? line.codePointCount(0, line.length()) : current.codePointCount(0, current.length()) + 1 + line.codePointCount(0, line.length());
            if (!current.isEmpty() && candidate > 4000) { result.add(current.toString()); current.setLength(0); }
            if (line.codePointCount(0, line.length()) > 4000) {
                if (!current.isEmpty()) { result.add(current.toString()); current.setLength(0); }
                result.add(line);
                continue;
            }
            if (!current.isEmpty()) current.append('\n');
            current.append(line);
        }
        if (!current.isEmpty()) result.add(current.toString());
        return result;
    }

    private int importanceRank(String value) { return importanceRankStatic(value); }

    private static int importanceRankStatic(String value) { return switch (value) { case "REQUIRED" -> 3; case "PREFERRED" -> 2; default -> 0; }; }

    private String clip(String value) {
        if (value.codePointCount(0, value.length()) <= 300) return value;
        int end = value.offsetByCodePoints(0, 300);
        return value.substring(0, end);
    }

    private boolean isKnownSkill(String skillId) {
        return isKnownSkillStatic(skillId);
    }

    private static boolean isKnownSkillStatic(String skillId) {
        return SkillCatalog.SKILLS.stream().anyMatch(skill -> skill.id().equals(skillId));
    }

    private static String typographicNormalize(String value) {
        return value.replace('\u2018', '\'').replace('\u2019', '\'')
            .replace('\u201c', '"').replace('\u201d', '"')
            .replace('\u2013', '-').replace('\u2014', '-');
    }

    private String stripCodeFence(String text) {
        String value = text.strip();
        if (value.startsWith("```") && value.endsWith("```")) {
            int firstNewline = value.indexOf('\n');
            return value.substring(firstNewline + 1, value.length() - 3).strip();
        }
        return value;
    }

    public static class SkillProviderException extends RuntimeException {
        private final boolean providerFailure;

        public SkillProviderException(boolean providerFailure) {
            this.providerFailure = providerFailure;
        }

        public boolean providerFailure() { return providerFailure; }
    }
}
