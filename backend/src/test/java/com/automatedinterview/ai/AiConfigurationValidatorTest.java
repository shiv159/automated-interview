package com.automatedinterview.ai;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class AiConfigurationValidatorTest {
    @Test
    void localProfilesDoNotRequireProviderAcknowledgement() {
        assertDoesNotThrow(() -> new AiConfigurationValidator(false, "stub", "disabled", "local"));
    }

    @Test
    void aiProfilesRequireRetentionAcknowledgement() {
        assertThrows(IllegalStateException.class, () -> new AiConfigurationValidator(false, "stub", "ai", "local"));
    }
}
