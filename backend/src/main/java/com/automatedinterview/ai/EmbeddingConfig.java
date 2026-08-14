package com.automatedinterview.ai;

import com.automatedinterview.questionbank.LocalEmbedding;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResultMetadata;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Configures the {@link EmbeddingModel} used by {@link org.springframework.ai.vectorstore.VectorStore}.
 *
 * <ul>
 *   <li><b>ai profile</b>: relies on the auto-configured {@code VertexAiEmbeddingModel}
 *       from {@code spring-ai-starter-model-google-genai-embedding}. No extra bean is defined
 *       here — Spring AI auto-configuration wins. This avoids bean-ambiguity issues.</li>
 *   <li><b>local profile</b>: exposes a {@code @Primary} {@link EmbeddingModel} bean that
 *       delegates to {@link LocalEmbedding#vector(String)} so that local development and
 *       tests work without any external API keys or network calls.</li>
 * </ul>
 *
 * The {@code local} bean is marked {@code @Primary} so it takes precedence over any
 * partially-configured auto-configured model that may still be present on the classpath.
 */
@Configuration
public class EmbeddingConfig {

    /**
     * Explicitly creates the PgVector store because the project uses the
     * library artifact rather than Spring AI's vector-store starter. Flyway
     * owns the schema, so PgVectorStore schema initialization stays disabled.
     */
    @Bean
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate, EmbeddingModel embeddingModel) {
        return PgVectorStore.builder(jdbcTemplate, embeddingModel)
                .dimensions(LocalEmbedding.DIMENSIONS)
                .vectorTableName("vector_store")
                .initializeSchema(false)
                .build();
    }

    /**
     * Local (development/test) {@link EmbeddingModel} backed by {@link LocalEmbedding}.
     * Active only when {@code app.embedding.profile=local} (the default).
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "app.embedding.profile", havingValue = "local", matchIfMissing = true)
    public EmbeddingModel localEmbeddingModel() {
        return new LocalEmbeddingModel();
    }

    /**
     * Thin adapter that makes {@link LocalEmbedding} satisfy the {@link EmbeddingModel} contract
     * expected by Spring AI's {@link org.springframework.ai.vectorstore.VectorStore}.
     */
    static class LocalEmbeddingModel implements EmbeddingModel {

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<Embedding> embeddings = new ArrayList<>();
            int index = 0;
            for (String text : request.getInstructions()) {
                float[] floats = toFloats(LocalEmbedding.vector(text));
                embeddings.add(new Embedding(floats, index++, EmbeddingResultMetadata.EMPTY));
            }
            return new EmbeddingResponse(embeddings);
        }

        @Override
        public float[] embed(Document document) {
            return toFloats(LocalEmbedding.vector(document.getText()));
        }

        @Override
        public int dimensions() {
            return LocalEmbedding.DIMENSIONS;
        }

        /** Parse the "[f0,f1,...]" string produced by {@link LocalEmbedding#vector}. */
        private static float[] toFloats(String vector) {
            String[] parts = vector.substring(1, vector.length() - 1).split(",");
            float[] result = new float[parts.length];
            for (int i = 0; i < parts.length; i++) result[i] = Float.parseFloat(parts[i].trim());
            return result;
        }
    }
}
