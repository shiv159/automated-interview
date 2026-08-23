package com.automatedinterview.document;

import java.io.IOException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/documents")
public class DocumentPreviewController {
    private static final int PREVIEW_LIMIT = 900;
    private final DocumentTextExtractor extractor;

    public DocumentPreviewController(DocumentTextExtractor extractor) {
        this.extractor = extractor;
    }

    @PostMapping(value = "/preview", consumes = "multipart/form-data")
    public PreviewResponse preview(@RequestParam MultipartFile file) {
        try {
            String text = extractor.extract(file);
            if (text == null || text.isBlank()) throw new PreviewException("DOCUMENT_TEXT_NOT_EXTRACTABLE");
            boolean truncated = text.codePointCount(0, text.length()) > PREVIEW_LIMIT;
            int end = text.offsetByCodePoints(0, Math.min(PREVIEW_LIMIT, text.codePointCount(0, text.length())));
            String type = file.getOriginalFilename() == null ? "UNKNOWN" : file.getOriginalFilename().toLowerCase().endsWith(".pdf") ? "PDF" : file.getOriginalFilename().toLowerCase().endsWith(".docx") ? "DOCX" : "TXT";
            return new PreviewResponse(text.substring(0, end), truncated, type);
        } catch (IllegalArgumentException exception) {
            throw new PreviewException(exception.getMessage() == null ? "INVALID_DOCUMENT" : exception.getMessage());
        } catch (IOException exception) {
            throw new PreviewException("INVALID_DOCUMENT");
        }
    }

    public record PreviewResponse(String text, boolean truncated, String documentType) { }

    public static class PreviewException extends RuntimeException {
        private final String code;
        public PreviewException(String code) { this.code = code; }
        public String code() { return code; }
    }
}
