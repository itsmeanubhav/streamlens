package com.streamlens.model;

public record PipelineSummary(
        int intermediateOpCount,
        String terminalOp,
        String note
) {
}
