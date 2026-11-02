package com.streamlens.service;

import java.util.List;

/**
 * Everything needed to regenerate and trace a single Stream pipeline:
 * the variable it starts from, the declarations that must run first to set that
 * variable up, the ordered intermediate operations, and the terminal operation.
 */
public record ExtractedPipeline(
        String sourceVarName,
        int sourceLine,                            // line of the `.stream()` call itself
        List<String> precedingStatementsSource,   // verbatim source text of statements before the pipeline
        List<OperationCall> intermediateOps,       // in source order, source (.stream()) excluded
        OperationCall terminalOp,
		String terminalSuffix
) {
}
