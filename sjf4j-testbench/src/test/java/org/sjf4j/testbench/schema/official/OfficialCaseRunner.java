package org.sjf4j.testbench.schema.official;

import org.sjf4j.CompiledInstances;
import org.sjf4j.processor.CodegenProcessor;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Compiles generated official schema bindings through the production annotation processor.
 *
 * <p>Each official group has its own interface. A file-sized javac batch is attempted
 * first; on failure it is bisected until only the offending groups remain. This
 * keeps unsupported schemas visible without preventing other official tests
 * from compiling or executing.
 */
final class OfficialCaseRunner implements AutoCloseable {
    private static final String PACKAGE = "org.sjf4j.testbench.schema.official.generated";
    private final Path generatedRoot;
    private final Path workRoot;
    private final JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
    private final Map<String, Outcome> outcomes = new LinkedHashMap<>();
    private final Map<String, String> directFailures = new LinkedHashMap<>();
    private final List<URLClassLoader> classLoaders = new ArrayList<>();
    private int batchSerial;

    OfficialCaseRunner(Path generatedRoot, Path workRoot) {
        this.generatedRoot = generatedRoot;
        this.workRoot = workRoot;
    }

    static final class Spec {
        final String file;
        final int group;
        final String className;
        final int tests;
        final boolean fallback;

        Spec(String file, int group, String className, int tests, boolean fallback) {
            this.file = file;
            this.group = group;
            this.className = className;
            this.tests = tests;
            this.fallback = fallback;
        }

        String key() {
            return file + "#" + group;
        }
    }

    @FunctionalInterface
    private interface Invoker {
        boolean validate(Object value) throws Exception;
    }

    private static final class Outcome {
        final Invoker invoker;
        final String error;
        final String mode;

        Outcome(Invoker invoker, String error, String mode) {
            this.invoker = invoker;
            this.error = error;
            this.mode = mode;
        }
    }

    static List<Spec> readIndex(Path generatedRoot, Set<String> fallbackGroups) throws IOException {
        List<String> lines = Files.readAllLines(generatedRoot.resolve("index.tsv"), StandardCharsets.UTF_8);
        if (lines.isEmpty() || !"file\tgroup\tclass\ttests".equals(lines.get(0))) {
            throw new IllegalStateException("Invalid official compiled cases index");
        }
        List<Spec> cases = new ArrayList<>();
        Map<String, Boolean> seen = new LinkedHashMap<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isEmpty()) continue;
            String[] cells = lines.get(i).split("\t", -1);
            if (cells.length != 4) throw new IllegalStateException("Invalid index line " + (i + 1));
            String key = cells[0] + "#" + cells[1];
            Spec spec = new Spec(cells[0], Integer.parseInt(cells[1]),
                    cells[2], Integer.parseInt(cells[3]), fallbackGroups.contains(key));
            if (spec.group < 0 || spec.tests < 1 || !spec.file.endsWith(".json")
                    || !spec.className.matches("[A-Za-z][A-Za-z0-9]*")
                    || seen.putIfAbsent(spec.key(), true) != null) {
                throw new IllegalStateException("Invalid or duplicate official case: " + spec.key());
            }
            cases.add(spec);
        }
        if (!seen.keySet().containsAll(fallbackGroups)) {
            Set<String> missing = new java.util.LinkedHashSet<>(fallbackGroups);
            missing.removeAll(seen.keySet());
            throw new IllegalStateException("Unknown explicit fallback groups: " + missing);
        }
        return cases;
    }

    void compileAll(List<Spec> specs, boolean probeDirect) throws IOException {
        // A previous run may have written partially generated classes/sources.
        // Never allow stale output to make a failed batch appear compilable.
        if (Files.exists(workRoot)) {
            try (Stream<Path> paths = Files.walk(workRoot)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        Files.createDirectories(workRoot);
        if (compiler == null) {
            for (Spec spec : specs) {
                outcomes.put(spec.key(), new Outcome(null, "JDK compiler unavailable: run with a JDK", "COMPILE_ERROR"));
            }
            return;
        }
        // Compile the fallback=false binding by default. Explicit exceptions
        // are chosen by OfficialCompiledTest, not inferred from javac failures.
        Map<String, List<Spec>> byFile = new LinkedHashMap<>();
        for (Spec spec : specs) {
            byFile.computeIfAbsent(spec.file, unused -> new ArrayList<>()).add(spec);
        }
        for (List<Spec> batch : byFile.values()) {
            compileBatch(batch, false, outcomes);
        }
        if (probeDirect) {
            // Verify that each explicitly allowed fallback is still necessary.
            Map<String, List<Spec>> fallbackByFile = new LinkedHashMap<>();
            for (Spec spec : specs) {
                if (spec.fallback) {
                    fallbackByFile.computeIfAbsent(spec.file, unused -> new ArrayList<>()).add(spec);
                }
            }
            Map<String, Outcome> direct = new LinkedHashMap<>();
            for (List<Spec> batch : fallbackByFile.values()) {
                compileBatch(batch, true, direct);
            }
            for (Spec spec : specs) {
                if (!spec.fallback) continue;
                Outcome probe = direct.get(spec.key());
                if (probe == null) {
                    throw new IllegalStateException("Missing direct compilation probe: " + spec.key());
                }
                if (probe.invoker != null) {
                    outcomes.put(spec.key(), new Outcome(null,
                            "Stale explicit fallback: schema now compiles with fallback=false: "
                                    + spec.key(), "COMPILE_ERROR"));
                } else {
                    directFailures.put(spec.key(), probe.error);
                }
            }
        }
        if (outcomes.size() != specs.size()) {
            throw new IllegalStateException("Compiled case accounting mismatch: "
                    + outcomes.size() + " vs " + specs.size());
        }
    }

    String compilationError(Spec spec) {
        Outcome outcome = outcomes.get(spec.key());
        if (outcome == null) throw new IllegalStateException("Unknown case " + spec.key());
        return outcome.error;
    }

    String compilationMode(Spec spec) {
        Outcome result = outcomes.get(spec.key());
        if (result == null) throw new IllegalStateException("Unknown case " + spec.key());
        return result.mode;
    }

    String directCompilationError(Spec spec) {
        return directFailures.get(spec.key());
    }

    boolean validate(Spec spec, Object data) throws Exception {
        Outcome outcome = outcomes.get(spec.key());
        if (outcome == null || outcome.invoker == null) {
            throw new IllegalStateException("Case not compiled: " + spec.key());
        }
        return outcome.invoker.validate(data);
    }

    private void compileBatch(List<Spec> batch, boolean direct,
                              Map<String, Outcome> destination) throws IOException {
        if (batch.isEmpty()) return;
        Path dir = workRoot.resolve(String.format(Locale.ROOT, "batch-%05d", ++batchSerial));
        Path classes = dir.resolve("classes");
        Path generatedSources = dir.resolve("sources");
        Files.createDirectories(classes);
        Files.createDirectories(generatedSources);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        boolean compiled;
        try (StandardJavaFileManager files =
                     compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            List<java.io.File> sources = new ArrayList<>();
            for (Spec spec : batch) {
                sources.add(generatedRoot.resolve("java")
                        .resolve(PACKAGE.replace('.', '/'))
                        .resolve(spec.className + (spec.fallback && !direct ? "Fallback" : "") + ".java").toFile());
            }
            String classpath = System.getProperty("sjf4j.schema.officialCompilerClasspath",
                    System.getProperty("java.class.path"));
            classpath += java.io.File.pathSeparator + generatedRoot.resolve("resources");
            List<String> options = Arrays.asList(
                    "-classpath", classpath,
                    "-d", classes.toString(),
                    "-s", generatedSources.toString(),
                    "-encoding", "UTF-8");
            JavaCompiler.CompilationTask task = compiler.getTask(null, files, diagnostics,
                    options, null, files.getJavaFileObjectsFromFiles(sources));
            task.setProcessors(Collections.singletonList(new CodegenProcessor()));
            compiled = Boolean.TRUE.equals(task.call());
        } catch (RuntimeException | LinkageError e) {
            // Processor exceptions are also isolated, not allowed to abort the whole suite.
            if (batch.size() > 1) {
                split(batch, direct, destination);
                return;
            }
            destination.put(batch.get(0).key(), new Outcome(null, e.toString(), "COMPILE_ERROR"));
            return;
        }

        if (!compiled) {
            if (batch.size() > 1) {
                split(batch, direct, destination);
                return;
            }
            StringBuilder error = new StringBuilder();
            for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                if (d.getKind() != Diagnostic.Kind.ERROR) continue;
                if (error.length() > 0) error.append(" | ");
                error.append(d.getMessage(Locale.ROOT));
                if (error.length() > 3000) break;
            }
            destination.put(batch.get(0).key(), new Outcome(null,
                    error.length() == 0 ? "javac failed without error diagnostics" : error.toString(),
                    "COMPILE_ERROR"));
            return;
        }

        URLClassLoader loader = new URLClassLoader(
                new URL[]{classes.toUri().toURL(), generatedRoot.resolve("resources").toUri().toURL()},
                OfficialCaseRunner.class.getClassLoader());
        classLoaders.add(loader);
        for (Spec spec : batch) {
            try {
                Class<?> type = Class.forName(PACKAGE + "." + spec.className
                        + (spec.fallback && !direct ? "Fallback" : ""), true, loader);
                @SuppressWarnings({"rawtypes", "unchecked"})
                Object validator = CompiledInstances.create((Class) type);
                Method method = type.getMethod("valid", Object.class);
                destination.put(spec.key(), new Outcome(value -> {
                    try {
                        return (Boolean) method.invoke(validator, value);
                    } catch (InvocationTargetException e) {
                        Throwable cause = e.getCause();
                        if (cause instanceof Error) throw (Error) cause;
                        if (cause instanceof Exception) throw (Exception) cause;
                        throw new IllegalStateException(cause);
                    }
                }, null, direct || !spec.fallback ? "DIRECT" : "FALLBACK"));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                destination.put(spec.key(), new Outcome(null,
                        "Generated validator load failed: " + e, "COMPILE_ERROR"));
            }
        }
    }

    private void split(List<Spec> batch, boolean direct,
                       Map<String, Outcome> destination) throws IOException {
        int midpoint = batch.size() / 2;
        compileBatch(batch.subList(0, midpoint), direct, destination);
        compileBatch(batch.subList(midpoint, batch.size()), direct, destination);
    }

    @Override
    public void close() throws IOException {
        IOException first = null;
        for (URLClassLoader loader : classLoaders) {
            try {
                loader.close();
            } catch (IOException e) {
                if (first == null) first = e;
                else first.addSuppressed(e);
            }
        }
        if (first != null) throw first;
    }
}
