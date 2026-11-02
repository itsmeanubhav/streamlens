
package com.streamlens.service;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.streamlens.catalog.OperationCatalog;
import com.streamlens.catalog.OperationCategory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class PipelineExtractor {

    public record Result(List<ExtractedPipeline> pipelines, List<String> errors) {

        static Result ok(List<ExtractedPipeline> pipelines) {
            return new Result(pipelines, List.of());
        }

        static Result fail(String message) {
            return new Result(List.of(), List.of(message));
        }
    }

    public Result extract(CompilationUnit cu) {

		Optional<MethodDeclaration> method =
				cu.findAll(MethodDeclaration.class).stream()
						.filter(m -> m.getNameAsString().equals("main"))
						.findFirst();

		if (method.isEmpty()) {
			method = cu.findFirst(MethodDeclaration.class);
		}

		if (method.isEmpty() || method.get().getBody().isEmpty()) {
			return Result.fail("Could not find a method body to analyze.");
		}

        BlockStmt body = method.get().getBody().get();
        List<Statement> statements = body.getStatements();

        List<ExtractedPipeline> pipelines = new ArrayList<>();

        for (int i = 0; i < statements.size(); i++) {

            Statement stmt = statements.get(i);

            Optional<MethodCallExpr> candidate =
                    findPipelineCandidate(stmt);

            if (candidate.isEmpty()) {
                continue;
            }

            if (!containsTerminalOperation(candidate.get())) {
                continue;
            }

            Result built = tryBuildPipeline(
                    candidate.get(),
                    statements.subList(0, i)
            );

            if (!built.pipelines().isEmpty()) {
                pipelines.addAll(built.pipelines());
            } else if (!built.errors().isEmpty()) {
                return built;
            }
        }

        if (pipelines.isEmpty()) {
            return Result.fail(
                    "No supported Stream pipeline found. " +
                    "A pipeline must start with a named variable followed by .stream() " +
                    "and contain a supported terminal operation."
            );
        }

        return Result.ok(pipelines);
    }

    private Optional<MethodCallExpr> findPipelineCandidate(Statement stmt) {

        if (!stmt.isExpressionStmt()) {
            return Optional.empty();
        }

        Expression expr =
                ((ExpressionStmt) stmt).getExpression();

        if (expr.isVariableDeclarationExpr()) {

            for (VariableDeclarator vd :
                    expr.asVariableDeclarationExpr().getVariables()) {

                if (vd.getInitializer().isPresent()
                        && vd.getInitializer().get().isMethodCallExpr()) {

                    return Optional.of(
                            vd.getInitializer().get().asMethodCallExpr()
                    );
                }
            }

            return Optional.empty();
        }

        if (expr.isAssignExpr()
                && expr.asAssignExpr().getValue().isMethodCallExpr()) {

            return Optional.of(
                    expr.asAssignExpr().getValue().asMethodCallExpr()
            );
        }

        if (expr.isMethodCallExpr()) {
            return Optional.of(expr.asMethodCallExpr());
        }

        return Optional.empty();
    }

    private boolean containsTerminalOperation(MethodCallExpr candidate) {

        Expression cursor = candidate;

        while (cursor != null && cursor.isMethodCallExpr()) {

            MethodCallExpr call = cursor.asMethodCallExpr();
            String name = call.getNameAsString();

            if (OperationCatalog.isKnownOperation(name)
                    && OperationCatalog.lookup(name).category()
                    == OperationCategory.TERMINAL) {

                return true;
            }

            cursor = call.getScope().orElse(null);
        }

        return false;
    }

    private Result tryBuildPipeline(
            MethodCallExpr terminalCandidate,
            List<Statement> precedingStatements) {

        List<MethodCallExpr> chainReversed = new ArrayList<>();

        Expression cursor = terminalCandidate;

        while (cursor != null && cursor.isMethodCallExpr()) {

            MethodCallExpr call = cursor.asMethodCallExpr();

            chainReversed.add(call);

            cursor = call.getScope().orElse(null);
        }

        if (!(cursor instanceof NameExpr sourceNameExpr)) {

            return Result.fail(
                    "The Stream pipeline must start from a plain variable."
            );
        }

        List<MethodCallExpr> chain =
                new ArrayList<>(chainReversed);

        Collections.reverse(chain);

        if (chain.isEmpty()
                || !chain.get(0).getNameAsString().equals("stream")) {

            return Result.fail(
                    "Expected the pipeline to start with .stream() on a variable."
            );
        }

        List<MethodCallExpr> opsAfterStream =
                chain.subList(1, chain.size());

        if (opsAfterStream.isEmpty()) {
            return Result.fail(
                    "Found .stream() but no operations after it."
            );
        }

        int terminalIndex = -1;

        for (int i = 0; i < opsAfterStream.size(); i++) {

            MethodCallExpr call = opsAfterStream.get(i);
            String name = call.getNameAsString();

            if (OperationCatalog.isKnownOperation(name)
                    && OperationCatalog.lookup(name).category()
                    == OperationCategory.TERMINAL) {

                if (terminalIndex != -1) {
                    return Result.fail(
                            "Multiple terminal operations found in one Stream chain."
                    );
                }

                terminalIndex = i;
            }
        }

        if (terminalIndex == -1) {
            return Result.fail("No supported terminal operation found.");
        }

        MethodCallExpr terminalNode =
                opsAfterStream.get(terminalIndex);

        List<MethodCallExpr> intermediateNodes =
                opsAfterStream.subList(0, terminalIndex);

		// Only preserve valid calls chained after a Stream terminal operation.
		// Example: max().orElse(0) -> .orElse(0)
		List<String> supportedSuffixMethods = List.of(
				"orElse",
				"orElseGet",
				"orElseThrow",
				"get",
				"getAsInt",
				"getAsLong",
				"getAsDouble"
		);

		StringBuilder suffixBuilder = new StringBuilder();

		for (int i = terminalIndex + 1; i < opsAfterStream.size(); i++) {

			MethodCallExpr suffixCall = opsAfterStream.get(i);
			String suffixMethod = suffixCall.getNameAsString();

			if (!supportedSuffixMethods.contains(suffixMethod)) {
				return Result.fail(
						"`" + suffixMethod + "` chained after the terminal operation "
								+ "is not supported. Supported calls after a terminal operation are: "
								+ String.join(", ", supportedSuffixMethods) + "."
				);
			}

			suffixBuilder.append(".")
					.append(suffixCall.getNameAsString())
					.append("(")
					.append(suffixCall.getArguments().stream()
							.map(Node::toString)
							.collect(Collectors.joining(", ")))
					.append(")");
		}

		String terminalSuffix = suffixBuilder.toString();

        for (MethodCallExpr op : intermediateNodes) {

            String name = op.getNameAsString();

            if (!OperationCatalog.isKnownOperation(name)
                    || OperationCatalog.lookup(name).category()
                    != OperationCategory.INTERMEDIATE) {

                return Result.fail(
                        "`" + name + "` is not a supported intermediate operation."
                );
            }
        }

        List<OperationCall> intermediateOps =
                intermediateNodes.stream()
                        .map(this::toOperationCall)
                        .collect(Collectors.toList());

        OperationCall terminalOp =
                toOperationCall(terminalNode);

        List<String> precedingSource =
                precedingStatements.stream()
                        .map(Node::toString)
                        .collect(Collectors.toList());

        int sourceLine =
                chain.get(0).getName().getBegin()
                        .map(position -> position.line)
                        .orElse(-1);

        ExtractedPipeline pipeline = new ExtractedPipeline(
                sourceNameExpr.getNameAsString(),
                sourceLine,
                precedingSource,
                intermediateOps,
                terminalOp,
                terminalSuffix
        );

        return Result.ok(List.of(pipeline));
    }

    private OperationCall toOperationCall(MethodCallExpr call) {

        String args = call.getArguments().stream()
                .map(Node::toString)
                .collect(Collectors.joining(", "));

        int line = call.getName().getBegin()
                .map(position -> position.line)
                .orElse(-1);

        return new OperationCall(
                call.getNameAsString(),
                args,
                line
        );
    }
}
