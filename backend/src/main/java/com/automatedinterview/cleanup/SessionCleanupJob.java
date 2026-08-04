package com.automatedinterview.cleanup;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SessionCleanupJob {
    private final JdbcClient jdbc;
    public SessionCleanupJob(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Scheduled(fixedDelay = 900000)
    public void purgeExpired() {
        jdbc.sql("DELETE FROM interview_session WHERE expires_at <= now()").update();
    }
}

