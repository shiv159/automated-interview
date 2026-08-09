package com.automatedinterview.ai;

import org.springframework.core.env.Environment;

public final class AiConfigurationValidator {
    private AiConfigurationValidator() {
    }

    public static void validate(Environment environment) {
        validate(
            environment.getProperty("AI_DATA_RETENTION_ACKNOWLEDGED", Boolean.class, false),
            environment.getProperty("APP_ANSWER_EVALUATION_PROFILE", "ai"),
            environment.getProperty("APP_QUESTION_ENRICHMENT_PROFILE", "ai"),
            environment.getProperty("APP_EMBEDDING_PROFILE", "local"),
            environment.getProperty("VERTEX_PROJECT_ID", "")
        );
    }

    static void validate(boolean retentionAcknowledged, String evaluationProfile, String enrichmentProfile, String embeddingProfile, String projectId) {
        boolean aiEnabled = "ai".equals(evaluationProfile) || "ai".equals(enrichmentProfile) || "ai".equals(embeddingProfile);
        if (aiEnabled && !retentionAcknowledged)
            throw new IllegalStateException("AI_DATA_RETENTION_ACKNOWLEDGED must be true when an AI profile is enabled");
        if (aiEnabled && (projectId == null || projectId.isBlank()))
            throw new IllegalStateException("VERTEX_PROJECT_ID must be set when APP_ANSWER_EVALUATION_PROFILE, APP_QUESTION_ENRICHMENT_PROFILE, or APP_EMBEDDING_PROFILE enables ai; set VERTEX_PROJECT_ID=intervu-ai-20260704-8f3c and ensure ADC is available");
    }
}
