package com.automatedinterview.questionbank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.ObjectMapper;

class QuestionFileParserTest {
    private final QuestionFileParser parser = new QuestionFileParser(new ObjectMapper());

    @Test
    void parsesAndNormalizesTextQuestions() {
        var file = textFile("Q1: Design a Spring Boot service.\r\n");

        var questions = parser.parseStrict(file, 10);

        assertEquals(1, questions.size());
        assertEquals("Design a Spring Boot service.", questions.getFirst().stem());
        assertNull(questions.getFirst().type());
    }

    @Test
    void parsesJsonQuestionMetadata() {
        var file = jsonFile("""
            [{"stem":"Explain relational database indexes.","type":"TECHNICAL","primarySkill":"SQL_RELATIONAL","difficulty":"MEDIUM","secondarySkills":[]}]
            """);

        var questions = parser.parseStrict(file, 10);

        assertEquals(1, questions.size());
        assertEquals("SQL_RELATIONAL", questions.getFirst().primarySkill());
        assertEquals("MEDIUM", questions.getFirst().difficulty());
    }

    @Test
    void lenientTextParsingReturnsDiagnosticsAndValidRows() {
        var file = textFile("Too short\nDescribe how you would design a Spring Boot service.");

        var result = parser.parseLenient(file, 10);

        assertEquals(1, result.questions().size());
        assertEquals(1, result.errors().size());
        assertEquals(1, result.errors().getFirst().line());
        assertTrue(result.errors().getFirst().hint().contains("20 characters"));
    }

    @Test
    void rejectsMalformedUtf8() {
        var file = new MockMultipartFile("questionsFile", "questions.txt", "text/plain", new byte[] {(byte) 0xc3, 0x28});

        var exception = assertThrows(QuestionImportService.ImportException.class,
            () -> parser.parseStrict(file, 10));

        assertEquals("INVALID_QUESTION_FILE", exception.code());
    }

    private MockMultipartFile textFile(String value) {
        return new MockMultipartFile("questionsFile", "questions.txt", "text/plain", value.getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile jsonFile(String value) {
        return new MockMultipartFile("questionsFile", "questions.json", "application/json", value.getBytes(StandardCharsets.UTF_8));
    }
}
