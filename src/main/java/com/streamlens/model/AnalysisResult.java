
package com.streamlens.model;

import java.util.List;

public record AnalysisResult(
        boolean valid,
        List<String> errors,
        PipelineSummary pipelineSummary,
        List<StepResult> steps,
        String overallExplanation,
        List<PipelineAnalysis> pipelines
) {

    // Backward-compatible constructor for existing five-argument callers.
    public AnalysisResult(
            boolean valid,
            List<String> errors,
            PipelineSummary pipelineSummary,
            List<StepResult> steps,
            String overallExplanation) {

        this(
                valid,
                errors,
                pipelineSummary,
                steps,
                overallExplanation,
                List.of()
        );
    }

    public static AnalysisResult invalid(List<String> errors) {
        return new AnalysisResult(
                false,
                errors,
                null,
                List.of(),
                null,
                List.of()
        );
    }

    public AnalysisResult withAiExplanations(
            String overallExplanation,
            List<StepResult> stepsWithAi) {

        // Step indexes are unique across all pipelines.
        java.util.Map<Integer, StepResult> stepsByIndex =
                new java.util.HashMap<>();

        for (StepResult step : stepsWithAi) {
            stepsByIndex.put(step.index(), step);
        }

        List<PipelineAnalysis> updatedPipelines =
                pipelines.stream()
                        .map(pipeline -> {

                            List<StepResult> updatedSteps =
                                    pipeline.steps().stream()
                                            .map(step -> stepsByIndex.getOrDefault(
                                                    step.index(),
                                                    step
                                            ))
                                            .toList();

                            return new PipelineAnalysis(
                                    pipeline.pipelineIndex(),
                                    pipeline.pipelineSummary(),
                                    updatedSteps
                            );
                        })
                        .toList();

        return new AnalysisResult(
                valid,
                errors,
                pipelineSummary,
                stepsWithAi,
                overallExplanation,
                updatedPipelines
        );
    }
}
