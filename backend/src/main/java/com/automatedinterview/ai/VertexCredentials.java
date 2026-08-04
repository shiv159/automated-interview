package com.automatedinterview.ai;

public final class VertexCredentials {
    private VertexCredentials() { }

    public static String token(String value) {
        String normalized = value == null ? "" : value.strip();
        return normalized.startsWith("-") ? normalized.substring(1) : normalized;
    }
}
