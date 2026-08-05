package com.automatedinterview.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(boolean enabled, int maxAttempts, long retryBackoffMs, int embeddingDimensions, int embeddingBatchSize) {
    public AiProperties {
        if (maxAttempts < 1 || maxAttempts > 3) throw new IllegalArgumentException("app.ai.max-attempts must be 1..3");
        if (retryBackoffMs < 0 || retryBackoffMs > 5000) throw new IllegalArgumentException("app.ai.retry-backoff-ms must be 0..5000");
        if (embeddingDimensions < 1) throw new IllegalArgumentException("app.ai.embedding-dimensions must be positive");
        if (embeddingBatchSize < 1 || embeddingBatchSize > 100) throw new IllegalArgumentException("app.ai.embedding-batch-size must be 1..100");
    }
}
