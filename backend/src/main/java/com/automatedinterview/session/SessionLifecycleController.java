package com.automatedinterview.session;

import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions/{sessionId}")
public class SessionLifecycleController {
    private final SessionService service;
    public SessionLifecycleController(SessionService service) { this.service = service; }

    @GetMapping
    public SessionResponse get(@PathVariable UUID sessionId, @CookieValue(name = "AIP_SESSION", required = false) String token) {
        return service.snapshot(sessionId, token);
    }

    @DeleteMapping
    public ResponseEntity<Void> delete(@PathVariable UUID sessionId, @CookieValue(name = "AIP_SESSION", required = false) String token) {
        service.delete(sessionId, token);
        return ResponseEntity.noContent().build();
    }
}
