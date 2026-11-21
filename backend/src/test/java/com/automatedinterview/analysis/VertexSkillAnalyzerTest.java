package com.automatedinterview.analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;

class VertexSkillAnalyzerTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void validatedClaimsAcceptsAcceptStatus() {
        List<SkillClaim> claims = validatedClaims("""
            {"status":"ACCEPT","skills":[{"skillId":"SPRING_BOOT","importance":"REQUIRED","evidence":"Spring Boot"}]}
            """, "Spring Boot");

        assertEquals(List.of(new SkillClaim("SPRING_BOOT", "REQUIRED", "Spring Boot", false)), claims);
    }

    @Test
    void validatedClaimsTreatsUncertainStatusAsAnalysisUncertain() {
        VertexSkillAnalyzer.SkillProviderException exception = assertThrows(VertexSkillAnalyzer.SkillProviderException.class, () -> validatedClaims("""
            {"status":"UNCERTAIN","skills":[]}
            """, "Spring Boot"));

        assertFalse(exception.providerFailure());
    }

    @Test
    void validatedClaimsRejectUnsupportedImportance() {
        assertThrows(IllegalArgumentException.class, () -> validatedClaims("""
            {"skills":[{"skillId":"SPRING_BOOT","importance":"OPTIONAL","evidence":"Spring Boot"}]}
            """, "Spring Boot"));
    }

    @Test
    void validatedClaimsRejectUnknownSkill() {
        assertThrows(IllegalArgumentException.class, () -> validatedClaims("""
            {"skills":[{"skillId":"KUBERNETES","importance":"REQUIRED","evidence":"Kubernetes"}]}
            """, "Kubernetes"));
    }

    @Test
    void validatedClaimsRejectDuplicateSkillClaims() {
        assertThrows(IllegalArgumentException.class, () -> validatedClaims("""
            {"skills":[
              {"skillId":"SPRING_BOOT","importance":"REQUIRED","evidence":"Spring Boot"},
              {"skillId":"SPRING_BOOT","importance":"PREFERRED","evidence":"Spring Boot"}
            ]}
            """, "Spring Boot"));
    }

    @Test
    void validatedClaimsRejectHallucinatedEvidence() {
        assertThrows(IllegalArgumentException.class, () -> validatedClaims("""
            {"skills":[{"skillId":"CORE_JAVA","importance":"REQUIRED","evidence":"Java 21"}]}
            """, "Built backend services with Java."));
    }

    @Test
    void validatedClaimsRejectEvidenceThatCrossesLineFeeds() {
        assertThrows(IllegalArgumentException.class, () -> validatedClaims("""
            {"skills":[{"skillId":"SPRING_BOOT","importance":"REQUIRED","evidence":"Spring\\nBoot"}]}
            """, "Spring\nBoot"));
    }

    @Test
    void validatedClaimsUseDocumentTypographyForMatchedEvidence() {
        List<SkillClaim> claims = validatedClaims("""
            {"skills":[{"skillId":"SPRING_BOOT","importance":"REQUIRED","evidence":"\\"Spring Boot\\" - Java services"}]}
            """, "Built “Spring Boot” — Java services for hiring workflows.");

        assertEquals(List.of(new SkillClaim("SPRING_BOOT", "REQUIRED", "“Spring Boot” — Java services", false)), claims);
    }

    @Test
    void validatedClaimsAllowsPdfWhitespaceDifferencesWithinOneLine() {
        List<SkillClaim> claims = validatedClaims("""
            {"skills":[{"skillId":"SPRING_BOOT","importance":"REQUIRED","evidence":"Spring   Boot"}]}
            """, "Spring Boot is used for APIs");
        assertEquals("SPRING_BOOT", claims.get(0).skillId());
    }

    @Test
    void validatedClaimsAllowsPdfExtractionPunctuationDifferences() {
        List<SkillClaim> claims = validatedClaims("""
            {"skills":[{"skillId":"CORE_JAVA","importance":"REQUIRED","evidence":"Java and Spring Boot"}]}
            """, "Java, Spring Boot, and REST APIs");
        assertEquals("CORE_JAVA", claims.get(0).skillId());
    }

    @Test
    void clipEvidenceCentersLongMatchesWithinTheLineBound() {
        String line = "a".repeat(220) + "quoted evidence" + "b".repeat(220);

        String clipped = invokeStatic("clipEvidence", new Class<?>[] {String.class, int.class, int.class}, line, 220, 235);

        assertEquals(300, clipped.codePointCount(0, clipped.length()));
        assertTrue(clipped.contains("quoted evidence"));
        assertTrue(clipped.indexOf("quoted evidence") > 100);
        assertTrue(clipped.indexOf("quoted evidence") < 200);
        assertFalse(clipped.equals(line.substring(0, line.offsetByCodePoints(0, 300))));
    }

    @Test
    void aggregateClaimsKeepsFirstVerifiedClaimWithItsImportanceAndEvidence() {
        List<SkillClaim> aggregated = invokeStatic(
            "aggregateClaims",
            new Class<?>[] {List.class},
            List.of(
                List.of(new SkillClaim("SQL_RELATIONAL", "PREFERRED", "PostgreSQL", false)),
                List.of(
                    new SkillClaim("CORE_JAVA", "REQUIRED", "Java", false),
                    new SkillClaim("SQL_RELATIONAL", "REQUIRED", "Advanced SQL tuning", false))
            ));

        assertEquals(
            List.of(
                new SkillClaim("SQL_RELATIONAL", "PREFERRED", "PostgreSQL", false),
                new SkillClaim("CORE_JAVA", "REQUIRED", "Java", false)),
            aggregated);
    }

    @Test
    void chunksKeepOversizeSingleLinesIntact() {
        String oversized = "x".repeat(4_001);

        List<String> chunks = invokeStatic("chunks", new Class<?>[] {String.class}, "alpha\n" + oversized + "\nomega");

        assertEquals(List.of("alpha", oversized, "omega"), chunks);
    }

    private static List<SkillClaim> validatedClaims(String json, String document) {
        JsonNode output = readJson(json);
        return invokeStatic("validatedClaims", new Class<?>[] {JsonNode.class, String.class}, output, document);
    }

    // -------------------------------------------------------------------------
    // chunks() — sliding window with overlap
    // -------------------------------------------------------------------------

    @Test
    void chunksSmallDocumentProducesSingleChunk() {
        String document = "Line 1\nLine 2\nLine 3";
        List<String> result = VertexSkillAnalyzer.chunks(document);
        assertEquals(1, result.size(), "A document smaller than the target size must yield a single chunk");
        assertEquals(document, result.get(0));
    }

    @Test
    void chunksNeverSplitsAcrossLineBoundary() {
        // Build a document where each line is ~100 chars — will require multiple chunks.
        String line = "x".repeat(100);
        String document = String.join("\n", java.util.Collections.nCopies(50, line));
        List<String> chunks = VertexSkillAnalyzer.chunks(document);
        assertTrue(chunks.size() > 1, "A large document must be split into multiple chunks");
        for (String chunk : chunks) {
            // Every line in every chunk must be an exact original line (no mid-line splits)
            for (String chunkLine : chunk.split("\n", -1)) {
                assertTrue(document.contains(chunkLine),
                    "Each line in a chunk must be an exact original line from the source document");
            }
        }
    }

    @Test
    void chunksOverlapPreservesTrailingLinesFromPreviousChunk() {
        // Build two chunks' worth of content. The second chunk must start with overlap
        // lines that also appeared at the tail of the first chunk.
        String line = "z".repeat(100); // 100 chars per line
        // 50 lines = 5000 chars — first chunk cut at ~4000; last ~3 lines become overlap
        String document = String.join("\n", java.util.Collections.nCopies(50, line));
        List<String> chunks = VertexSkillAnalyzer.chunks(document);
        assertTrue(chunks.size() >= 2, "A 5000-char document with 100-char lines must produce >=2 chunks");
        // The second chunk must share at least one line with the first chunk (the overlap)
        String[] firstLines  = chunks.get(0).split("\n", -1);
        String[] secondLines = chunks.get(1).split("\n", -1);
        String lastOfFirst  = firstLines[firstLines.length - 1];
        String firstOfSecond = secondLines[0];
        assertEquals(lastOfFirst, firstOfSecond,
            "The last line(s) of chunk N must appear as the first line(s) of chunk N+1 (overlap)");
    }

    @Test
    void chunksVeryLongSingleLineGetsItsOwnChunk() {
        String longLine   = "y".repeat(5000);
        String normalLine = "normal line";
        String document   = normalLine + "\n" + longLine + "\n" + normalLine;
        List<String> chunks = VertexSkillAnalyzer.chunks(document);
        boolean hasLongLineAlone = chunks.stream().anyMatch(c -> c.equals(longLine));
        assertTrue(hasLongLineAlone, "A line exceeding the target size must become its own chunk");
    }

    private static JsonNode readJson(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T invokeStatic(String methodName, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Method method = VertexSkillAnalyzer.class.getDeclaredMethod(methodName, parameterTypes);
            method.setAccessible(true);
            return (T) method.invoke(null, arguments);
        } catch (NoSuchMethodException exception) {
            fail("Missing package-private static helper VertexSkillAnalyzer." + methodName);
            return null;
        } catch (InvocationTargetException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            if (cause instanceof Error error) throw error;
            throw new AssertionError(cause);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }
}
