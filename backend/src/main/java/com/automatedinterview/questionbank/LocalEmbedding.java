package com.automatedinterview.questionbank;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;

public final class LocalEmbedding {
    public static final int DIMENSIONS = 64;

    private LocalEmbedding() { }

    public static String vector(String text) {
        double[] values = new double[DIMENSIONS];
        String[] tokens = text.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{Nd}]+");
        for (String token : tokens) {
            if (token.isBlank()) continue;
            byte[] digest = digest(token);
            for (int i = 0; i < digest.length; i++) values[i % DIMENSIONS] += digest[i] / 255.0;
        }
        double norm = 0;
        for (double value : values) norm += value * value;
        norm = Math.sqrt(norm);
        StringBuilder result = new StringBuilder("[");
        for (int i = 0; i < DIMENSIONS; i++) {
            if (i > 0) result.append(',');
            result.append(norm == 0 ? 0 : values[i] / norm);
        }
        return result.append(']').toString();
    }

    private static byte[] digest(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
}

