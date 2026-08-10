package com.automatedinterview.document;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.zip.ZipInputStream;

@Service
public class DocumentTextExtractor {
    public String extract(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty() || file.getSize() > 2 * 1024 * 1024) throw new IllegalArgumentException("DOCUMENT_LIMIT_EXCEEDED");
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
        byte[] bytes = file.getBytes();
        boolean textName = name.endsWith(".txt");
        boolean pdfName = name.endsWith(".pdf");
        boolean docxName = name.endsWith(".docx");
        if (textName && !contentType.isBlank() && !contentType.equals("text/plain")) throw new IllegalArgumentException("UNSUPPORTED_DOCUMENT");
        if (pdfName && !contentType.isBlank() && !contentType.equals("application/pdf")) throw new IllegalArgumentException("UNSUPPORTED_DOCUMENT");
        if (docxName && !contentType.isBlank() && !contentType.contains("wordprocessingml.document")) throw new IllegalArgumentException("UNSUPPORTED_DOCUMENT");
        if (textName) {
            try {
                return DocumentNormalizer.normalize(StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString());
            } catch (CharacterCodingException exception) { throw new IllegalArgumentException("INVALID_DOCUMENT", exception); }
        }
        if (pdfName) {
            if (bytes.length < 5 || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-")) throw new IllegalArgumentException("INVALID_DOCUMENT");
            try (var document = Loader.loadPDF(bytes)) {
                String text = DocumentNormalizer.normalize(new PDFTextStripper().getText(document));
                if (text.isBlank()) throw new IllegalArgumentException("DOCUMENT_TEXT_NOT_EXTRACTABLE");
                return text;
            }
        }
        if (docxName) {
            if (!validDocx(bytes)) throw new IllegalArgumentException("INVALID_DOCUMENT");
            try (InputStream input = new ByteArrayInputStream(bytes); XWPFDocument document = new XWPFDocument(input);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
                String text = DocumentNormalizer.normalize(extractor.getText());
                if (text.isBlank()) throw new IllegalArgumentException("DOCUMENT_TEXT_NOT_EXTRACTABLE");
                return text;
            }
        }
        throw new IllegalArgumentException("UNSUPPORTED_DOCUMENT");
    }

    private boolean validDocx(byte[] bytes) throws IOException {
        boolean contentTypes = false;
        boolean document = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                contentTypes |= entry.getName().equals("[Content_Types].xml");
                document |= entry.getName().equals("word/document.xml");
            }
        }
        return contentTypes && document;
    }
}
