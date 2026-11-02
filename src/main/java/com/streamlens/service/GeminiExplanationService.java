
package com.streamlens.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.streamlens.model.AnalysisResult;
import com.streamlens.model.StepResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GeminiExplanationService {

    private static final Logger log =
            LoggerFactory.getLogger(GeminiExplanationService.class);

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.model}")
    private String model;

    @Value("${gemini.explanation.enabled}")
    private boolean enabled;

    public AnalysisResult enrich(AnalysisResult verified) {

        if (!enabled
                || apiKey == null
                || apiKey.isBlank()
                || !verified.valid()) {

            return verified;
        }

        try {
            String prompt = buildPrompt(verified);
            String rawResponse = callGemini(prompt);

            return mergeIntoResult(verified, rawResponse);

        } catch (Exception e) {
            log.warn("Gemini explanation skipped: {}", e.getMessage());
            return verified;
        }
    }

    private String buildPrompt(AnalysisResult verified) throws Exception {

        String verifiedJson = mapper.writeValueAsString(verified);

        return """
                You are explaining Java 8 Stream API code to a complete beginner.

                The input JSON can contain one or multiple independent Stream pipelines.
                Each pipeline has its own steps. Step indexes are unique across the complete
                response.

                The values shown in the JSON were produced by actually compiling and running
                the Java code. They are verified facts.

                Your job is ONLY to explain these verified facts in simple, friendly English.

                Rules:
                1. Do not invent or change any data value.
                2. Do not add operations that are not listed.
                3. Explain each pipeline separately in the overall explanation.
                4. Explain every step index present in the input.
                5. Do not speculate about code that is not represented in the input.
                6. Keep explanations beginner-friendly and concise.

                Verified analysis data (JSON):
                %s

                Respond with ONLY raw JSON, without markdown fences, in exactly this shape:

                {
                  "overallExplanation": "A short explanation of what the pipelines do.",
                  "stepExplanations": {
                    "0": "Explanation for step index 0",
                    "1": "Explanation for step index 1"
                  }
                }

                Include one stepExplanations entry for every step index in the input.
                """.formatted(verifiedJson);
    }

    private String callGemini(String prompt) throws Exception {

        String url =
                "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s"
                        .formatted(model, apiKey);

        Map<String, Object> body = Map.of(
                "contents", List.of(
                        Map.of(
                                "parts",
                                List.of(Map.of("text", prompt))
                        )
                ),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "responseMimeType", "application/json"
                )
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        mapper.writeValueAsString(body)
                ))
                .build();

        HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );

        if (response.statusCode() != 200) {
            throw new RuntimeException(
                    "Gemini call failed: HTTP "
                            + response.statusCode()
                            + " - "
                            + response.body()
            );
        }

        JsonNode root = mapper.readTree(response.body());

        return root.path("candidates")
                .path(0)
                .path("content")
                .path("parts")
                .path(0)
                .path("text")
                .asText();
    }

    private AnalysisResult mergeIntoResult(
            AnalysisResult verified,
            String geminiJsonText) throws Exception {

        JsonNode parsed = mapper.readTree(geminiJsonText);

        String overall =
                parsed.path("overallExplanation").asText(null);

        JsonNode stepExplanations =
                parsed.path("stepExplanations");

        Map<Integer, String> byIndex = new HashMap<>();

        stepExplanations.fields().forEachRemaining(entry -> {
            try {
                byIndex.put(
                        Integer.parseInt(entry.getKey()),
                        entry.getValue().asText(null)
                );
            } catch (NumberFormatException ignored) {
                log.warn(
                        "Ignoring invalid Gemini step index: {}",
                        entry.getKey()
                );
            }
        });

        List<StepResult> merged = new ArrayList<>();

        for (StepResult step : verified.steps()) {

            String friendly = byIndex.get(step.index());

            merged.add(new StepResult(
                    step.index(),
                    step.operation(),
                    step.category(),
                    step.lazy(),
                    step.sourceLine(),
                    step.generalDescription(),
                    step.specificDescription(),
                    step.input(),
                    step.output(),
                    step.finalResult(),
                    step.diff(),
                    friendly
            ));
        }

        return verified.withAiExplanations(overall, merged);
    }
}
