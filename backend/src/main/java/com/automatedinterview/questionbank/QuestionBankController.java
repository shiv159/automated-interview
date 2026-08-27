package com.automatedinterview.questionbank;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.automatedinterview.catalog.SkillCatalog;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/question-bank")
public class QuestionBankController {
    private final JdbcClient jdbc;
    private final QuestionImportService imports;

    public QuestionBankController(JdbcClient jdbc, QuestionImportService imports) { this.jdbc = jdbc; this.imports = imports; }

    @PostMapping("/import")
    public ResponseEntity<QuestionImportService.ImportResponse> importQuestions(@RequestParam MultipartFile questionsFile) {
        QuestionImportService.ImportResponse response = imports.importFile(questionsFile);
        return ResponseEntity.status(response.createdCount() > 0 ? HttpStatus.CREATED : HttpStatus.OK).body(response);
    }

    @PatchMapping("/questions/{id}/status")
    public ResponseEntity<Void> status(@PathVariable UUID id, @RequestBody StatusRequest request) {
        imports.deactivate(id, request.status()); return ResponseEntity.noContent().build();
    }

    @GetMapping
    public QuestionBankResponse list() {
        List<QuestionSummary> questions = jdbc.sql("""
            SELECT id, stem, origin, status, type, primary_skill, difficulty, tags, rubric, ideal_answer, updated_at
            FROM question ORDER BY origin, type, primary_skill NULLS LAST, difficulty NULLS LAST, id
            """).query((rs, row) -> new QuestionSummary(rs.getObject("id", UUID.class), rs.getString("stem"), rs.getString("origin"),
                rs.getString("status"), rs.getString("type"), rs.getString("primary_skill"), rs.getString("difficulty"), rs.getString("tags"),
                rs.getString("rubric"), rs.getString("ideal_answer"), rs.getTimestamp("updated_at").toInstant())).list();
        List<CoverageBucket> coverage = jdbc.sql("""
            SELECT type, primary_skill, difficulty, status, count(*) AS total
            FROM question GROUP BY type, primary_skill, difficulty, status
            ORDER BY type, primary_skill NULLS LAST, difficulty NULLS LAST, status
            """).query((rs, row) -> new CoverageBucket(rs.getString("type"), rs.getString("primary_skill"), rs.getString("difficulty"), rs.getString("status"), rs.getLong("total"))).list();
        long active = questions.stream().filter(item -> item.status().equals("ACTIVE")).count();
        long skillAreaCount = questions.stream().map(item -> item.primarySkill() == null ? "BEHAVIORAL" : item.primarySkill()).distinct().count();
        List<SkillOption> skills = SkillCatalog.activeSkills().stream().map(skill -> new SkillOption(skill.id(), skill.displayName(), skill.aliases())).toList();
        return new QuestionBankResponse(questions, questions.size(), active, Math.max(skillAreaCount, skills.size()), coverage, skills);
    }

    public record QuestionBankResponse(List<QuestionSummary> questions, int total, long activeCount, long skillAreaCount, List<CoverageBucket> coverage, List<SkillOption> skills) { }
    public record SkillOption(String id, String displayName, List<String> aliases) { }
    public record QuestionSummary(UUID id, String stem, String origin, String status, String type, String primarySkill, String difficulty, String tags, String rubric, String idealAnswer, Instant updatedAt) { }
    public record CoverageBucket(String type, String primarySkill, String difficulty, String status, long count) { }
    public record StatusRequest(String status) { }
}
