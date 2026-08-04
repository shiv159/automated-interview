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
public class VertexEmbeddingService {
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final String projectId, location, token, model;
    private final int dimensions;

    public VertexEmbeddingService(@Value("${VERTEX_PROJECT_ID:}") String projectId, @Value("${VERTEX_LOCATION:us-central1}") String location,
        @Value("${VERTEX_ACCESS_TOKEN:}") String token, @Value("${VERTEX_EMBEDDING_MODEL:text-embedding-005}") String model,
        @Value("${VERTEX_EMBEDDING_DIMENSIONS:768}") int dimensions) {
        this.projectId = projectId; this.location = location; this.token = VertexCredentials.token(token); this.model = model; this.dimensions = dimensions;
    }

    public String embed(String text) {
        if (projectId.isBlank() || token.isBlank()) throw new ProviderUnavailable();
        try {
            String body = mapper.createObjectNode().set("instances", mapper.createArrayNode().add(mapper.createObjectNode().put("content", text))).toString();
            String endpoint = "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/%s:predict".formatted(location, projectId, location, model);
            HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(endpoint)).timeout(Duration.ofSeconds(60)).header("Authorization", "Bearer " + token).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) throw new ProviderUnavailable();
            JsonNode values = mapper.readTree(response.body()).path("predictions").path(0).path("embeddings").path("values");
            if (!values.isArray() || values.size() != dimensions) throw new ProviderUnavailable();
            List<String> output = new ArrayList<>(); values.forEach(item -> output.add(item.asText()));
            return "[" + String.join(",", output) + "]";
        } catch (ProviderUnavailable exception) { throw exception; }
        catch (Exception exception) { throw new ProviderUnavailable(); }
    }

    public static class ProviderUnavailable extends RuntimeException { }
}
