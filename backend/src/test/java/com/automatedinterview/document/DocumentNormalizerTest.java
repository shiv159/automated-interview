package com.automatedinterview.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class DocumentNormalizerTest {
    @Test
    void normalizesWhitespaceAndLineEdges() {
        assertEquals("Java design\n\nSpring Boot", DocumentNormalizer.normalize("  Java\t design\r\n\r\n\r\n Spring Boot  "));
    }

    @Test
    void rejectsControlsAndOversizedText() {
        assertThrows(IllegalArgumentException.class, () -> DocumentNormalizer.normalize("valid\u0001text"));
        assertThrows(IllegalArgumentException.class, () -> DocumentNormalizer.normalize("x".repeat(30_001)));
    }

    @Test
    void repairsCommonMojibake() {
        assertEquals("3-7 years and \"Java\"", DocumentNormalizer.normalize("3â€“7 years and â€œJavaâ€ "));
    }

    @Test
    void masksEmailAndPhoneNumbersWhilePreservingContext() {
        String input = "John Doe, email: john.doe@example.com, phone: +1 (555) 123-4567. Senior Java Engineer with Spring Boot experience.";
        String masked = DocumentNormalizer.maskPii(input);
        assertFalse(masked.contains("john.doe@example.com"));
        assertFalse(masked.contains("123-4567"));
        assertTrue(masked.contains("[EMAIL]"));
        assertTrue(masked.contains("[PHONE]"));
        assertTrue(masked.contains("Senior Java Engineer with Spring Boot experience"));
    }
}
