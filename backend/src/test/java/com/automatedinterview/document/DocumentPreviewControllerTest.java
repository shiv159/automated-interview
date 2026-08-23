package com.automatedinterview.document;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class DocumentPreviewControllerTest {
    @Test
    void returnsNormalizedPreviewMetadataAndTruncatesByCodePoint() throws Exception {
        var extractor = mock(DocumentTextExtractor.class);
        var file = new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[] {1});
        String source = "a".repeat(899) + "😀" + "tail";
        when(extractor.extract(file)).thenReturn(source);

        var response = new DocumentPreviewController(extractor).preview(file);

        assertEquals("PDF", response.documentType());
        assertEquals(900, response.text().codePointCount(0, response.text().length()));
        assertEquals("😀", response.text().substring(response.text().length() - 2));
        assertEquals(true, response.truncated());
    }

    @Test
    void rejectsEmptyExtractedTextWithActionableCode() throws Exception {
        var extractor = mock(DocumentTextExtractor.class);
        var file = new MockMultipartFile("file", "resume.docx", "application/octet-stream", new byte[] {1});
        when(extractor.extract(file)).thenReturn(" \n ");

        var exception = assertThrows(DocumentPreviewController.PreviewException.class,
            () -> new DocumentPreviewController(extractor).preview(file));

        assertEquals("DOCUMENT_TEXT_NOT_EXTRACTABLE", exception.code());
    }
}
