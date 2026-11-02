package com.streamlens.service;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds a runnable Java source file that re-executes the extracted pipeline once per
 * prefix (source, after op 1, after op 1+2, ...) so each stage can be printed as a real,
 * materialized list, plus once more with the real terminal operation for the final result.
 *
 * Re-running prefixes instead of using peek() is deliberate: peek() hands you one element
 * at a time as it flows through, which doesn't give a clean "snapshot of the whole list
 * after this stage" when later operations reorder or drop elements. Re-running each prefix
 * is slightly wasteful computationally but trivial for snippet-sized data and far simpler
 * to get right, which matters more for a day-1 MVP.
 */

@Component
public class CodeGenerator {

    private static final String STAGE_PREFIX = "STAGE_";
    private static final String FINAL_MARKER = "STAGE_FINAL:";

    public String generate(ExtractedPipeline pipeline) {

        StringBuilder src = new StringBuilder();

        src.append("import java.util.*;\n");
        src.append("import java.util.stream.*;\n");
        src.append("import java.util.function.*;\n\n");

        src.append("public class Main {\n");

        // Helper methods to capture normal and primitive streams
        src.append("    private static <T> List<T> snapshot(Stream<T> stream) {\n");
        src.append("        return stream.collect(Collectors.toList());\n");
        src.append("    }\n\n");

        src.append("    private static List<Integer> snapshot(IntStream stream) {\n");
        src.append("        return stream.boxed().collect(Collectors.toList());\n");
        src.append("    }\n\n");

        src.append("    private static List<Long> snapshot(LongStream stream) {\n");
        src.append("        return stream.boxed().collect(Collectors.toList());\n");
        src.append("    }\n\n");

        src.append("    private static List<Double> snapshot(DoubleStream stream) {\n");
        src.append("        return stream.boxed().collect(Collectors.toList());\n");
        src.append("    }\n\n");

        src.append("    public static void main(String[] args) throws Exception {\n");

        for (String stmt : pipeline.precedingStatementsSource()) {
            src.append("        ").append(stmt).append("\n");
        }

        String source = pipeline.sourceVarName();

        src.append("        System.out.println(\"")
                .append(STAGE_PREFIX)
                .append("0:\" + ")
                .append(source)
                .append(");\n");

        StringBuilder cumulative =
                new StringBuilder(source).append(".stream()");

        List<OperationCall> intermediateOps =
                pipeline.intermediateOps();

        for (int i = 0; i < intermediateOps.size(); i++) {

            cumulative.append(
                    intermediateOps.get(i).toSourceFragment()
            );

            String stageVar = "__stage_" + (i + 1);

            src.append("        Object ")
                    .append(stageVar)
                    .append(" = snapshot(")
                    .append(cumulative)
                    .append(");\n");

            src.append("        System.out.println(\"")
                    .append(STAGE_PREFIX)
                    .append(i + 1)
                    .append(":\" + ")
                    .append(stageVar)
                    .append(");\n");
        }

        // Build the actual terminal pipeline
        StringBuilder finalChain =
                new StringBuilder(source).append(".stream()");

        for (OperationCall op : intermediateOps) {
            finalChain.append(op.toSourceFragment());
        }

        finalChain.append(
                pipeline.terminalOp().toSourceFragment()
        );

        // Apply calls chained after the Stream terminal, e.g. Optional.orElse(0).
        finalChain.append(pipeline.terminalSuffix());

        if (pipeline.terminalOp().name().equals("forEach")) {

            src.append("        ")
                    .append(finalChain)
                    .append(";\n");

            src.append("        System.out.println(\"")
                    .append(FINAL_MARKER)
                    .append("(no return value)\");\n");

        } else {

            src.append("        Object __stage_final = ")
                    .append(finalChain)
                    .append(";\n");

            src.append("        System.out.println(\"")
                    .append(FINAL_MARKER)
                    .append("\" + __stage_final);\n");
        }

        src.append("    }\n");
        src.append("}\n");

        return src.toString();
    }
}
