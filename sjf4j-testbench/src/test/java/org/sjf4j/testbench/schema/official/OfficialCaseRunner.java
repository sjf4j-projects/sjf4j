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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

        Spec(String file, int group, String className, int tests) {
            this.file = file;
            this.group = group;
            this.className = className;
            this.tests = tests;
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

        Outcome(Invoker invoker, String error) {
            this.invoker = invoker;
            this.error = error;
        }
    }

    static List<Spec> readIndex(Path generatedRoot) throws IOException {
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
            Spec spec = new Spec(cells[0], Integer.parseInt(cells[1]),
                    cells[2], Integer.parseInt(cells[3]));
            if (spec.group < 0 || spec.tests < 1 || !spec.file.endsWith(".json")
                    || !spec.className.matches("[A-Za-z][A-Za-z0-9]*")
                    || seen.putIfAbsent(spec.key(), true) != null) {
                throw new IllegalStateException("Invalid or duplicate official case: " + spec.key());
            }
            cases.add(spec);
        }
        return cases;
    }

    void compileAll(List<Spec> specs) throws IOException {
        Files.createDirectories(workRoot);
        if (compiler == null) {
            for (Spec spec : specs) {
                outcomes.put(spec.key(), new Outcome(null, "JDK compiler unavailable: run with a JDK"));
            }
            return;
        }
        Map<String, List<Spec>> byFile = new LinkedHashMap<>();
        for (Spec spec : specs) {
            byFile.computeIfAbsent(spec.file, unused -> new ArrayList<>()).add(spec);
        }
        for (List<Spec> batch : byFile.values()) {
            compileBatch(batch);
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

    boolean validate(Spec spec, Object data) throws Exception {
        Outcome outcome = outcomes.get(spec.key());
        if (outcome == null || outcome.invoker == null) {
            throw new IllegalStateException("Case not compiled: " + spec.key());
        }
        return outcome.invoker.validate(data);
    }

    private void compileBatch(List<Spec> batch) throws IOException {
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
                        .resolve(spec.className + ".java").toFile());
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
                split(batch);
                return;
            }
            outcomes.put(batch.get(0).key(), new Outcome(null, e.toString()));
            return;
        }

        if (!compiled) {
            if (batch.size() > 1) {
                split(batch);
                return;
            }
            StringBuilder error = new StringBuilder();
            for (Diagnostic<? extends JavaFileObject> d : diagnostics.getDiagnostics()) {
                if (d.getKind() != Diagnostic.Kind.ERROR) continue;
                if (error.length() > 0) error.append(" | ");
                error.append(d.getMessage(Locale.ROOT));
                if (error.length() > 3000) break;
            }
            outcomes.put(batch.get(0).key(), new Outcome(null,
                    error.length() == 0 ? "javac failed without error diagnostics" : error.toString()));
            return;
        }

        URLClassLoader loader = new URLClassLoader(
                new URL[]{classes.toUri().toURL(), generatedRoot.resolve("resources").toUri().toURL()},
                OfficialCaseRunner.class.getClassLoader());
        classLoaders.add(loader);
        for (Spec spec : batch) {
            try {
                Class<?> type = Class.forName(PACKAGE + "." + spec.className, true, loader);
                @SuppressWarnings({"rawtypes", "unchecked"})
                Object validator = CompiledInstances.create((Class) type);
                Method method = type.getMethod("valid", Object.class);
                outcomes.put(spec.key(), new Outcome(value -> {
                    try {
                        return (Boolean) method.invoke(validator, value);
                    } catch (InvocationTargetException e) {
                        Throwable cause = e.getCause();
                        if (cause instanceof Error) throw (Error) cause;
                        if (cause instanceof Exception) throw (Exception) cause;
                        throw new IllegalStateException(cause);
                    }
                }, null));
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                outcomes.put(spec.key(), new Outcome(null, "Generated validator load failed: " + e));
            }
        }
    }

    private void split(List<Spec> batch) throws IOException {
        int midpoint = batch.size() / 2;
        compileBatch(batch.subList(0, midpoint));
        compileBatch(batch.subList(midpoint, batch.size()));
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
