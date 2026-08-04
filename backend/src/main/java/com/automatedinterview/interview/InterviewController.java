package com.automatedinterview.interview;

import java.util.UUID;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions/{sessionId}")
public class InterviewController {
    private final InterviewService service;

    public InterviewController(InterviewService service) { this.service = service; }

    @PostMapping("/interview")
    public InterviewService.QuestionResponse start(@PathVariable UUID sessionId, @CookieValue(name = "AIP_SESSION", required = false) String token) {
        return service.start(sessionId, token);
    }

    @PostMapping("/questions/{instanceId}/answers")
    public InterviewService.AnswerResponse answer(@PathVariable UUID sessionId, @PathVariable UUID instanceId,
        @CookieValue(name = "AIP_SESSION", required = false) String token, @RequestBody AnswerRequest request) {
        return service.answer(sessionId, instanceId, token, request.answer());
    }

    @GetMapping("/report")
    public InterviewService.ReportResponse report(@PathVariable UUID sessionId, @CookieValue(name = "AIP_SESSION", required = false) String token) {
        return service.report(sessionId, token);
    }

    public record AnswerRequest(String answer) { }
}

