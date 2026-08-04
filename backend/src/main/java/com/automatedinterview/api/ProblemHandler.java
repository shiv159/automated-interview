package com.automatedinterview.api;

import com.automatedinterview.analysis.VertexSkillAnalyzer;
import com.automatedinterview.session.SessionService;
import com.automatedinterview.interview.InterviewService;
import com.automatedinterview.questionbank.QuestionImportService;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class ProblemHandler {
    @ExceptionHandler(SessionService.SessionInputException.class)
    ResponseEntity<Problem> input(SessionService.SessionInputException exception, HttpServletRequest request) {
        int status = exception.code().equals("INVALID_SESSION_TOKEN") ? 401 : exception.code().equals("SESSION_EXPIRED") ? 410 : exception.code().equals("SESSION_NOT_FOUND") ? 404 : exception.code().equals("DOCUMENT_LIMIT_EXCEEDED") ? 413 : exception.code().equals("UNSUPPORTED_DOCUMENT") ? 415 : exception.code().equals("ATTESTATION_REQUIRED") || exception.code().equals("INVALID_EXPERIENCE")
            ? 400 : exception.code().equals("NO_SUPPORTED_SKILLS") ? 400 : 400;
        return response(exception.code(), status, request);
    }

    @ExceptionHandler(VertexSkillAnalyzer.SkillProviderException.class)
    ResponseEntity<Problem> provider(VertexSkillAnalyzer.SkillProviderException exception, HttpServletRequest request) {
        int status = exception.providerFailure() ? 503 : 422;
        String code = exception.providerFailure() ? "SKILL_ANALYSIS_UNAVAILABLE" : "SKILL_ANALYSIS_UNCERTAIN";
        return response(code, status, request);
    }

    @ExceptionHandler(InterviewService.InterviewException.class)
    ResponseEntity<Problem> interview(InterviewService.InterviewException exception, HttpServletRequest request) {
        return response(exception.code(), exception.status(), request);
    }

    @ExceptionHandler(QuestionImportService.ImportException.class)
    ResponseEntity<Problem> importProblem(QuestionImportService.ImportException exception, HttpServletRequest request) {
        return response(exception.code(), exception.status(), request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Problem> uploadLimit(MaxUploadSizeExceededException exception, HttpServletRequest request) {
        return response("DOCUMENT_LIMIT_EXCEEDED", 413, request);
    }

    private ResponseEntity<Problem> response(String code, int status, HttpServletRequest request) {
        Problem problem = new Problem("urn:automated-interview:problem:" + code, code, status, code,
            request.getRequestURI(), code, UUID.randomUUID().toString());
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    public record Problem(String type, String title, int status, String detail, String instance, String code, String correlationId) { }
}
