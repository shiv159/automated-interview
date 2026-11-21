package com.automatedinterview.ai;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public class AiResilience {
    private final AiProperties properties;
    public AiResilience(AiProperties properties) { this.properties = properties; }
    public <T> T call(Supplier<T> operation) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            try { return operation.get(); }
            catch (RuntimeException exception) {
                last = exception;
                if (!retryable(exception) || attempt == properties.maxAttempts()) throw exception;
                try {
                    long delay = (long)(properties.retryBackoffMs() * Math.pow(2, attempt - 1) + (Math.random() * 200));
                    Thread.sleep(Math.min(delay, 10_000L));
                }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw exception; }
            }
        }
        throw last;
    }
    private static boolean retryable(Throwable error) {
        String message = error.getMessage();
        if (message == null) return false;
        String value = message.toLowerCase(java.util.Locale.ROOT);
        return value.contains("429") || value.contains("rate limit") || value.contains("timeout") || value.contains("503") || value.contains("temporarily unavailable");
    }
}
