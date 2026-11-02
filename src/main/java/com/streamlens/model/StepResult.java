package com.streamlens.model;

import java.util.List;

public record StepResult(
        int index,
        String operation,              // e.g. "filter", "map", "collect"
        String category,                // "SOURCE" | "INTERMEDIATE" | "TERMINAL"
        boolean lazy,
        Integer sourceLine,             // 1-based line number in the snippet as the user typed it
                                         // (null if unavailable). Frontend uses this to highlight
                                         // the matching line in the code panel for this step.
        String generalDescription,      // reused text from the OperationCatalog
        String specificDescription,     // built from the actual lambda/method ref in this snippet
        List<String> input,             // real values entering this step (verified by sandbox run)
        List<String> output,            // real values leaving this step (null for terminal steps)
        String finalResult,             // set only on the terminal step (e.g. "3", "true", "[ALICE, DAVE]")
        StepDiff diff,
        String friendlyExplanation      // optional: Gemini's plain-English rewrite of the verified facts above.
                                         // Null if AI explanation wasn't requested or the call failed -
                                         // the rest of the step is still fully correct without it.
) {
}
