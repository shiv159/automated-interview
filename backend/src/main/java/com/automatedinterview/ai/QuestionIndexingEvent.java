package com.automatedinterview.ai;

import java.util.UUID;

public record QuestionIndexingEvent(UUID questionId, Operation operation) {
    public enum Operation {
        UPSERT,
        DELETE
    }
}
