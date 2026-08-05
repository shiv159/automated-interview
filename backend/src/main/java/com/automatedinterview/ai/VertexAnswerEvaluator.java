package com.automatedinterview.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class VertexAnswerEvaluator {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String projectId, location, model;
    private final VertexAccessTokenProvider credentials;

    public VertexAnswerEvaluator(@Value("${VERTEX_PROJECT_ID:}") String projectId, @Value("${VERTEX_LOCATION:us-central1}") String location,
        @Value("${VERTEX_CHAT_MODEL:gemini-2.5-flash-lite}") String model,
        VertexAccessTokenProvider credentials) {
        this.projectId = projectId; this.location = location; this.model = model; this.credentials = credentials;
    }

    public Result evaluate(String stem, String criteria, String idealAnswer, String answer) {
        if (projectId.isBlank() || !credentials.isAvailable()) throw new ProviderUnavailable();
        try {
            String token = credentials.token();
            String prompt = "Evaluate the candidate answer against the question and ideal answer. Return only JSON: {\"score\": number 0..10, \"strengths\": [1..3 strings], \"improvements\": [1..3 strings]}. Question: " + stem + " Criteria: " + criteria + " Ideal answer: " + idealAnswer + " Candidate answer: " + answer;
            var bodyNode = mapper.createObjectNode();
            bodyNode.set("generationConfig", mapper.createObjectNode().put("temperature", 0));
            bodyNode.set("contents", mapper.createArrayNode().add(mapper.createObjectNode().put("role", "user").set("parts", mapper.createArrayNode().add(mapper.createObjectNode().put("text", prompt)))));
            String body = bodyNode.toString();
            String endpoint = "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/%s:generateContent".formatted(location, projectId, location, model);
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(60)).expectContinue(false).header("Authorization", "Bearer " + token).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw new ProviderUnavailable();
            String text = mapper.readTree(response.body()).path("candidates").path(0).path("content").path("parts").path(0).path("text").asText("").strip();
            if (text.startsWith("```") && text.endsWith("```")) text = text.substring(text.indexOf('\n') + 1, text.length() - 3).strip();
            JsonNode value = mapper.readTree(text); double score = value.path("score").asDouble(-1);
            if (score < 0 || score > 10 || !value.path("strengths").isArray() || !value.path("improvements").isArray()) throw new ProviderUnavailable();
            List<String> strengths = new ArrayList<>(); value.path("strengths").forEach(item -> strengths.add(item.asText()));
            List<String> improvements = new ArrayList<>(); value.path("improvements").forEach(item -> improvements.add(item.asText()));
            if (strengths.isEmpty() || strengths.size() > 3 || improvements.isEmpty() || improvements.size() > 3) throw new ProviderUnavailable();
            return new Result(Math.round(score * 10) / 10.0, strengths, improvements);
        } catch (ProviderUnavailable exception) { throw exception; }
        catch (Exception exception) { throw new ProviderUnavailable(); }
    }

    public String model() { return model; }

    public record Result(double score, List<String> strengths, List<String> improvements) { }
    public static class ProviderUnavailable extends RuntimeException { }
}
