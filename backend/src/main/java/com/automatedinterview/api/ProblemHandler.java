package com.automatedinterview.api;

import com.automatedinterview.analysis.VertexSkillAnalyzer;
import com.automatedinterview.session.SessionService;
import com.automatedinterview.interview.InterviewService;
import com.automatedinterview.questionbank.QuestionImportService;
import com.automatedinterview.document.DocumentPreviewController;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

@RestControllerAdvice
public class ProblemHandler {
    private static final Logger log = LoggerFactory.getLogger(ProblemHandler.class);
    @ExceptionHandler(SessionService.SessionInputException.class)
    ResponseEntity<Problem> input(SessionService.SessionInputException exception, HttpServletRequest request) {
        int status = exception.code().equals("INVALID_SESSION_TOKEN") ? 401 : exception.code().equals("SESSION_EXPIRED") ? 410 : exception.code().equals("SESSION_NOT_FOUND") ? 404 : exception.code().equals("DOCUMENT_LIMIT_EXCEEDED") ? 413 : exception.code().equals("UNSUPPORTED_DOCUMENT") ? 415 : exception.code().equals("ATTESTATION_REQUIRED") || exception.code().equals("INVALID_EXPERIENCE")
            ? 400 : exception.code().equals("NO_SUPPORTED_SKILLS") ? 400 : 400;
        return response(exception.code(), status, request);
    }

    @ExceptionHandler(VertexSkillAnalyzer.SkillProviderException.class)
    ResponseEntity<Problem> provider(VertexSkillAnalyzer.SkillProviderException exception, HttpServletRequest request) {
        log.warn("skill_analysis_error category={} providerFailure={} correlationId={}",
            exception.category(), exception.providerFailure(), MDC.get(ApiRequestLoggingFilter.CORRELATION_MDC_KEY));
        String code = switch (exception.category()) {
            case "evidence_not_found", "missing_evidence", "evidence_crosses_line" -> "SKILL_EVIDENCE_INVALID";
            case "duplicate_skill", "invalid_skill_id", "invalid_importance", "invalid_status", "invalid_skills" -> "SKILL_ANALYSIS_INVALID";
            case "provider_uncertain" -> "SKILL_ANALYSIS_UNCERTAIN";
            default -> exception.providerFailure() ? "AI_PROVIDER_UNAVAILABLE" : "SKILL_ANALYSIS_UNCERTAIN";
        };
        int status = Set.of("SKILL_EVIDENCE_INVALID", "SKILL_ANALYSIS_INVALID", "SKILL_ANALYSIS_UNCERTAIN").contains(code) ? 422 : 503;
        return response(code, status, request);
    }

    @ExceptionHandler(InterviewService.InterviewException.class)
    ResponseEntity<Problem> interview(InterviewService.InterviewException exception, HttpServletRequest request) {
        return response(exception.code(), exception.status(), request);
    }

    @ExceptionHandler(QuestionImportService.ImportException.class)
    ResponseEntity<Problem> importProblem(QuestionImportService.ImportException exception, HttpServletRequest request) {
        return response(exception.code(), exception.status(), request, exception.getMessage(), exception.item(), exception.line(), exception.field(), exception.hint(), exception.errors());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Problem> uploadLimit(MaxUploadSizeExceededException exception, HttpServletRequest request) {
        return response("DOCUMENT_LIMIT_EXCEEDED", 413, request);
    }

    @ExceptionHandler(DocumentPreviewController.PreviewException.class)
    ResponseEntity<Problem> preview(DocumentPreviewController.PreviewException exception, HttpServletRequest request) {
        int status = Set.of("DOCUMENT_LIMIT_EXCEEDED").contains(exception.code()) ? 413 : 400;
        return response(exception.code(), status, request);
    }

    private ResponseEntity<Problem> response(String code, int status, HttpServletRequest request) {
        return response(code, status, request, code);
    }

    private ResponseEntity<Problem> response(String code, int status, HttpServletRequest request, String detail) {
        return response(code, status, request, detail, null, null, null, null);
    }

    private ResponseEntity<Problem> response(String code, int status, HttpServletRequest request, String detail,
        Integer item, Integer line, String field, String hint) {
        return response(code, status, request, detail, item, line, field, hint, List.of());
    }

    private ResponseEntity<Problem> response(String code, int status, HttpServletRequest request, String detail,
        Integer item, Integer line, String field, String hint, List<QuestionImportService.ImportDiagnostic> errors) {
        log.warn("api_error method={} path={} status={} code={} correlationId={}",
            request.getMethod(), request.getRequestURI(), status, code,
            MDC.get(ApiRequestLoggingFilter.CORRELATION_MDC_KEY));
        String correlationId = MDC.get(ApiRequestLoggingFilter.CORRELATION_MDC_KEY);
        if (correlationId == null || correlationId.isBlank()) correlationId = UUID.randomUUID().toString();
        Problem problem = new Problem("urn:automated-interview:problem:" + code, code, status, detail == null || detail.isBlank() ? code : detail,
            request.getRequestURI(), code, correlationId, item, line, field, hint, errors);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem);
    }

    public record Problem(String type, String title, int status, String detail, String instance, String code, String correlationId,
        Integer item, Integer line, String field, String hint, List<QuestionImportService.ImportDiagnostic> errors) { }
}
