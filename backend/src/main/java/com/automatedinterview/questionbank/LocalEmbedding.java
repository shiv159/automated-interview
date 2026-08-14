package com.automatedinterview.questionbank;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

/**
 * Deterministic, zero-dependency embedding for development and test use only.
 *
 * The algorithm hashes each token into a 64-element feature array, then zero-pads
 * to 768 dimensions so that the output is compatible with the pgvector(768) schema
 * used by Spring AI's PgVectorStore.
 *
 * WARNING: Local vectors are not semantically equivalent to Vertex AI embeddings.
 * Switching an existing database between the "local" and "ai" embedding profiles
 * requires truncating vector_store and allowing VectorReconciliationJob to rebuild
 * all vectors from scratch.
 */
public final class LocalEmbedding {
    /** Total output dimensions — must match the vector_store schema. */
    public static final int DIMENSIONS = 768;
    /** Number of meaningful feature slots; remaining slots are zero-padded. */
    private static final int FEATURE_DIMS = 64;

    private LocalEmbedding() { }

    public static String vector(String text) {
        double[] values = new double[DIMENSIONS];
        String[] tokens = text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{Nd}]+");
        for (String token : tokens) {
            if (token.isBlank()) continue;
            byte[] digest = digest(token);
            for (int i = 0; i < digest.length; i++) values[i % FEATURE_DIMS] += digest[i] / 255.0;
        }
        // Normalise the feature portion; zero-padded slots remain 0.0.
        double norm = 0;
        for (int i = 0; i < FEATURE_DIMS; i++) norm += values[i] * values[i];
        norm = Math.sqrt(norm);
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < DIMENSIONS; i++) {
            if (i > 0) result.append(',');
            result.append((i < FEATURE_DIMS && norm != 0) ? values[i] / norm : 0.0);
        }
        return result.append(']').toString();
    }

    private static byte[] digest(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
}

