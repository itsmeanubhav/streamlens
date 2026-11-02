package com.streamlens.service;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.Problem;
import com.github.javaparser.ast.CompilationUnit;
import com.streamlens.catalog.OperationCatalog;
import com.streamlens.catalog.OperationInfo;
import com.streamlens.model.AnalysisResult;
import com.streamlens.model.ElementChange;
import com.streamlens.model.PipelineSummary;
import com.streamlens.model.StepDiff;
import com.streamlens.model.StepResult;
import com.streamlens.model.PipelineAnalysis;


import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Orchestrates the whole verified-analysis pipeline:
 * parse -> extract the Stream chain -> generate instrumented source -> run it in the
 * sandbox -> turn the real printed output into a structured, UI-ready result.
 * The Gemini enrichment step (plain-English prose on top of these verified facts) is
 * applied by the caller (the controller), so this class stays pure/deterministic and
 * easy to unit test without any network dependency.
 */

@Service
public class StreamAnalysisService {

    private final PipelineExtractor extractor;
    private final CodeGenerator codeGenerator;
    private final SandboxExecutor sandboxExecutor;

    public StreamAnalysisService(
            PipelineExtractor extractor,
            CodeGenerator codeGenerator,
            SandboxExecutor sandboxExecutor) {

        this.extractor = extractor;
        this.codeGenerator = codeGenerator;
        this.sandboxExecutor = sandboxExecutor;
    }

    public AnalysisResult analyze(String rawCode) {

        ParserConfiguration cfg = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_8);

        JavaParser parser = new JavaParser(cfg);

        ParseResult<CompilationUnit> parseResult = parser.parse(rawCode);
        boolean wrapped = false;

        if (!parseResult.isSuccessful()) {
            parseResult = parser.parse(wrap(rawCode));
            wrapped = true;
        }

        if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {

            List<String> errors = parseResult.getProblems().stream()
                    .map(Problem::getMessage)
                    .collect(Collectors.toList());

            if (errors.isEmpty()) {
                errors = List.of("Could not parse this snippet as Java.");
            }

            return AnalysisResult.invalid(errors);
        }

        PipelineExtractor.Result extraction =
                extractor.extract(parseResult.getResult().get());

        if (extraction.pipelines().isEmpty()) {
            return AnalysisResult.invalid(extraction.errors());
        }

        int lineOffset = wrapped ? -1 : 0;
        int nextStepIndex = 0;

        List<PipelineAnalysis> pipelineAnalyses = new ArrayList<>();
        List<StepResult> allSteps = new ArrayList<>();

        for (int i = 0; i < extraction.pipelines().size(); i++) {

            ExtractedPipeline pipeline = extraction.pipelines().get(i);

            String generatedSource = codeGenerator.generate(pipeline);

            SandboxExecutor.ExecutionResult execution =
                    sandboxExecutor.compileAndRun(generatedSource);

            if (!execution.success()) {
                return AnalysisResult.invalid(List.of(
                        "Pipeline " + (i + 1) + " failed: "
                                + execution.errorMessage()
                ));
            }

            PipelineBuild build = buildResult(
                    i + 1,
                    pipeline,
                    execution.stdoutLines(),
                    lineOffset,
                    nextStepIndex
            );

            pipelineAnalyses.add(build.pipelineAnalysis());
            allSteps.addAll(build.steps());

            nextStepIndex = build.nextStepIndex();
        }

        PipelineSummary primarySummary =
                pipelineAnalyses.get(0).pipelineSummary();

        return new AnalysisResult(
                true,
                List.of(),
                primarySummary,
                allSteps,
                null,
                pipelineAnalyses
        );
    }

    private String wrap(String code) {
        return "class SnippetHolder { void run() {\n"
                + code
                + "\n} }";
    }

    private PipelineBuild buildResult(
            int pipelineNumber,
            ExtractedPipeline pipeline,
            List<String> stdoutLines,
            int lineOffset,
            int startingStepIndex) {

        Map<String, String> stageRaw = new HashMap<>();

        for (String line : stdoutLines) {

            int colon = line.indexOf(':');

            if (colon < 0 || !line.startsWith("STAGE_")) {
                continue;
            }

            String key = line.substring("STAGE_".length(), colon);
            stageRaw.put(key, line.substring(colon + 1));
        }

        List<StepResult> steps = new ArrayList<>();
        int index = startingStepIndex;

        List<String> sourceOutput =
                parseListLiteral(stageRaw.get("0"));

        steps.add(new StepResult(
                index++,
                "source",
                "SOURCE",
                false,
                pipeline.sourceLine() + lineOffset,
                "This is the original collection before the Stream pipeline begins. "
                        + "The stream() call creates a Stream view so the operations can process these values.",
                "Original source variable: " + pipeline.sourceVarName(),
                null,
                sourceOutput,
                null,
                null,
                null
        ));

        List<String> previousOutput = sourceOutput;

        List<OperationCall> intermediateOps =
                pipeline.intermediateOps();

        for (int i = 0; i < intermediateOps.size(); i++) {

            OperationCall op = intermediateOps.get(i);

            OperationInfo info = OperationCatalog.lookup(op.name());

            List<String> stageOutput =
                    parseListLiteral(stageRaw.get(String.valueOf(i + 1)));

            steps.add(new StepResult(
                    index++,
                    op.name(),
                    "INTERMEDIATE",
                    true,
                    op.line() + lineOffset,
                    info.generalDescription(),
                    buildSpecificDescription(op),
                    previousOutput,
                    stageOutput,
                    null,
                    computeDiff(op.name(), previousOutput, stageOutput),
                    null
            ));

            previousOutput = stageOutput;
        }

        OperationCall terminalOp = pipeline.terminalOp();

        OperationInfo terminalInfo =
                OperationCatalog.lookup(terminalOp.name());

        String finalRaw =
                stageRaw.getOrDefault("FINAL", "").trim();

        steps.add(new StepResult(
                index++,
                terminalOp.name(),
                "TERMINAL",
                false,
                terminalOp.line() + lineOffset,
                terminalInfo.generalDescription(),
                buildTerminalDescription(terminalOp, pipeline.terminalSuffix()),
                previousOutput,
                null,
                finalRaw,
                null,
                null
        ));

        String opNames = intermediateOps.stream()
                .map(OperationCall::name)
                .collect(Collectors.joining(", "));

        String note = intermediateOps.isEmpty()
                ? "There are no intermediate steps. "
                    + terminalOp.name()
                    + "() runs directly on the source."
                : "This pipeline has "
                    + intermediateOps.size()
                    + " intermediate step(s): "
                    + opNames
                    + ". The terminal operation triggers their execution.";

        PipelineSummary summary = new PipelineSummary(
                intermediateOps.size(),
                terminalOp.name(),
                note
        );

        PipelineAnalysis analysis = new PipelineAnalysis(
                pipelineNumber,
                summary,
                steps
        );

        return new PipelineBuild(analysis, steps, index);
    }

    private record PipelineBuild(
            PipelineAnalysis pipelineAnalysis,
            List<StepResult> steps,
            int nextStepIndex) {
    }

    private StepDiff computeDiff(
            String opName,
            List<String> input,
            List<String> output) {

        return switch (opName) {

            case "filter" -> {

                List<String> remainingOutput =
                        new ArrayList<>(output);

                List<String> removed = new ArrayList<>();

                for (String element : input) {
                    if (!remainingOutput.remove(element)) {
                        removed.add(element);
                    }
                }

                yield new StepDiff(
                        removed,
                        List.of(),
                        output.size() == input.size(),
                        "Elements failing the condition were dropped; "
                                + "everything else was kept."
                );
            }

            case "map" -> {

                List<ElementChange> changed = new ArrayList<>();

                if (input.size() == output.size()) {
                    for (int i = 0; i < input.size(); i++) {
                        changed.add(new ElementChange(
                                input.get(i),
                                output.get(i)
                        ));
                    }
                }

                yield new StepDiff(
                        List.of(),
                        changed,
                        true,
                        "Every element was transformed."
                );
            }

            case "sorted" -> new StepDiff(
                    List.of(),
                    List.of(),
                    input.size() == output.size(),
                    "Same elements, reordered."
            );

            case "distinct" -> new StepDiff(
                    List.of(),
                    List.of(),
                    input.size() == output.size(),
                    "Duplicate values were removed."
            );

            case "limit" -> new StepDiff(
                    List.of(),
                    List.of(),
                    input.size() == output.size(),
                    "Stream was limited to the requested number of elements."
            );

            case "skip" -> new StepDiff(
                    List.of(),
                    List.of(),
                    input.size() == output.size(),
                    "The first requested elements were skipped."
            );

            case "flatMap" -> new StepDiff(
                    List.of(),
                    List.of(),
                    input.size() == output.size(),
                    "Nested streams were flattened into one stream."
            );

            default -> new StepDiff(
                    List.of(),
                    List.of(),
                    input.size() == output.size(),
                    "See the general description for this operation."
            );
        };
    }

    private String buildSpecificDescription(OperationCall op) {

        String args = op.argsText();

        return switch (op.name()) {

            case "filter" ->
                    "Here, the test is: " + args
                            + " — only matching items move forward.";

            case "map" ->
                    "Each item is transformed using: " + args;

            case "mapToInt" ->
                    "Each item is converted to a primitive int using this mapping logic: " + args
                            + ". The result is an IntStream, which supports numeric operations such as max(), min() and sum().";

            case "mapToLong" ->
                    "Each item is converted to a long using: " + args;

            case "mapToDouble" ->
                    "Each item is converted to a double using: " + args;

            case "boxed" ->
                    "Primitive values are converted back into wrapper objects.";

            case "flatMap" ->
                    "Each item is expanded using: " + args;

            case "sorted" ->
                    args.isBlank()
                            ? "Items are sorted in their natural order."
                            : "Items are sorted using: " + args;

            case "distinct" ->
                    "Duplicate items are removed.";

            case "limit" ->
                    "Only the first " + args + " items are kept.";

            case "skip" ->
                    "The first " + args + " items are skipped.";

            case "peek" ->
                    "This runs " + args
                            + " on each item without changing the data.";

            case "collect" ->
                    "The final items are gathered using: " + args;

            case "forEach" ->
                    "This runs " + args + " on every final item.";

            case "reduce" ->
                    "All items are combined using: " + args;

            case "count" ->
                    "Counts the number of items.";

            case "anyMatch", "allMatch", "noneMatch" ->
                    "The condition being checked is: " + args;

            case "findFirst", "findAny" ->
                    "Finds an item from the stream.";

            case "min", "max" ->
                    "Compares items using: " + args;

            default ->
                    "Arguments: " + args;
        };
    }

    private String buildTerminalDescription(OperationCall op, String terminalSuffix) {
        String args = op.argsText();
        String suffix = terminalSuffix == null ? "" : terminalSuffix;

        if ("collect".equals(op.name()) && args.contains("toSet")) {
            return "collect(" + args + ") runs the stream and gathers the resulting elements into a Set. "
                    + "A Set keeps unique elements; its iteration order is not guaranteed.";
        }
        if ("max".equals(op.name())) {
            return "max() compares the int values produced by the previous step and returns the largest one "
                    + "inside an OptionalInt."
                    + (suffix.contains("orElse")
                        ? " Then " + suffix + " supplies 0 if the stream is empty; otherwise it returns the maximum."
                        : "");
        }
        return buildSpecificDescription(op);
    }

    private List<String> parseListLiteral(String raw) {

        if (raw == null) {
            return List.of();
        }

        String trimmed = raw.trim();

        if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
            return List.of(trimmed);
        }

        String inner =
                trimmed.substring(1, trimmed.length() - 1).trim();

        if (inner.isEmpty()) {
            return List.of();
        }

        return List.of(inner.split(", "));
    }
}
