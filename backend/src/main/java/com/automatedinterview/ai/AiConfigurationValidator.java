package com.automatedinterview.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AiConfigurationValidator {
    public AiConfigurationValidator(
        @Value("${AI_DATA_RETENTION_ACKNOWLEDGED:false}") boolean retentionAcknowledged,
        @Value("${APP_ANSWER_EVALUATION_PROFILE:stub}") String evaluationProfile,
        @Value("${APP_QUESTION_ENRICHMENT_PROFILE:disabled}") String enrichmentProfile,
        @Value("${APP_EMBEDDING_PROFILE:local}") String embeddingProfile) {
        if ((!"stub".equals(evaluationProfile) || "ai".equals(enrichmentProfile) || "ai".equals(embeddingProfile)) && !retentionAcknowledged)
            throw new IllegalStateException("AI_DATA_RETENTION_ACKNOWLEDGED must be true when an AI profile is enabled");
    }
}
