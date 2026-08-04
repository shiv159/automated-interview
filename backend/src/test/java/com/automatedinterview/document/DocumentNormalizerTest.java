package com.automatedinterview.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
}
