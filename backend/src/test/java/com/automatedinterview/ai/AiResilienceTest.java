package com.automatedinterview.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AiResilienceTest {
    @Test
    void retriesTransientProviderFailureOnlyWithinBound() {
        var resilience = new AiResilience(new AiProperties(true, 2, 0, 768, 32));
        AtomicInteger calls = new AtomicInteger();
        assertEquals("ok", resilience.call(() -> {
            if (calls.incrementAndGet() == 1) throw new IllegalStateException("429 rate limit");
            return "ok";
        }));
        assertEquals(2, calls.get());
    }

    @Test
    void doesNotRetryMalformedOutput() {
        var resilience = new AiResilience(new AiProperties(true, 3, 0, 768, 32));
        AtomicInteger calls = new AtomicInteger();
        assertThrows(IllegalArgumentException.class, () -> resilience.call(() -> {
            calls.incrementAndGet();
            throw new IllegalArgumentException("invalid response shape");
        }));
        assertEquals(1, calls.get());
    }
}
