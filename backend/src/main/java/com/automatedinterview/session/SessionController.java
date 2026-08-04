package com.automatedinterview.session;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {
    private final SessionService service;

    public SessionController(SessionService service) { this.service = service; }

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<SessionResponse> create(
        @RequestParam MultipartFile jobDescription,
        @RequestParam MultipartFile resume,
        @RequestParam int yearsExperience,
        @RequestParam boolean syntheticDataAttested) {
        SessionService.CreatedSession created = service.create(jobDescription, resume, yearsExperience, syntheticDataAttested);
        ResponseCookie cookie = ResponseCookie.from("AIP_SESSION", created.token())
            .httpOnly(true).sameSite("Strict").path("/").maxAge(7200).build();
        return ResponseEntity.status(201).header(HttpHeaders.SET_COOKIE, cookie.toString()).body(created.response());
    }
}

