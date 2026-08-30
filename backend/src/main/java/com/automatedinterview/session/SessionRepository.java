package com.automatedinterview.session;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SessionRepository {
    private final JdbcClient jdbc;

    public SessionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean deleteOwned(UUID sessionId, String tokenHash) {
        return jdbc.sql("""
                DELETE FROM interview_session
                WHERE id = :id AND token_hash = :tokenHash AND expires_at > now()
                """)
            .param("id", sessionId)
            .param("tokenHash", tokenHash)
            .update() == 1;
    }
}
