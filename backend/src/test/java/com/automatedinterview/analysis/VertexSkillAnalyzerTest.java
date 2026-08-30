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
