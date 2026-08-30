package com.automatedinterview.questionbank;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import com.automatedinterview.catalog.SkillCatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.automatedinterview.config.QuestionLimitsProperties;

@RestController
@RequestMapping("/api/v1/question-bank")
public class QuestionBankController {
    private final QuestionBankRepository repository;
    private final QuestionImportService imports;
    private final SkillCatalogService catalog;
    private final QuestionLimitsProperties.QuestionBank limits;
    private final ObjectMapper json;

    public QuestionBankController(QuestionBankRepository repository, QuestionImportService imports, SkillCatalogService catalog, QuestionLimitsProperties properties, ObjectMapper json) { this.repository = repository; this.imports = imports; this.catalog = catalog; this.limits = properties.questionBank(); this.json = json; }

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
    public QuestionBankResponse list(@RequestParam(defaultValue = "0") int page, @RequestParam(required = false) Integer size,
                                    @RequestParam(required = false) String search, @RequestParam(required = false) String skill,
                                    @RequestParam(required = false) String difficulty, @RequestParam(required = false) String origin) {
        if (page < 0) throw new IllegalArgumentException("page must be non-negative");
        int pageSize = size == null ? limits.pageSize() : Math.min(size, limits.maxPageSize());
        if (pageSize < 1) throw new IllegalArgumentException("size must be positive");
        var filter = new QuestionBankRepository.Filter(search, skill, difficulty, origin);
        long total = repository.countQuestions(filter);
        List<QuestionSummary> questions = repository.listQuestions(filter, pageSize, paginationOffset(page, pageSize));
        List<CoverageBucket> coverage = repository.coverage();
        long active = repository.countActiveQuestions(filter);
        long skillAreaCount = repository.countSkillAreas(filter);
        List<SkillOption> skills = catalog.activeSkills().stream().map(option -> new SkillOption(option.id(), option.displayName(), option.aliases())).toList();
        return new QuestionBankResponse(questions, total, active, Math.max(skillAreaCount, skills.size()), coverage, skills,
            page, pageSize, (int) Math.ceil(total / (double) pageSize));
    }

    public static long paginationOffset(int page, int pageSize) {
        if (page < 0 || pageSize < 1) throw new IllegalArgumentException("Invalid pagination values");
        try {
            long offset = Math.multiplyExact((long) page, pageSize);
            if (offset > Integer.MAX_VALUE) throw new IllegalArgumentException("Pagination offset is too large");
            return offset;
        }
        catch (ArithmeticException exception) { throw new IllegalArgumentException("Pagination offset is too large", exception); }
    }

    @GetMapping("/export")
    public ResponseEntity<String> export(@RequestParam(defaultValue = "json") String format, @RequestParam(required = false) String search,
                                         @RequestParam(required = false) String skill, @RequestParam(required = false) String difficulty,
                                         @RequestParam(required = false) String origin) throws Exception {
        List<QuestionSummary> rows = repository.listQuestions(new QuestionBankRepository.Filter(search, skill, difficulty, origin), limits.maxTotalQuestions());
        if ("csv".equalsIgnoreCase(format)) {
            StringBuilder csv = new StringBuilder("Question,Skill,Difficulty,Origin,Status\n");
            for (QuestionSummary row : rows) csv.append(csv(row.stem())).append(',').append(csv(row.primarySkill())).append(',')
                .append(csv(row.difficulty())).append(',').append(csv(row.origin())).append(',').append(csv(row.status())).append('\n');
            return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv")).body(csv.toString());
        }
        return ResponseEntity.ok(json.writeValueAsString(rows));
    }

    private static String csv(String value) { return "\"" + String.valueOf(value == null ? "" : value).replace("\"", "\"\"") + "\""; }

    public record QuestionBankResponse(List<QuestionSummary> questions, long total, long activeCount, long skillAreaCount, List<CoverageBucket> coverage, List<SkillOption> skills, int page, int size, int totalPages) { }
    public record SkillOption(String id, String displayName, List<String> aliases) { }
    public record QuestionSummary(UUID id, String stem, String origin, String status, String type, String primarySkill, String secondarySkills, String difficulty, String tags, String rubric, String idealAnswer, Instant updatedAt) { }
    public record CoverageBucket(String type, String primarySkill, String difficulty, String status, long count) { }
    public record StatusRequest(String status) { }
}
