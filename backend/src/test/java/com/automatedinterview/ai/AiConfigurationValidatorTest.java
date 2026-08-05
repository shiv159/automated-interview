package com.automatedinterview.ai;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class AiConfigurationValidatorTest {
    @Test
    void localProfilesDoNotRequireProviderAcknowledgement() {
        assertDoesNotThrow(() -> AiConfigurationValidator.validate(false, "stub", "disabled", "local", ""));
    }

    @Test
    void aiProfilesRequireRetentionAcknowledgement() {
        assertThrows(IllegalStateException.class, () -> AiConfigurationValidator.validate(false, "stub", "ai", "local", "intervu-ai-20260704-8f3c"));
    }

    @Test
    void aiProfilesRequireVertexProjectId() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> AiConfigurationValidator.validate(true, "ai", "disabled", "local", ""));

        assertTrue(exception.getMessage().contains("VERTEX_PROJECT_ID"));
    }

    @Test
    void aiProfilesAcceptConfiguredVertexProjectId() {
        assertDoesNotThrow(() -> AiConfigurationValidator.validate(true, "ai", "disabled", "local", "intervu-ai-20260704-8f3c"));
    }
}
