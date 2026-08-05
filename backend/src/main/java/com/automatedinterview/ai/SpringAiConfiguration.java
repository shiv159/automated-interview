package com.automatedinterview.ai;

import com.google.genai.Client;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;
import org.springframework.ai.google.genai.embedding.GoogleGenAiEmbeddingConnectionDetails;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingModel;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Configuration
@ConditionalOnProperty(name = "APP_SPRING_AI_ENABLED", havingValue = "true")
@ConditionalOnProperty(name = "APP_SPRING_AI_CREDENTIALS_AVAILABLE", havingValue = "true", matchIfMissing = true)
public class SpringAiConfiguration {
    @Bean
    Client googleGenAiClient(@Value("${VERTEX_PROJECT_ID:}") String projectId,
                             @Value("${VERTEX_LOCATION:us-central1}") String location) {
        return Client.builder().project(projectId).location(location).vertexAI(true).build();
    }

    @Bean
    GoogleGenAiChatModel springAiChatModel(Client client,
                                            @Value("${VERTEX_CHAT_MODEL:gemini-2.5-flash-lite}") String model) {
        return GoogleGenAiChatModel.builder().genAiClient(client)
            .options(GoogleGenAiChatOptions.builder().model(model).temperature(0.0).responseMimeType("application/json").build())
            .build();
    }

    @Bean
    GoogleGenAiTextEmbeddingModel springAiEmbeddingModel(Client client,
                                                         @Value("${VERTEX_EMBEDDING_MODEL:text-embedding-005}") String model,
                                                         @Value("${VERTEX_EMBEDDING_DIMENSIONS:768}") int dimensions) {
        var details = GoogleGenAiEmbeddingConnectionDetails.builder().genAiClient(client).build();
        var options = GoogleGenAiTextEmbeddingOptions.builder().model(model).dimensions(dimensions)
            .taskType(GoogleGenAiTextEmbeddingOptions.TaskType.RETRIEVAL_DOCUMENT).build();
        return new GoogleGenAiTextEmbeddingModel(details, options);
    }
}
