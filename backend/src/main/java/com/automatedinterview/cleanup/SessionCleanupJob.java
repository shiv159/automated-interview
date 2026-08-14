package com.automatedinterview.cleanup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class SessionCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(SessionCleanupJob.class);
    private final JdbcClient jdbc;
    public SessionCleanupJob(JdbcClient jdbc) { this.jdbc = jdbc; }

    @Scheduled(fixedDelay = 900000)
    public void purgeExpired() {
        try {
            int deleted = jdbc.sql("DELETE FROM interview_session WHERE expires_at <= now()").update();
            if (deleted > 0) log.info("Session cleanup removed {} expired session(s).", deleted);
        } catch (DataAccessResourceFailureException exception) {
            // A database outage must not terminate or flood the scheduler thread.
            // The next fixed-delay execution will retry the cleanup.
            log.warn("Session cleanup skipped because the database is unavailable; will retry later. Cause: {}",
                    exception.getMostSpecificCause() == null ? exception.getMessage()
                            : exception.getMostSpecificCause().getMessage());
        }
    }
}
