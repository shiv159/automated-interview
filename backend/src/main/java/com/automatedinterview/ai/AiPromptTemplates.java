package com.automatedinterview.ai;

import java.util.Map;
import org.springframework.ai.chat.prompt.PromptTemplate;
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
    public String enrichment(String stem, String type, String skill) { return new PromptTemplate(enrichment).render(Map.of("stem", safe(stem), "deterministicType", type, "deterministicSkill", skill == null ? "null" : skill)); }
    public String evaluation(String stem, String criteria, String idealAnswer, String answer) { return new PromptTemplate(evaluation).render(Map.of("stem", safe(stem), "criteria", safe(criteria), "idealAnswer", safe(idealAnswer), "answer", safe(answer))); }
    public String skillAnalysis(String documentType, String skills, String document) { return new PromptTemplate(skillAnalysis).render(Map.of("documentType", safe(documentType), "skills", safe(skills), "document", safe(document))); }
    private static String safe(String value) { return value == null ? "" : value; }
}
