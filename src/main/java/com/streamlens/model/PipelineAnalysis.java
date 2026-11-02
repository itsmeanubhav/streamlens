package com.streamlens.model;

import java.util.List;

public record PipelineAnalysis(
        int pipelineIndex,
        PipelineSummary pipelineSummary,
        List<StepResult> steps
) {
}
