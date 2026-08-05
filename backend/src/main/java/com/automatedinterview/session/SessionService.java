package com.automatedinterview.session;

import com.automatedinterview.analysis.SkillClaim;
import com.automatedinterview.analysis.VertexSkillAnalyzer;
import com.automatedinterview.document.DocumentNormalizer;
import com.automatedinterview.document.DocumentTextExtractor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class SessionService {
    private static final Logger log = LoggerFactory.getLogger(SessionService.class);
    private final DocumentTextExtractor extractor;
    private final VertexSkillAnalyzer analyzer;
    private final JdbcClient jdbc;

    public SessionService(DocumentTextExtractor extractor, VertexSkillAnalyzer analyzer, JdbcClient jdbc) {
        this.extractor = extractor;
        this.analyzer = analyzer;
        this.jdbc = jdbc;
    }

    @Transactional
    public CreatedSession create(MultipartFile jobFile, MultipartFile resumeFile, int yearsExperience, boolean attested) {
        if (yearsExperience < 0 || yearsExperience > 30) throw new SessionInputException("INVALID_EXPERIENCE");
        if (!attested) throw new SessionInputException("ATTESTATION_REQUIRED");
        try {
            String job = DocumentNormalizer.normalize(extractor.extract(jobFile));
            String resume = DocumentNormalizer.normalize(extractor.extract(resumeFile));
            if (job.isBlank()) throw new SessionInputException("INVALID_DOCUMENT");
            List<SkillClaim> jobClaims = analyzer.analyze("job description", job);
            if (jobClaims.isEmpty()) throw new SessionInputException("NO_SUPPORTED_SKILLS");
            List<SkillClaim> resumeClaims = resume.isBlank() ? List.of() : analyzer.analyze("resume", resume);
            Set<String> jobSkills = ids(jobClaims);
            Set<String> resumeSkills = ids(resumeClaims);
            List<String> matched = jobSkills.stream().filter(resumeSkills::contains).toList();
            List<String> missing = jobSkills.stream().filter(skill -> !resumeSkills.contains(skill)).toList();
            double totalWeight = jobClaims.stream().mapToDouble(claim -> weight(claim.importance())).sum();
            double matchedWeight = jobClaims.stream().filter(claim -> resumeSkills.contains(claim.skillId()))
                .mapToDouble(claim -> weight(claim.importance())).sum();
            double profileMatch = totalWeight == 0 ? 0 : Math.round((matchedWeight / totalWeight) * 1000) / 10.0;
            String difficulty = yearsExperience <= 2 ? "EASY" : yearsExperience <= 6 ? "MEDIUM" : "HARD";
            UUID id = UUID.randomUUID();
            String token = UUID.randomUUID().toString() + UUID.randomUUID();
            Instant expiresAt = Instant.now().plus(Duration.ofHours(2));
            jdbc.sql("""
                INSERT INTO interview_session (id, token_hash, state, years_experience, difficulty, profile_match, expires_at)
                VALUES (:id, :tokenHash, 'READY', :years, :difficulty, :profileMatch, :expiresAt)
                """).param("id", id).param("tokenHash", hash(token)).param("years", yearsExperience)
                .param("difficulty", difficulty).param("profileMatch", profileMatch)
                .param("expiresAt", java.sql.Timestamp.from(expiresAt)).update();
            saveClaims(id, "JOB", jobClaims, resumeSkills);
            saveClaims(id, "RESUME", resumeClaims, jobSkills);
            return new CreatedSession(new SessionResponse(id, expiresAt, difficulty, profileMatch, jobClaims, resumeClaims, matched, missing), token);
        } catch (SessionInputException | VertexSkillAnalyzer.SkillProviderException exception) {
            throw exception;
        } catch (IllegalArgumentException exception) {
            String code = Set.of("DOCUMENT_LIMIT_EXCEEDED", "UNSUPPORTED_DOCUMENT").contains(exception.getMessage()) ? exception.getMessage() : "INVALID_DOCUMENT";
            throw new SessionInputException(code);
        } catch (Exception exception) {
            log.error("Session creation failed before a session could be committed", exception);
            throw new SessionInputException("INVALID_DOCUMENT");
        }
    }

    public SessionResponse snapshot(UUID id, String token) {
        if (token == null || token.isBlank()) throw new SessionInputException("INVALID_SESSION_TOKEN");
        SessionRow row = jdbc.sql("SELECT id, state, difficulty, profile_match, expires_at FROM interview_session WHERE id = :id AND token_hash = :tokenHash")
            .param("id", id).param("tokenHash", hash(token)).query((rs, n) -> new SessionRow(
                rs.getObject("id", UUID.class), rs.getString("difficulty"), rs.getDouble("profile_match"), rs.getTimestamp("expires_at").toInstant())).optional()
            .orElseThrow(() -> new SessionInputException("SESSION_NOT_FOUND"));
        if (!row.expiresAt().isAfter(Instant.now())) throw new SessionInputException("SESSION_EXPIRED");
        List<SkillClaim> job = claims(id, "JOB");
        List<SkillClaim> resume = claims(id, "RESUME");
        Set<String> resumeIds = ids(resume);
        List<String> matched = job.stream().map(SkillClaim::skillId).filter(resumeIds::contains).distinct().toList();
        List<String> missing = job.stream().map(SkillClaim::skillId).filter(skill -> !resumeIds.contains(skill)).distinct().toList();
        return new SessionResponse(id, row.expiresAt(), row.difficulty(), row.profileMatch(), job, resume, matched, missing);
    }

    private List<SkillClaim> claims(UUID id, String documentType) {
        return jdbc.sql("SELECT skill_id, importance, evidence, matched FROM session_skill WHERE session_id = :id AND document_type = :documentType ORDER BY skill_id")
            .param("id", id).param("documentType", documentType)
            .query((rs, n) -> new SkillClaim(rs.getString("skill_id"), rs.getString("importance"), rs.getString("evidence"), rs.getBoolean("matched"))).list();
    }

    private void saveClaims(UUID sessionId, String documentType, List<SkillClaim> claims, Set<String> otherSkills) {
        for (SkillClaim claim : claims) {
            boolean matched = otherSkills.contains(claim.skillId());
            jdbc.sql("""
                INSERT INTO session_skill (session_id, document_type, skill_id, importance, matched, evidence)
                VALUES (:sessionId, :documentType, :skillId, :importance, :matched, :evidence)
                """).param("sessionId", sessionId).param("documentType", documentType).param("skillId", claim.skillId())
                .param("importance", claim.importance()).param("matched", matched).param("evidence", claim.evidence()).update();
        }
    }

    private Set<String> ids(List<SkillClaim> claims) {
        return new HashSet<>(claims.stream().map(SkillClaim::skillId).toList());
    }

    private double weight(String importance) {
        return switch (importance) { case "REQUIRED" -> 1.0; case "PREFERRED" -> 0.6; default -> 0.0; };
    }

    public static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte item : digest) result.append("%02x".formatted(item));
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    public record CreatedSession(SessionResponse response, String token) { }
    private record SessionRow(UUID id, String difficulty, double profileMatch, Instant expiresAt) { }

    public static class SessionInputException extends RuntimeException {
        private final String code;
        public SessionInputException(String code) { this.code = code; }
        public String code() { return code; }
    }
}
