package com.automatedinterview.analysis;

import com.automatedinterview.catalog.SkillCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class VertexSkillAnalyzer {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String projectId;
    private final String location;
    private final String token;
    private final String model;

    public VertexSkillAnalyzer(
        @Value("${VERTEX_PROJECT_ID:}") String projectId,
        @Value("${VERTEX_LOCATION:us-central1}") String location,
        @Value("${VERTEX_ACCESS_TOKEN:}") String token,
        @Value("${VERTEX_CHAT_MODEL:gemini-2.5-flash-lite}") String model) {
        this.projectId = projectId;
        this.location = location;
        this.token = com.automatedinterview.ai.VertexCredentials.token(token);
        this.model = model;
    }

    public List<SkillClaim> analyze(String documentType, String document) {
        if (projectId.isBlank() || token.isBlank()) throw new SkillProviderException(true);
        if (document == null || document.isBlank()) return List.of();
        Map<String, SkillClaim> aggregated = new LinkedHashMap<>();
        for (String chunk : chunks(document)) {
            for (SkillClaim claim : analyzeChunk(documentType, chunk)) {
                SkillClaim prior = aggregated.get(claim.skillId());
                if (prior == null || importanceRank(claim.importance()) > importanceRank(prior.importance()))
                    aggregated.put(claim.skillId(), new SkillClaim(claim.skillId(), claim.importance(), clip(claim.evidence()), false));
            }
        }
        return new ArrayList<>(aggregated.values());
    }

    private List<SkillClaim> analyzeChunk(String documentType, String document) {
        try {
            String skills = SkillCatalog.SKILLS.stream()
                .map(skill -> skill.id() + "=" + String.join(", ", skill.aliases()))
                .reduce((left, right) -> left + "; " + right).orElse("");
            String prompt = """
                Analyze this synthetic %s for supported technical skills.
                Return only JSON: {\"skills\":[{\"skillId\":\"CORE_JAVA|SPRING_BOOT|SQL_RELATIONAL|ANGULAR\",\"importance\":\"REQUIRED|PREFERRED|OPTIONAL\",\"evidence\":\"exact quote\"}]}.
                Use only this catalog and exact evidence from the document. Do not invent evidence.
                Catalog: %s
                Document:\n%s
                """.formatted(documentType, skills, document);
            var requestNode = mapper.createObjectNode();
            requestNode.set("generationConfig", mapper.createObjectNode().put("temperature", 0));
            requestNode.set("contents", mapper.createArrayNode().add(mapper.createObjectNode()
                .put("role", "user")
                .set("parts", mapper.createArrayNode().add(mapper.createObjectNode().put("text", prompt)))));
            String requestJson = requestNode.toString();
            String endpoint = "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/%s:generateContent"
                .formatted(location, projectId, location, model);
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofSeconds(60))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw new SkillProviderException(true);
            JsonNode root = mapper.readTree(response.body());
            String text = root.path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("");
            JsonNode output = mapper.readTree(stripCodeFence(text));
            Set<String> seen = new HashSet<>();
            List<SkillClaim> claims = new ArrayList<>();
            for (JsonNode item : output.path("skills")) {
                String skillId = item.path("skillId").asText("");
                String importance = item.path("importance").asText("");
                String evidence = item.path("evidence").asText("");
                if (!isKnownSkill(skillId) || !Set.of("REQUIRED", "PREFERRED", "OPTIONAL").contains(importance)
                    || evidence.isBlank() || !document.contains(evidence) || !seen.add(skillId)) {
                    throw new SkillProviderException(true);
                }
                claims.add(new SkillClaim(skillId, importance, evidence, false));
            }
            return claims;
        } catch (SkillProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new SkillProviderException(true);
        }
    }

    private List<String> chunks(String document) {
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

    private int importanceRank(String value) { return switch (value) { case "REQUIRED" -> 3; case "PREFERRED" -> 2; default -> 1; }; }

    private String clip(String value) {
        if (value.codePointCount(0, value.length()) <= 300) return value;
        int end = value.offsetByCodePoints(0, 300);
        return value.substring(0, end);
    }

    private boolean isKnownSkill(String skillId) {
        return SkillCatalog.SKILLS.stream().anyMatch(skill -> skill.id().equals(skillId));
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
