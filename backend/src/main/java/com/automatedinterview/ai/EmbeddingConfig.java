package com.automatedinterview.ai;

import com.automatedinterview.questionbank.LocalEmbedding;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResultMetadata;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;
import org.springframework.beans.factory.annotation.Value;
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
 *   <li><b>ai profile</b>: wraps the auto-configured {@code EmbeddingModel}
 *       from {@code spring-ai-starter-model-google-genai-embedding} with asymmetric task-type
 *       support ({@code RETRIEVAL_DOCUMENT} for indexing, {@code RETRIEVAL_QUERY} for search).</li>
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
    public VectorStore vectorStore(JdbcTemplate jdbcTemplate,
                                   EmbeddingModel embeddingModel,
                                   @Value("${APP_EMBEDDING_PROFILE:local}") String profile,
                                   @Value("${VERTEX_EMBEDDING_DIMENSIONS:768}") int dimensions) {
        EmbeddingModel effectiveModel = "ai".equalsIgnoreCase(profile)
                ? new TaskAwareEmbeddingModel(embeddingModel, dimensions)
                : embeddingModel;

        return PgVectorStore.builder(jdbcTemplate, effectiveModel)
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
     * Adapter that applies asymmetric task types for Vertex AI / Google GenAI embeddings:
     * <ul>
     *   <li>{@code RETRIEVAL_DOCUMENT} for indexing documents into the vector store.</li>
     *   <li>{@code RETRIEVAL_QUERY} for query-time similarity searches.</li>
     * </ul>
     */
    static class TaskAwareEmbeddingModel implements EmbeddingModel {
        private final EmbeddingModel delegate;
        private final int dimensions;

        TaskAwareEmbeddingModel(EmbeddingModel delegate, int dimensions) {
            this.delegate = delegate;
            this.dimensions = dimensions;
        }

        @Override
        public float[] embed(Document document) {
            try {
                var options = GoogleGenAiTextEmbeddingOptions.builder()
                        .taskType(GoogleGenAiTextEmbeddingOptions.TaskType.RETRIEVAL_DOCUMENT)
                        .dimensions(dimensions)
                        .build();
                return delegate.call(new EmbeddingRequest(List.of(document.getText()), options)).getResult().getOutput();
            } catch (Exception e) {
                return delegate.embed(document);
            }
        }

        @Override
        public float[] embed(String text) {
            try {
                var options = GoogleGenAiTextEmbeddingOptions.builder()
                        .taskType(GoogleGenAiTextEmbeddingOptions.TaskType.RETRIEVAL_QUERY)
                        .dimensions(dimensions)
                        .build();
                return delegate.call(new EmbeddingRequest(List.of(text), options)).getResult().getOutput();
            } catch (Exception e) {
                return delegate.embed(text);
            }
        }

        @Override
        public List<float[]> embed(List<String> texts) {
            return delegate.embed(texts);
        }

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            return delegate.call(request);
        }

        @Override
        public int dimensions() {
            return delegate.dimensions();
        }
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
