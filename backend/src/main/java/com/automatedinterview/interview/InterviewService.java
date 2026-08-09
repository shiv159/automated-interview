package com.automatedinterview.interview;

import com.automatedinterview.session.SessionService;
import com.automatedinterview.ai.VertexAnswerEvaluator;
import com.automatedinterview.ai.VertexEmbeddingService;
import com.automatedinterview.questionbank.LocalEmbedding;
import com.automatedinterview.document.DocumentNormalizer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterviewService {
    private static final Pattern WORDS = Pattern.compile("[^\\p{L}\\p{Nd}]+");
    private final JdbcClient jdbc;
    private final VertexAnswerEvaluator vertexEvaluator;
    private final String evaluationProfile;
    private final VertexEmbeddingService embeddings;
    private final String embeddingProfile;

    public InterviewService(JdbcClient jdbc, VertexAnswerEvaluator vertexEvaluator, VertexEmbeddingService embeddings,
        @org.springframework.beans.factory.annotation.Value("${APP_ANSWER_EVALUATION_PROFILE:ai}") String evaluationProfile,
        @org.springframework.beans.factory.annotation.Value("${APP_EMBEDDING_PROFILE:local}") String embeddingProfile) {
        this.jdbc = jdbc; this.vertexEvaluator = vertexEvaluator; this.embeddings = embeddings;
        this.evaluationProfile = evaluationProfile; this.embeddingProfile = embeddingProfile;
    }

    @Transactional
    public QuestionResponse start(UUID sessionId, String token) {
        SessionRow session = session(sessionId, token);
        if (session.state().equals("INTERVIEWING")) return current(sessionId, token);
        if (!session.state().equals("READY")) throw new InterviewException("INVALID_SESSION_STATE", 409);
        List<TargetSkill> targets = jdbc.sql("""
            SELECT ss.skill_id, s.display_name, ss.importance, ss.matched, ss.evidence AS job_evidence,
                   COALESCE(resume.evidence, '') AS resume_evidence
            FROM session_skill ss JOIN skill s ON s.id = ss.skill_id
            LEFT JOIN session_skill resume ON resume.session_id = ss.session_id
                AND resume.document_type = 'RESUME' AND resume.skill_id = ss.skill_id
            WHERE ss.session_id = :sessionId AND ss.document_type = 'JOB'
            ORDER BY ss.matched ASC,
                CASE ss.importance WHEN 'REQUIRED' THEN 1 WHEN 'PREFERRED' THEN 2 ELSE 3 END,
                ss.skill_id
            """).param("sessionId", sessionId).query((rs, row) -> new TargetSkill(rs.getString("skill_id"), rs.getString("display_name"),
                rs.getString("importance"), rs.getBoolean("matched"), rs.getString("job_evidence"), rs.getString("resume_evidence"))).list();
        if (targets.isEmpty()) throw new InterviewException("QUESTION_BANK_UNAVAILABLE", 503);
        List<QuestionRow> technical = new ArrayList<>();
        Set<UUID> used = new HashSet<>();
        for (int position = 1; position <= 2; position++) {
            QuestionRow selected = null;
            for (int offset = 0; offset < targets.size(); offset++) {
                TargetSkill target = targets.get((position - 1 + offset) % targets.size());
                String queryText = queryText(target, session.difficulty());
                String queryEmbedding;
                try { queryEmbedding = embeddingProfile.equals("ai") ? embeddings.embedQuery(queryText) : LocalEmbedding.vector(queryText); }
                catch (VertexEmbeddingService.ProviderUnavailable exception) { throw new InterviewException("EMBEDDING_UNAVAILABLE", 503); }
                selected = jdbc.sql("""
                    SELECT q.id, q.stem, q.type, q.primary_skill, q.difficulty, q.rubric, q.ideal_answer, q.content_hash
                    FROM question q JOIN question_embedding qe ON qe.question_id = q.id
                    WHERE q.type = 'TECHNICAL' AND q.status = 'ACTIVE' AND q.primary_skill = :skill
                      AND q.difficulty = :difficulty AND (CAST(:excludeId AS uuid) IS NULL OR q.id <> CAST(:excludeId AS uuid))
                    ORDER BY qe.embedding <=> CAST(:queryEmbedding AS vector), lower(q.id::text)
                    LIMIT 1
                    """).param("skill", target.skillId()).param("difficulty", session.difficulty()).param("excludeId", used.isEmpty() ? null : used.iterator().next())
                    .param("queryEmbedding", queryEmbedding).query(this::question).optional().orElse(null);
                if (selected != null) break;
            }
            if (selected == null) throw new InterviewException("QUESTION_BANK_UNAVAILABLE", 503);
            technical.add(selected); used.add(selected.id());
        }
        String behavioralEmbedding;
        try { behavioralEmbedding = embeddingProfile.equals("ai") ? embeddings.embed("behavioral communication teamwork problem solving reflection") : LocalEmbedding.vector("behavioral communication teamwork problem solving reflection"); }
        catch (VertexEmbeddingService.ProviderUnavailable exception) { throw new InterviewException("EMBEDDING_UNAVAILABLE", 503); }
        QuestionRow behavioral = jdbc.sql("""
            SELECT q.id, q.stem, q.type, q.primary_skill, q.difficulty, q.rubric, q.ideal_answer, q.content_hash
            FROM question q JOIN question_embedding qe ON qe.question_id = q.id
            WHERE q.type = 'BEHAVIORAL' AND q.status = 'ACTIVE'
            ORDER BY qe.embedding <=> CAST(:queryEmbedding AS vector), lower(q.id::text) LIMIT 1
            """).param("queryEmbedding", behavioralEmbedding).query(this::question).optional()
            .orElseThrow(() -> new InterviewException("QUESTION_BANK_UNAVAILABLE", 503));
        List<QuestionRow> questions = new ArrayList<>(technical);
        questions.add(behavioral);
        for (int index = 0; index < questions.size(); index++) {
            QuestionRow question = questions.get(index);
            jdbc.sql("""
                INSERT INTO session_question (id, session_id, question_id, position, status, type, primary_skill, difficulty, stem, criteria, ideal_answer, source_hash)
                VALUES (:id, :sessionId, :questionId, :position, :status, :type, :skill, :difficulty, :stem, CAST(:criteria AS jsonb), :ideal, :hash)
                """).param("id", UUID.randomUUID()).param("sessionId", sessionId).param("questionId", question.id())
                .param("position", index + 1).param("status", index == 0 ? "ACTIVE" : "LOCKED").param("type", question.type())
                .param("skill", question.skill()).param("difficulty", question.difficulty()).param("stem", question.stem())
                .param("criteria", question.rubric()).param("ideal", question.idealAnswer()).param("hash", question.hash()).update();
        }
        jdbc.sql("UPDATE interview_session SET state = 'INTERVIEWING' WHERE id = :id AND state = 'READY'").param("id", sessionId).update();
        return current(sessionId, token);
    }

    private String queryText(TargetSkill target, String difficulty) {
        return List.of(target.displayName(), difficulty, target.jobEvidence(), target.resumeEvidence()).stream()
            .map(value -> DocumentNormalizer.normalize(value == null ? "" : value)).reduce((left, right) -> left + "\n" + right).orElse("");
    }

    @Transactional
    public AnswerResponse answer(UUID sessionId, UUID instanceId, String token, String answer) {
        session(sessionId, token);
        String normalized = normalizeAnswer(answer);
        QuestionRow question = jdbc.sql("""
            UPDATE session_question SET status = 'EVALUATING'
            WHERE id = :instanceId AND session_id = :sessionId AND status = 'ACTIVE'
            RETURNING id, position, type, primary_skill, difficulty, stem, criteria, ideal_answer, source_hash
            """).param("instanceId", instanceId).param("sessionId", sessionId).query(this::sessionQuestion).optional()
            .orElseThrow(() -> answerConflict(sessionId, instanceId));
        Evaluation evaluation;
        String adapter;
        String model;
        if (evaluationProfile.equals("ai")) {
            try {
                VertexAnswerEvaluator.Result result = vertexEvaluator.evaluate(question.stem(), question.rubric(), question.idealAnswer(), normalized);
                evaluation = new Evaluation(result.score(), result.strengths(), result.improvements(), "[\"score\"]", json(result.strengths()), json(result.improvements()));
                adapter = "vertex";
                model = vertexEvaluator.model();
            } catch (VertexAnswerEvaluator.ProviderUnavailable exception) {
                jdbc.sql("UPDATE session_question SET status = 'ACTIVE' WHERE id = :id").param("id", question.id()).update();
                throw new InterviewException("EVALUATION_UNAVAILABLE", 503);
            }
        } else throw new IllegalStateException("APP_ANSWER_EVALUATION_PROFILE must be ai");
        jdbc.sql("""
            INSERT INTO evaluation (id, session_question_id, criteria_scores, strengths, improvements, score, adapter, model)
            VALUES (:id, :questionId, CAST(:criteria AS jsonb), CAST(:strengths AS jsonb), CAST(:improvements AS jsonb), :score, :adapter, :model)
            """).param("id", UUID.randomUUID()).param("questionId", question.id()).param("criteria", evaluation.criteriaJson())
            .param("strengths", evaluation.strengthsJson()).param("improvements", evaluation.improvementsJson()).param("score", evaluation.score())
            .param("adapter", adapter).param("model", model).update();
        jdbc.sql("UPDATE session_question SET status = 'EVALUATED', accepted_at = now() WHERE id = :id").param("id", question.id()).update();
        if (question.position() < 3) {
            jdbc.sql("UPDATE session_question SET status = 'ACTIVE' WHERE session_id = :sessionId AND position = :position AND status = 'LOCKED'")
                .param("sessionId", sessionId).param("position", question.position() + 1).update();
        } else {
            jdbc.sql("UPDATE interview_session SET state = 'REPORT_READY' WHERE id = :sessionId").param("sessionId", sessionId).update();
        }
        return new AnswerResponse(question.position(), evaluation.score(), evaluation.strengths(), evaluation.improvements(),
            question.position() < 3 ? current(sessionId, token) : null);
    }

    private InterviewException answerConflict(UUID sessionId, UUID instanceId) {
        String status = jdbc.sql("SELECT status FROM session_question WHERE id = :id AND session_id = :sessionId")
            .param("id", instanceId).param("sessionId", sessionId).query(String.class).optional().orElse("MISSING");
        return new InterviewException(status.equals("EVALUATING") ? "ANSWER_EVALUATION_IN_PROGRESS" : "ANSWER_ALREADY_ACCEPTED", 409);
    }

    public QuestionResponse current(UUID sessionId, String token) {
        session(sessionId, token);
        return jdbc.sql("""
            SELECT id, position, type, primary_skill, difficulty, stem, criteria, ideal_answer, source_hash
            FROM session_question WHERE session_id = :sessionId AND status = 'ACTIVE'
            ORDER BY position LIMIT 1
            """).param("sessionId", sessionId).query(this::sessionQuestion).optional()
            .map(question -> new QuestionResponse(question.id(), question.position(), question.type(), question.skill(), question.difficulty(), question.stem(), question.rubric()))
            .orElseThrow(() -> new InterviewException("REPORT_NOT_READY", 409));
    }

    public ReportResponse report(UUID sessionId, String token) {
        SessionRow session = session(sessionId, token);
        if (!session.state().equals("REPORT_READY")) throw new InterviewException("REPORT_NOT_READY", 409);
        List<EvaluatedQuestion> evaluations = jdbc.sql("""
            SELECT sq.position, sq.type, sq.primary_skill, sq.stem, e.score, e.strengths, e.improvements
            FROM session_question sq JOIN evaluation e ON e.session_question_id = sq.id
            WHERE sq.session_id = :sessionId ORDER BY sq.position
            """).param("sessionId", sessionId).query((rs, row) -> new EvaluatedQuestion(rs.getInt("position"), rs.getString("type"), rs.getString("primary_skill"), rs.getString("stem"), rs.getDouble("score"), rs.getString("strengths"), rs.getString("improvements"))).list();
        double technical = evaluations.stream().filter(item -> item.type().equals("TECHNICAL")).mapToDouble(EvaluatedQuestion::score).average().orElse(0) * 10;
        double behavioral = evaluations.stream().filter(item -> item.type().equals("BEHAVIORAL")).mapToDouble(EvaluatedQuestion::score).findFirst().orElse(0) * 10;
        double interview = technical * .8 + behavioral * .2;
        double readiness = session.profileMatch() * .3 + interview * .7;
        String label = readiness >= 80 ? "Ready" : readiness >= 65 ? "Nearly Ready" : readiness >= 50 ? "Developing" : "Significant Gaps";
        return new ReportResponse(sessionId, evaluations, session.profileMatch(), technical, behavioral, interview, readiness, label, Instant.now().plusSeconds(7200));
    }

    private SessionRow session(UUID id, String token) {
        if (token == null || token.isBlank()) throw new InterviewException("INVALID_SESSION_TOKEN", 401);
        return jdbc.sql("SELECT id, state, difficulty, profile_match, expires_at FROM interview_session WHERE id = :id AND token_hash = :tokenHash")
            .param("id", id).param("tokenHash", SessionService.hash(token)).query((rs, row) -> new SessionRow(rs.getObject("id", UUID.class), rs.getString("state"), rs.getString("difficulty"), rs.getDouble("profile_match"), rs.getTimestamp("expires_at").toInstant())).optional()
            .filter(item -> item.expiresAt().isAfter(Instant.now())).orElseThrow(() -> new InterviewException("SESSION_EXPIRED", 410));
    }

    private QuestionRow question(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new QuestionRow(rs.getObject("id", UUID.class), rs.getString("stem"), rs.getString("type"), rs.getString("primary_skill"), rs.getString("difficulty"), rs.getString("rubric"), rs.getString("ideal_answer"), rs.getString("content_hash"), 0);
    }

    private QuestionRow sessionQuestion(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
        return new QuestionRow(rs.getObject("id", UUID.class), rs.getString("stem"), rs.getString("type"), rs.getString("primary_skill"), rs.getString("difficulty"), rs.getString("criteria"), rs.getString("ideal_answer"), rs.getString("source_hash"), rs.getInt("position"));
    }

    private String normalizeAnswer(String value) {
        if (value == null) throw new InterviewException("INVALID_ANSWER", 400);
        String normalized = value.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.indexOf('\0') >= 0 || normalized.codePoints().anyMatch(character -> Character.isISOControl(character) && character != '\n' && character != '\t'))
            throw new InterviewException("INVALID_ANSWER", 400);
        normalized = java.text.Normalizer.normalize(normalized, java.text.Normalizer.Form.NFC)
            .replaceAll("(?m)[^\\S\\n]+", " ").replaceAll("(?m)^ +| +$", "").strip();
        if (normalized.isBlank() || normalized.codePointCount(0, normalized.length()) > 4000) throw new InterviewException("INVALID_ANSWER", 400);
        return normalized;
    }

    private String json(List<String> values) { return "[" + values.stream().map(value -> "\"" + value.replace("\"", "\\\"") + "\"").reduce((a, b) -> a + "," + b).orElse("") + "]"; }

    private record SessionRow(UUID id, String state, String difficulty, double profileMatch, Instant expiresAt) { }
    private record TargetSkill(String skillId, String displayName, String importance, boolean matched, String jobEvidence, String resumeEvidence) { }
    private record QuestionRow(UUID id, String stem, String type, String skill, String difficulty, String rubric, String idealAnswer, String hash, int position) { }
    private record Evaluation(double score, List<String> strengths, List<String> improvements, String criteriaJson, String strengthsJson, String improvementsJson) { }
    public record QuestionResponse(UUID instanceId, int position, String type, String primarySkill, String difficulty, String stem, String criteria) { }
    public record AnswerResponse(int position, double score, List<String> strengths, List<String> improvements, QuestionResponse nextQuestion) { }
    public record EvaluatedQuestion(int position, String type, String primarySkill, String stem, double score, String strengths, String improvements) { }
    public record ReportResponse(UUID sessionId, List<EvaluatedQuestion> evaluations, double profileMatch, double technicalScore, double behavioralScore, double interviewScore, double readinessScore, String readinessLabel, Instant expiresAt) { }
    public static class InterviewException extends RuntimeException { private final String code; private final int status; public InterviewException(String code, int status) { this.code = code; this.status = status; } public String code() { return code; } public int status() { return status; } }
}
