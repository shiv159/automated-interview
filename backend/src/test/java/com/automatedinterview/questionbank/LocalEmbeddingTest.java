package com.automatedinterview.questionbank;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import org.junit.jupiter.api.Test;

class LocalEmbeddingTest {
    @Test
    void producesStableFixedDimensionVectors() {
        String first = LocalEmbedding.vector("Java concurrency");
        assertEquals(first, LocalEmbedding.vector("Java concurrency"));
        assertEquals(LocalEmbedding.DIMENSIONS, first.substring(1, first.length() - 1).split(",").length);
        assertNotEquals(first, LocalEmbedding.vector("Angular components"));
    }
}
