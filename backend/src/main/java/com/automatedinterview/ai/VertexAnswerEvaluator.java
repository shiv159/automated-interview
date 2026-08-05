package com.automatedinterview.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class VertexAnswerEvaluator {
    private static final Logger log = LoggerFactory.getLogger(VertexAnswerEvaluator.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final String model;
    private final ChatModel springAiModel;
    private final ObservationRegistry observationRegistry;

    public VertexAnswerEvaluator(@Value("${VERTEX_CHAT_MODEL:gemini-2.5-flash-lite}") String model,
        ObjectProvider<ChatModel> springAiModel, ObservationRegistry observationRegistry) {
        this.model = model;
        this.springAiModel = springAiModel.getIfAvailable();
        this.observationRegistry = observationRegistry;
    }

    public Result evaluate(String stem, String criteria, String idealAnswer, String answer) {
        if (springAiModel == null) throw new ProviderUnavailable();
        Observation observation = Observation.createNotStarted("automated-interview.answer.evaluation", observationRegistry)
            .lowCardinalityKeyValue("question.type", stem.startsWith("Tell me") ? "behavioral" : "other")
            .lowCardinalityKeyValue("model", model);
        try {
            return observation.observe(() -> evaluateInternal(stem, criteria, idealAnswer, answer));
        } catch (ProviderUnavailable exception) {
            observation.error(exception);
            log.warn("answer_evaluation_failed model={} stemLength={} criteriaLength={} answerLength={} reason={}",
                model, stem.length(), criteria.length(), answer.length(), exception.getMessage());
            throw exception;
        }
    }

    private Result evaluateInternal(String stem, String criteria, String idealAnswer, String answer) {
        try {
            String prompt = "Evaluate the candidate answer against the question and ideal answer. Return only JSON: {\"score\": number 0..10, \"strengths\": [1..3 strings], \"improvements\": [1..3 strings]}. Question: " + stem + " Criteria: " + criteria + " Ideal answer: " + idealAnswer + " Candidate answer: " + answer;
            JsonNode value = mapper.readTree(springAiModel.call(new Prompt(prompt)).getResult().getOutput().getText());
            double score = value.path("score").asDouble(-1);
            if (score < 0 || score > 10 || !value.path("strengths").isArray() || !value.path("improvements").isArray()) throw new ProviderUnavailable("invalid response shape");
            List<String> strengths = new ArrayList<>(); value.path("strengths").forEach(item -> strengths.add(item.asText()));
            List<String> improvements = new ArrayList<>(); value.path("improvements").forEach(item -> improvements.add(item.asText()));
            if (strengths.isEmpty() || strengths.size() > 3 || improvements.isEmpty() || improvements.size() > 3)
                throw new ProviderUnavailable("invalid response cardinality strengths=" + strengths.size() + " improvements=" + improvements.size());
            return new Result(Math.round(score * 10) / 10.0, strengths, improvements);
        } catch (ProviderUnavailable exception) { throw exception; }
        catch (Exception exception) { throw new ProviderUnavailable(); }
    }

    public String model() { return model; }

    public record Result(double score, List<String> strengths, List<String> improvements) { }
    public static class ProviderUnavailable extends RuntimeException {
        public ProviderUnavailable() { }
        public ProviderUnavailable(String message) { super(message); }
    }
}
