package com.automatedinterview.session;

import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions/{sessionId}")
public class SessionLifecycleController {
    private final JdbcClient jdbc;
    private final SessionService service;
    public SessionLifecycleController(JdbcClient jdbc, SessionService service) { this.jdbc = jdbc; this.service = service; }

    @GetMapping
    public SessionResponse get(@PathVariable UUID sessionId, @CookieValue(name = "AIP_SESSION", required = false) String token) {
        return service.snapshot(sessionId, token);
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@PathVariable UUID sessionId, @CookieValue(name = "AIP_SESSION", required = false) String token) {
        service.snapshot(sessionId, token);
        jdbc.sql("DELETE FROM interview_session WHERE id = :id").param("id", sessionId).update();
        return ResponseEntity.noContent().build();
    }

    private SessionState find(UUID id, String token) {
        if (token == null || token.isBlank()) throw new SessionService.SessionInputException("INVALID_SESSION_TOKEN");
        return jdbc.sql("SELECT id,state,difficulty,profile_match,expires_at FROM interview_session WHERE id=:id AND token_hash=:tokenHash")
            .param("id", id).param("tokenHash", SessionService.hash(token)).query((rs, row) -> new SessionState(rs.getObject("id", UUID.class), rs.getString("state"), rs.getString("difficulty"), rs.getDouble("profile_match"), rs.getTimestamp("expires_at").toInstant())).optional()
            .filter(session -> session.expiresAt().isAfter(Instant.now())).orElseThrow(() -> new SessionService.SessionInputException("SESSION_EXPIRED"));
    }

    public record SessionState(UUID id, String state, String difficulty, double profileMatch, Instant expiresAt) { }
}
