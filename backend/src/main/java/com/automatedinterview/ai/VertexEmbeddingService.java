package com.automatedinterview.ai;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.google.genai.text.GoogleGenAiTextEmbeddingOptions;
import java.util.List;
import java.util.ArrayList;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class VertexEmbeddingService {
    private final EmbeddingModel model;
    private final int dimensions;
    private final int batchSize;

    public VertexEmbeddingService(@Value("${VERTEX_EMBEDDING_DIMENSIONS:768}") int dimensions,
                                  @Value("${APP_AI_EMBEDDING_BATCH_SIZE:32}") int batchSize,
                                  ObjectProvider<EmbeddingModel> model) {
        this.model = model.getIfAvailable();
        this.dimensions = dimensions;
        this.batchSize = batchSize;
    }

    public String embed(String text) {
        return embed(text, false);
    }

    public String embedQuery(String text) {
        return embed(text, true);
    }

    public List<String> embedDocuments(List<String> texts) {
        if (model == null) throw new ProviderUnavailable();
        List<String> result = new ArrayList<>();
        try {
            for (int start = 0; start < texts.size(); start += batchSize) {
                List<String> batch = texts.subList(start, Math.min(start + batchSize, texts.size()));
                for (float[] values : model.embed(batch)) {
                    if (values.length != dimensions) throw new ProviderUnavailable();
                    result.add(format(values));
                }
            }
            return result;
        } catch (ProviderUnavailable exception) { throw exception; }
        catch (Exception exception) { throw new ProviderUnavailable(); }
    }

    private String embed(String text, boolean query) {
        if (model == null) throw new ProviderUnavailable();
        try {
            float[] values;
            if (!query) {
                values = model.embed(text);
            } else {
                var options = GoogleGenAiTextEmbeddingOptions.builder()
                    .taskType(GoogleGenAiTextEmbeddingOptions.TaskType.RETRIEVAL_QUERY)
                    .dimensions(dimensions)
                    .build();
                values = model.call(new EmbeddingRequest(List.of(text), options)).getResult().getOutput();
            }
            if (values.length != dimensions) throw new ProviderUnavailable();
            return format(values);
        } catch (ProviderUnavailable exception) { throw exception; }
        catch (Exception exception) { throw new ProviderUnavailable(); }
    }

    private String format(float[] values) {
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) result.append(',');
            result.append(values[i]);
        }
        return result.append(']').toString();
    }

    public static class ProviderUnavailable extends RuntimeException { }
}
