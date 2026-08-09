package com.automatedinterview.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
public class AiPromptTemplates {
    private final Resource enrichment;
    private final Resource evaluation;
    private final Resource skillAnalysis;
    public AiPromptTemplates(@Value("classpath:/prompts/question-enrichment.st") Resource enrichment,
        @Value("classpath:/prompts/answer-evaluation.st") Resource evaluation,
        @Value("classpath:/prompts/skill-analysis.st") Resource skillAnalysis) {
        this.enrichment = enrichment; this.evaluation = evaluation; this.skillAnalysis = skillAnalysis;
    }
    public String enrichment(String stem, String type, String skill) { return render(enrichment, Map.of("stem", safe(stem), "deterministicType", safe(type), "deterministicSkill", skill == null ? "null" : skill)); }
    public String evaluation(String stem, String criteria, String idealAnswer, String answer) { return render(evaluation, Map.of("stem", safe(stem), "criteria", safe(criteria), "idealAnswer", safe(idealAnswer), "answer", safe(answer))); }
    public String skillAnalysis(String documentType, String skills, String document) { return render(skillAnalysis, Map.of("documentType", safe(documentType), "skills", safe(skills), "document", safe(document))); }
    private static String render(Resource resource, Map<String, String> values) {
        try {
            String template = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            for (var entry : values.entrySet()) template = template.replace("{" + entry.getKey() + "}", entry.getValue());
            return template;
        } catch (IOException exception) { throw new IllegalStateException("Unable to load AI prompt template", exception); }
    }
    private static String safe(String value) { return value == null ? "" : value; }
}
