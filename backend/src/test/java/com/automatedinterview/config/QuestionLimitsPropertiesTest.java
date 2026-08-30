package com.automatedinterview.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class QuestionLimitsPropertiesTest {
    @Test
    void acceptsConfiguredDefaultsAndValues() {
        var limits = new QuestionLimitsProperties(new QuestionLimitsProperties.QuestionBank(50, 10_000, 50, 100), new QuestionLimitsProperties.Interview(3, 10));

        assertEquals(50, limits.questionBank().maxImportQuestions());
        assertEquals(10_000, limits.questionBank().maxTotalQuestions());
        assertEquals(3, limits.interview().questionsPerSession());
    }

    @Test
    void rejectsValuesOutsideOperationalBounds() {
        assertThrows(IllegalArgumentException.class, () -> new QuestionLimitsProperties.QuestionBank(0, 10_000, 50, 100));
        assertThrows(IllegalArgumentException.class, () -> new QuestionLimitsProperties.Interview(11, 10));
        assertThrows(IllegalArgumentException.class, () -> new QuestionLimitsProperties.Interview(3, 2));
    }
}
