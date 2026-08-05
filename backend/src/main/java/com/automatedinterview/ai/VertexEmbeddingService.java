package com.automatedinterview.ai;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class VertexEmbeddingService {
    private final EmbeddingModel model;
    private final int dimensions;

    public VertexEmbeddingService(@Value("${VERTEX_EMBEDDING_DIMENSIONS:768}") int dimensions,
                                  ObjectProvider<EmbeddingModel> model) {
        this.model = model.getIfAvailable();
        this.dimensions = dimensions;
    }

    public String embed(String text) {
        if (model == null) throw new ProviderUnavailable();
        try {
            float[] values = model.embed(text);
            if (values.length != dimensions) throw new ProviderUnavailable();
            StringBuilder result = new StringBuilder("[");
            for (int i = 0; i < values.length; i++) {
                if (i > 0) result.append(',');
                result.append(values[i]);
            }
            return result.append(']').toString();
        } catch (ProviderUnavailable exception) { throw exception; }
        catch (Exception exception) { throw new ProviderUnavailable(); }
    }

    public static class ProviderUnavailable extends RuntimeException { }
}
