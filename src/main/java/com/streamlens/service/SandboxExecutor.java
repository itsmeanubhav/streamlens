package com.streamlens.service;

import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Compiles and runs a generated Java source file in its own OS process - never in-process
 * via reflection/eval. This is the isolation boundary: a separate process, a short timeout,
 * a capped heap, and no network calls in the generated code to begin with.
 *
 * This is "good enough" isolation for a free-tier hobby portfolio demo with low traffic,
 * not a hardened multi-tenant sandbox. A production version would add OS-level sandboxing
 * (seccomp, a disposable container per run, no filesystem access outside the temp dir) -
 * worth a line in the README as a known trade-off, not something to pretend isn't there.
 */
@Component
public class SandboxExecutor {

    // Caps how many compile+run cycles can happen at once, so a burst of visitors on a
    // free-tier 512MB host can't spin up enough JVMs to OOM the box.
    private static final Semaphore CONCURRENCY_LIMIT = new Semaphore(2);

    private static final long COMPILE_TIMEOUT_SECONDS = 10;
    private static final long RUN_TIMEOUT_SECONDS = 5;

    // Resolve javac/java from the JDK that's running this app, rather than relying on
    // PATH in the subprocess environment, which some hosting buildpacks don't set up
    // the same way for spawned processes as for the main app.
    private static final String JAVA_HOME = System.getProperty("java.home");
    private static final String JAVAC_BIN = JAVA_HOME + File.separator + "bin" + File.separator + "javac";
    private static final String JAVA_BIN = JAVA_HOME + File.separator + "bin" + File.separator + "java";

    // Minimal defense-in-depth: the pipeline itself can only call known Stream operations,
    // but "preceding statements" (variable declarations) are copied near-verbatim from the
    // user's snippet, so block anything that smells like I/O, process control, reflection,
    // or system access before even attempting to compile.
    private static final List<String> BLOCKED_SUBSTRINGS = List.of(
            "System.exit", "Runtime.", "ProcessBuilder", "java.io.File", "java.nio.file",
            "Files.", "Socket", "URLConnection", "Class.forName", "new Thread", "Thread.",
            "System.getenv", "System.setProperty", "sun.misc.Unsafe", ".reflect.", ".exec(",
            "import java.io", "import java.net", "import java.nio"
    );

    public record ExecutionResult(boolean success, List<String> stdoutLines, String errorMessage) {
    }

    public ExecutionResult compileAndRun(String generatedSource) {
        for (String blocked : BLOCKED_SUBSTRINGS) {
            if (generatedSource.contains(blocked)) {
                return new ExecutionResult(false, List.of(),
                        "Snippet references an API that isn't allowed in this sandbox (" + blocked.trim() + ").");
            }
        }

        boolean acquired = false;
        try {
            acquired = CONCURRENCY_LIMIT.tryAcquire(5, TimeUnit.SECONDS);
            if (!acquired) {
                return new ExecutionResult(false, List.of(), "Server is busy analyzing other snippets, try again shortly.");
            }
            return doCompileAndRun(generatedSource);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ExecutionResult(false, List.of(), "Analysis was interrupted.");
        } finally {
            if (acquired) {
                CONCURRENCY_LIMIT.release();
            }
        }
    }

    private ExecutionResult doCompileAndRun(String generatedSource) {
        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("streamlens-sandbox-");
            Path sourceFile = tempDir.resolve("Main.java");
            Files.writeString(sourceFile, generatedSource, StandardCharsets.UTF_8);

            Process compile = new ProcessBuilder(JAVAC_BIN, "Main.java")
                    .directory(tempDir.toFile())
                    .redirectErrorStream(true)
                    .start();
            String compileOutput = readAll(compile.getInputStream());
            boolean compileFinished = compile.waitFor(COMPILE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!compileFinished) {
                compile.destroyForcibly();
                return new ExecutionResult(false, List.of(), "Compilation timed out.");
            }
            if (compile.exitValue() != 0) {
                return new ExecutionResult(false, List.of(), "Compile error:\n" + compileOutput);
            }

            ProcessBuilder runPb = new ProcessBuilder(
                    JAVA_BIN, "-Xmx64m", "-XX:TieredStopAtLevel=1", "-cp", ".", "Main")
                    .directory(tempDir.toFile())
                    .redirectErrorStream(true);
            Process run = runPb.start();
            String runOutput = readAll(run.getInputStream());
            boolean runFinished = run.waitFor(RUN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!runFinished) {
                run.destroyForcibly();
                return new ExecutionResult(false, List.of(), "Execution timed out (possible infinite loop).");
            }
            if (run.exitValue() != 0) {
                return new ExecutionResult(false, List.of(), "Runtime error:\n" + runOutput);
            }

            List<String> lines = runOutput.lines().toList();
            return new ExecutionResult(true, lines, null);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return new ExecutionResult(false, List.of(), "Sandbox error: " + e.getMessage());
        } finally {
            cleanupQuietly(tempDir);
        }
    }

    private void cleanupQuietly(Path dir) {
        if (dir == null) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            walk.sorted((a, b) -> b.compareTo(a)).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    private String readAll(java.io.InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
}
