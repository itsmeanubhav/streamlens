package com.streamlens.web;

import com.streamlens.model.AnalysisResult;
import com.streamlens.service.GeminiExplanationService;
import com.streamlens.service.StreamAnalysisService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AnalyzeController {

    private final StreamAnalysisService analysisService;
    private final GeminiExplanationService geminiExplanationService;

    public AnalyzeController(StreamAnalysisService analysisService,
                              GeminiExplanationService geminiExplanationService) {
        this.analysisService = analysisService;
        this.geminiExplanationService = geminiExplanationService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<AnalysisResult> analyze(@RequestBody AnalyzeRequest request) {
		if (request.code() == null || request.code().isBlank()) {
			return ResponseEntity.badRequest()
					.body(AnalysisResult.invalid(java.util.List.of("No code provided.")));
		}
		if (request.code().length() > 10_000) {
			return ResponseEntity.badRequest()
					.body(AnalysisResult.invalid(java.util.List.of(
							"Snippet is too large. Please keep it under 10,000 characters.")));
		}
        AnalysisResult verified = analysisService.analyze(request.code());
        AnalysisResult enriched = geminiExplanationService.enrich(verified);
        return ResponseEntity.ok(enriched);
    }
}
