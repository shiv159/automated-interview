package com.automatedinterview.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app")
public record QuestionLimitsProperties(QuestionBank questionBank, Interview interview) {
    public QuestionLimitsProperties {
        if (questionBank == null || interview == null) throw new IllegalArgumentException("Question limits are required");
    }

    public record QuestionBank(int maxImportQuestions, int maxTotalQuestions, int pageSize, int maxPageSize) {
        public QuestionBank {
            if (maxImportQuestions < 1 || maxImportQuestions > 100) throw new IllegalArgumentException("app.question-bank.max-import-questions must be 1..100");
            if (maxTotalQuestions < 1 || maxTotalQuestions > 100_000) throw new IllegalArgumentException("app.question-bank.max-total-questions must be 1..100000");
            if (pageSize < 1 || pageSize > 100) throw new IllegalArgumentException("app.question-bank.page-size must be 1..100");
            if (maxPageSize < pageSize || maxPageSize > 100) throw new IllegalArgumentException("app.question-bank.max-page-size must be >= page-size and <= 100");
        }
    }

    public record Interview(int questionsPerSession, int maxQuestionsPerSession) {
        public Interview {
            if (questionsPerSession < 1 || questionsPerSession > 10) throw new IllegalArgumentException("app.interview.questions-per-session must be 1..10");
            if (maxQuestionsPerSession < questionsPerSession || maxQuestionsPerSession > 10) throw new IllegalArgumentException("app.interview.max-questions-per-session must be >= questions-per-session and <= 10");
        }
    }
}
