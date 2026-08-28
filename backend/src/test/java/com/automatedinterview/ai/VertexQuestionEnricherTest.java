package com.automatedinterview.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.automatedinterview.ai.VertexQuestionEnricher.Enrichment;
import org.junit.jupiter.api.Test;

class VertexQuestionEnricherTest {
    @Test
    void acceptsValidTechnicalResponse() {
        String response = """
            {
              "type": "TECHNICAL",
              "primarySkill": "Java",
              "difficulty": "MEDIUM",
              "tags": ["collections", "algorithms"],
              "idealAnswer": "A strong answer explains time and space tradeoffs, names the data structures involved, and justifies the final approach with concrete examples."
            }
            """;

        Enrichment enrichment = VertexQuestionEnricher.parseCandidateText(response, "TECHNICAL", "Java");

        assertEquals(new Enrichment(
            "TECHNICAL",
            "Java",
            "MEDIUM",
            java.util.List.of("collections", "algorithms"),
            "A strong answer explains time and space tradeoffs, names the data structures involved, and justifies the final approach with concrete examples."
        ), enrichment);
    }

    @Test
    void acceptsMultipleSecondarySkillsAlongsideOnePrimarySkill() {
        String response = """
            {
              "type": "TECHNICAL",
              "primarySkill": "SPRING_BOOT",
              "secondarySkills": ["DOCKER", "SQL_RELATIONAL"],
              "difficulty": "MEDIUM",
              "tags": ["deployment"],
              "idealAnswer": "A strong answer explains the service boundary, container packaging, database integration, deployment configuration, health checks, and how the design would be tested in production."
            }
            """;

        assertEquals(java.util.List.of("DOCKER", "SQL_RELATIONAL"),
            VertexQuestionEnricher.parseCandidateText(response, "TECHNICAL", "SPRING_BOOT").secondarySkills());
    }

    @Test
    void rejectsUnknownProperties() {
        String response = """
            {
              "type": "TECHNICAL",
              "primarySkill": "Java",
              "difficulty": "MEDIUM",
              "tags": ["collections"],
              "idealAnswer": "A strong answer explains time and space tradeoffs, names the data structures involved, and justifies the final approach with concrete examples.",
              "unexpected": "value"
            }
            """;

        assertThrows(IllegalArgumentException.class, () -> VertexQuestionEnricher.parseCandidateText(response, "TECHNICAL", "Java"));
    }

    @Test
    void rejectsBehavioralResponseWhenPrimarySkillIsNotNull() {
        String response = """
            {
              "type": "BEHAVIORAL",
              "primarySkill": "Leadership",
              "difficulty": null,
              "tags": ["communication"],
              "idealAnswer": "A strong answer describes a real situation, explains the candidate's actions clearly, and ends with the measurable outcome and lesson learned."
            }
            """;

        assertThrows(IllegalArgumentException.class, () -> VertexQuestionEnricher.parseCandidateText(response, "BEHAVIORAL", null));
    }

    @Test
    void rejectsBehavioralResponseWhenDifficultyIsNotNull() {
        String response = """
            {
              "type": "BEHAVIORAL",
              "primarySkill": null,
              "difficulty": "EASY",
              "tags": ["communication"],
              "idealAnswer": "A strong answer describes a real situation, explains the candidate's actions clearly, and ends with the measurable outcome and lesson learned."
            }
            """;

        assertThrows(IllegalArgumentException.class, () -> VertexQuestionEnricher.parseCandidateText(response, "BEHAVIORAL", null));
    }

    @Test
    void rejectsDuplicateTags() {
        String response = """
            {
              "type": "TECHNICAL",
              "primarySkill": "Java",
              "difficulty": "MEDIUM",
              "tags": ["collections", "collections"],
              "idealAnswer": "A strong answer explains time and space tradeoffs, names the data structures involved, and justifies the final approach with concrete examples."
            }
            """;

        assertThrows(IllegalArgumentException.class, () -> VertexQuestionEnricher.parseCandidateText(response, "TECHNICAL", "Java"));
    }

    @Test
    void rejectsBlankTags() {
        String response = """
            {
              "type": "TECHNICAL",
              "primarySkill": "Java",
              "difficulty": "MEDIUM",
              "tags": ["collections", "   "],
              "idealAnswer": "A strong answer explains time and space tradeoffs, names the data structures involved, and justifies the final approach with concrete examples."
            }
            """;

        assertThrows(IllegalArgumentException.class, () -> VertexQuestionEnricher.parseCandidateText(response, "TECHNICAL", "Java"));
    }

    @Test
    void rejectsTooShortIdealAnswer() {
        String response = """
            {
              "type": "TECHNICAL",
              "primarySkill": "Java",
              "difficulty": "MEDIUM",
              "tags": ["collections"],
              "idealAnswer": "Needs more detail."
            }
            """;

        assertThrows(IllegalArgumentException.class, () -> VertexQuestionEnricher.parseCandidateText(response, "TECHNICAL", "Java"));
    }

    @Test
    void rejectsTooLongIdealAnswer() {
        String tooLong = "x".repeat(2001);
        String response = """
            {
              "type": "TECHNICAL",
              "primarySkill": "Java",
              "difficulty": "MEDIUM",
              "tags": ["collections"],
              "idealAnswer": "%s"
            }
            """.formatted(tooLong);

        assertThrows(IllegalArgumentException.class, () -> VertexQuestionEnricher.parseCandidateText(response, "TECHNICAL", "Java"));
    }
}
