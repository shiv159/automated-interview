package com.automatedinterview.questionbank;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.automatedinterview.catalog.SkillCatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/question-bank")
public class QuestionBankController {
    private final QuestionBankRepository repository;
    private final QuestionImportService imports;
    private final SkillCatalogService catalog;

    public QuestionBankController(QuestionBankRepository repository, QuestionImportService imports, SkillCatalogService catalog) { this.repository = repository; this.imports = imports; this.catalog = catalog; }

    @PostMapping("/import")
    public ResponseEntity<QuestionImportService.ImportResponse> importQuestions(@RequestParam MultipartFile questionsFile) {
        QuestionImportService.ImportResponse response = imports.importFile(questionsFile);
        return ResponseEntity.status(response.createdCount() > 0 ? HttpStatus.CREATED : HttpStatus.OK).body(response);
    }

    @PostMapping("/analyze")
    public QuestionImportService.AnalysisResponse analyzeQuestions(@RequestParam MultipartFile questionsFile) {
        return imports.analyzeFile(questionsFile);
    }

    @PostMapping("/import-draft")
    public ResponseEntity<QuestionImportService.ImportResponse> importDraft(@RequestBody QuestionImportService.DraftImportRequest request) {
        QuestionImportService.ImportResponse response = imports.importDraft(request);
        return ResponseEntity.status(response.createdCount() > 0 ? HttpStatus.CREATED : HttpStatus.OK).body(response);
    }

    @PatchMapping("/questions/{id}/status")
    public ResponseEntity<Void> status(@PathVariable UUID id, @RequestBody StatusRequest request) {
        imports.deactivate(id, request.status()); return ResponseEntity.noContent().build();
    }

    @GetMapping
    public QuestionBankResponse list() {
        List<QuestionSummary> questions = repository.listQuestions();
        List<CoverageBucket> coverage = repository.coverage();
        long active = questions.stream().filter(item -> item.status().equals("ACTIVE")).count();
        long skillAreaCount = questions.stream().map(item -> item.primarySkill() == null ? "BEHAVIORAL" : item.primarySkill()).distinct().count();
        List<SkillOption> skills = catalog.activeSkills().stream().map(skill -> new SkillOption(skill.id(), skill.displayName(), skill.aliases())).toList();
        return new QuestionBankResponse(questions, questions.size(), active, Math.max(skillAreaCount, skills.size()), coverage, skills);
    }

    public record QuestionBankResponse(List<QuestionSummary> questions, int total, long activeCount, long skillAreaCount, List<CoverageBucket> coverage, List<SkillOption> skills) { }
    public record SkillOption(String id, String displayName, List<String> aliases) { }
    public record QuestionSummary(UUID id, String stem, String origin, String status, String type, String primarySkill, String secondarySkills, String difficulty, String tags, String rubric, String idealAnswer, Instant updatedAt) { }
    public record CoverageBucket(String type, String primarySkill, String difficulty, String status, long count) { }
    public record StatusRequest(String status) { }
}
