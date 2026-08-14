package com.automatedinterview.document;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipInputStream;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.ExtractedTextFormatter;
import org.springframework.ai.reader.pdf.PagePdfDocumentReader;
import org.springframework.ai.reader.pdf.config.PdfDocumentReaderConfig;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * Extracts plain text from uploaded documents (PDF, DOCX, TXT).
 *
 * <p>Security and validation contracts (preserved from the original implementation):
 * <ul>
 *   <li>Maximum file size: 2 MB</li>
 *   <li>Allowed extensions/MIME types: .txt/text/plain, .pdf/application/pdf,
 *       .docx/vnd.openxmlformats-officedocument.wordprocessingml.document</li>
 *   <li>Manual DOCX ZIP structure validation</li>
 *   <li>Rejection of blank extraction results</li>
 * </ul>
 */
@Service
public class DocumentTextExtractor {

    public String extract(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty() || file.getSize() > 2 * 1024 * 1024)
            throw new IllegalArgumentException("DOCUMENT_LIMIT_EXCEEDED");

        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        byte[] bytes = file.getBytes();

        boolean textName = name.endsWith(".txt");
        boolean pdfName  = name.endsWith(".pdf");
        boolean docxName = name.endsWith(".docx");

        // Strict MIME/extension cross-validation (preserved from original).
        if (textName && !contentType.isBlank() && !contentType.equals("text/plain"))
            throw new IllegalArgumentException("UNSUPPORTED_DOCUMENT");
        if (pdfName && !contentType.isBlank() && !contentType.equals("application/pdf"))
            throw new IllegalArgumentException("UNSUPPORTED_DOCUMENT");
        if (docxName && !contentType.isBlank() && !contentType.contains("wordprocessingml.document"))
            throw new IllegalArgumentException("UNSUPPORTED_DOCUMENT");

        if (textName) return extractText(bytes);
        if (pdfName)  return extractPdf(bytes);
        if (docxName) return extractDocx(bytes);

        throw new IllegalArgumentException("UNSUPPORTED_DOCUMENT");
    }

    // -------------------------------------------------------------------------
    // Extraction helpers
    // -------------------------------------------------------------------------

    private String extractText(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            return DocumentNormalizer.normalize(text);
        } catch (CharacterCodingException e) {
            throw new IllegalArgumentException("INVALID_DOCUMENT", e);
        }
    }

    private String extractPdf(byte[] bytes) {
        // Magic-byte check: every PDF starts with "%PDF-".
        if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-"))
            throw new IllegalArgumentException("INVALID_DOCUMENT");

        PdfDocumentReaderConfig config = PdfDocumentReaderConfig.builder()
                .withPageExtractedTextFormatter(ExtractedTextFormatter.defaults())
                .withPagesPerDocument(Integer.MAX_VALUE) // read all pages as one Document
                .build();
        PagePdfDocumentReader reader = new PagePdfDocumentReader(new ByteArrayResource(bytes), config);
        String text = joinDocuments(reader.read());
        if (text.isBlank()) throw new IllegalArgumentException("DOCUMENT_TEXT_NOT_EXTRACTABLE");
        return DocumentNormalizer.normalize(text);
    }

    private String extractDocx(byte[] bytes) throws IOException {
        // ZIP structure validation (preserved from original).
        if (!validDocx(bytes)) throw new IllegalArgumentException("INVALID_DOCUMENT");

        TikaDocumentReader reader = new TikaDocumentReader(new ByteArrayResource(bytes));
        String text = joinDocuments(reader.read());
        if (text.isBlank()) throw new IllegalArgumentException("DOCUMENT_TEXT_NOT_EXTRACTABLE");
        return DocumentNormalizer.normalize(text);
    }

    /** Joins text from multiple Spring AI {@link Document} objects with a newline separator. */
    private String joinDocuments(List<Document> documents) {
        return documents.stream()
                .map(Document::getText)
                .filter(t -> t != null && !t.isBlank())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");
    }

    /** Checks that the DOCX ZIP contains the required structural entries. */
    private boolean validDocx(byte[] bytes) throws IOException {
        boolean contentTypes = false;
        boolean document = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                contentTypes |= entry.getName().equals("[Content_Types].xml");
                document     |= entry.getName().equals("word/document.xml");
            }
        }
        return contentTypes && document;
    }
}

