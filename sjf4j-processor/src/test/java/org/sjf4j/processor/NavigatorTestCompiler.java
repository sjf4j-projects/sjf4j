package org.sjf4j.processor;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Lightweight compiler fixture for navigator integration tests. */
final class NavigatorTestCompiler {

    private NavigatorTestCompiler() {
    }

    static Result compile(Map<String, String> sources) throws IOException {
        return compile(
                sources,
                TestNavigatorProcessor.class);
    }


    static Result compile(
            Map<String, String> sources,
            Class<?> processor) throws IOException {

        Path directory = Files.createTempDirectory("sjf4j-navigator-test");
        Path source = directory.resolve("src");
        Path classes = directory.resolve("classes");
        Path generated = directory.resolve("generated");
        Files.createDirectories(source);
        Files.createDirectories(classes);
        Files.createDirectories(generated);

        List<java.io.File> files = new ArrayList<java.io.File>();
        for (Map.Entry<String, String> entry : sources.entrySet()) {
            Path file = source.resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, entry.getValue(), StandardCharsets.UTF_8);
            files.add(file.toFile());
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "JDK compiler is required");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<JavaFileObject>();

        try (StandardJavaFileManager manager = compiler.getStandardFileManager(
                diagnostics, null, StandardCharsets.UTF_8)) {
            manager.setLocation(StandardLocation.CLASS_OUTPUT, Arrays.asList(classes.toFile()));
            manager.setLocation(StandardLocation.SOURCE_OUTPUT, Arrays.asList(generated.toFile()));
            Boolean success = compiler.getTask(null, manager, diagnostics, Arrays.asList(
                    "-classpath", System.getProperty("java.class.path"),
                    "-processor", processor.getName()
            ), null, manager.getJavaFileObjectsFromFiles(files)).call();
            return new Result(directory, classes, generated, Boolean.TRUE.equals(success), diagnostics);
        }
    }

    static final class Result {

        final Path directory;
        final Path classes;
        final Path generated;
        final boolean success;
        private final DiagnosticCollector<JavaFileObject> diagnostics;

        Result(
                Path directory,
                Path classes,
                Path generated,
                boolean success,
                DiagnosticCollector<JavaFileObject> diagnostics) {
            this.directory = directory;
            this.classes = classes;
            this.generated = generated;
            this.success = success;
            this.diagnostics = diagnostics;
        }

        String diagnostics() {
            StringBuilder text = new StringBuilder();
            for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                text.append(diagnostic.getMessage(null)).append('\n');
            }
            return text.toString();
        }

        String generatedSource(String relativePath) throws IOException {
            return Files.readString(generated.resolve(relativePath));
        }

        URLClassLoader classLoader(ClassLoader parent) throws IOException {
            return new URLClassLoader(new URL[]{classes.toUri().toURL()}, parent);
        }
    }
}
