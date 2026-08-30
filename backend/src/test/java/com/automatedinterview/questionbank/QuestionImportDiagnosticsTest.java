package com.automatedinterview.questionbank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class QuestionImportDiagnosticsTest {
    @Test
    void preservesSourceContextAndCorrectionHint() {
        QuestionImportService.ImportException exception = new QuestionImportService.ImportException(
            "QUESTION_SKILL_AMBIGUOUS", 422, "Question skill could not be determined.", null, null,
            "primarySkill", "Set primarySkill to a canonical supported skill ID.")
            .withContext(2, null, "primarySkill", "Set primarySkill to a canonical supported skill ID.");

        QuestionImportService.ImportDiagnostic diagnostic = exception.errors().get(0);

        assertEquals(2, diagnostic.item());
        assertEquals("primarySkill", diagnostic.field());
        assertEquals("QUESTION_SKILL_AMBIGUOUS", diagnostic.code());
        assertTrue(diagnostic.hint().contains("canonical"));
    }

    @Test
    void exposesAllBatchDiagnostics() {
        List<QuestionImportService.ImportDiagnostic> diagnostics = List.of(
            new QuestionImportService.ImportDiagnostic("INVALID_QUESTION_FILE", 422, null, 2, "stem", "Duplicate", "Change the stem."),
            new QuestionImportService.ImportDiagnostic("QUESTION_SKILL_AMBIGUOUS", 422, 3, null, "primarySkill", "Ambiguous", "Set the skill."));

        QuestionImportService.ImportException exception = QuestionImportService.ImportException.batch(diagnostics);

        assertEquals(2, exception.errors().size());
        assertEquals(2, exception.errors().get(0).line());
        assertEquals(3, exception.errors().get(1).item());
    }

    @Test
    void rejectsShortAndNonQuestionLikeTextStems() {
        var exception = QuestionImportService.validateTextQuestionStem("[INVALID TEST ROW: missing question text]");
        assertEquals("INVALID_QUESTION_STEM", exception.code());
        assertTrue(exception.hint().contains("question"));

        assertEquals("INVALID_QUESTION_STEM", QuestionImportService.validateTextQuestionStem("Hi").code());
        assertNull(QuestionImportService.validateTextQuestionStem("Build a REST API with Spring Boot."));
    }

    @Test
    void removesCommonTextListPrefixes() {
        assertEquals("Design a Spring Boot service.", QuestionImportService.removeTextListPrefix("Q3: Design a Spring Boot service."));
        assertEquals("What is PostgreSQL?", QuestionImportService.removeTextListPrefix("2) What is PostgreSQL?"));
        assertEquals("Explain Docker.", QuestionImportService.removeTextListPrefix("- Explain Docker."));
    }

    @Test
    void draftImportCapacityAllowsUpdatesButRejectsNewQuestionsAtTheLimit() {
        assertTrue(QuestionImportService.hasCapacity(10_000, 0, true, 10_000));
        assertTrue(!QuestionImportService.hasCapacity(10_000, 0, false, 10_000));
        assertTrue(QuestionImportService.hasCapacity(9_999, 0, false, 10_000));
    }
}
