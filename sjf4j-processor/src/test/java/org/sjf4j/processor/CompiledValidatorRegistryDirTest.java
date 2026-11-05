package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sjf4j.CompiledInstances;
import org.sjf4j.processor.code.JavaWriter;

import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Registry directory resources are indexed by $id before compiled schema resolution. */
class CompiledValidatorRegistryDirTest {

    @TempDir
    Path registryDir;

    @Test
    void loadsRemoteSchemaIdsForCompileTimeAndRuntimeFallback() throws Exception {
        Path nested = Files.createDirectories(registryDir.resolve("nested"));
        Files.write(registryDir.resolve("strict.json"),
                ("{\"$id\":\"https://example.test/strict.json\","
                        + "\"$ref\":\"base.json\"}").getBytes(StandardCharsets.UTF_8));
        Files.write(nested.resolve("base.json"),
                ("{\"$id\":\"https://example.test/base.json\","
                        + "\"type\":\"string\",\"minLength\":3}").getBytes(StandardCharsets.UTF_8));
        Files.write(registryDir.resolve("ignored.txt"), "{}".getBytes(StandardCharsets.UTF_8));

        String schema = "{\"$ref\":\"https://example.test/strict.json\"}";
        NavigatorTestCompiler.Result noRegistry = compile(schema, "");
        assertFalse(noRegistry.success, "Unregistered external references must fail");
        assertTrue(noRegistry.diagnostics().contains("cannot resolve schema resource"),
                noRegistry.diagnostics());

        NavigatorTestCompiler.Result compiled = compile(schema, registryDir.toUri().toString());
        assertTrue(compiled.success, compiled.diagnostics());
        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            Class<?> type = loader.loadClass("fixture.Check");
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object validator = CompiledInstances.create((Class) type);
            Method valid = type.getMethod("valid", Object.class);
            assertEquals(Boolean.TRUE, valid.invoke(validator, "hello"));
            assertEquals(Boolean.FALSE, valid.invoke(validator, "no"));
        }
    }

    @Test
    void loadsSchemasWithoutIdsUsingConfiguredRemoteRetrievalBase() throws Exception {
        Path draft = Files.createDirectories(registryDir.resolve("draft2020-12"));
        Files.write(draft.resolve("integer.json"),
                "{\"type\":\"integer\"}".getBytes(StandardCharsets.UTF_8));
        String remote = "http://localhost:1234/";
        String schema = "{\"$ref\":\"" + remote + "draft2020-12/integer.json\"}";

        NavigatorTestCompiler.Result withoutBase = compile(schema, registryDir.toUri().toString());
        assertFalse(withoutBase.success);
        assertTrue(withoutBase.diagnostics().contains("cannot resolve schema resource"),
                withoutBase.diagnostics());

        NavigatorTestCompiler.Result compiled = compile(
                schema, registryDir.toUri().toString(), remote);
        assertTrue(compiled.success, compiled.diagnostics());
        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            Class<?> type = loader.loadClass("fixture.Check");
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object validator = CompiledInstances.create((Class) type);
            Method valid = type.getMethod("valid", Object.class);
            assertEquals(Boolean.TRUE, valid.invoke(validator, 1));
            assertEquals(Boolean.FALSE, valid.invoke(validator, "a"));
        }
    }

    @Test
    void preloadedRemoteReferencesCompileDirectly() throws Exception {
        Path remote = Files.createDirectories(registryDir.resolve("draft2020-12"));
        Files.write(remote.resolve("word.json"),
                "{\"$anchor\":\"word\",\"type\":\"string\",\"minLength\":3}"
                        .getBytes(StandardCharsets.UTF_8));
        String schema = "{\"$ref\":\"http://localhost:1234/draft2020-12/word.json#word\"}";
        NavigatorTestCompiler.Result result = compile(schema, registryDir.toUri().toString(),
                "http://localhost:1234/", false);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Check_Impl.java");
        assertFalse(generated.contains(".isValid("), generated);
        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> type = loader.loadClass("fixture.Check");
            Object validator = CompiledInstances.create(type);
            Method valid = type.getMethod("valid", Object.class);
            assertEquals(Boolean.TRUE, valid.invoke(validator, "hello"));
            assertEquals(Boolean.FALSE, valid.invoke(validator, "no"));
        }
    }

    @Test
    void rootRemoteRefSelectsPreloadedAnchorDirectly() throws Exception {
        Path remote = Files.createDirectories(registryDir.resolve("draft2020-12"));
        Files.write(remote.resolve("word.json"),
                "{\"$defs\":{\"word\":{\"$anchor\":\"word\",\"minLength\":3}}}"
                        .getBytes(StandardCharsets.UTF_8));
        String source = "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator(fallback=false, registryDir="
                + JavaWriter.stringLiteral(registryDir.toUri().toString())
                + ", registryBaseUri=\"http://localhost:1234/\") "
                + "public interface Check { "
                + "@ValidJsonSchema(ref=\"http://localhost:1234/draft2020-12/word.json#word\") "
                + "boolean valid(Object value); }";
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                Map.of("fixture/Check.java", source), CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        assertFalse(result.generatedSource("fixture/Check_Impl.java").contains(".isValid("));
    }

    @Test
    void missingRegistryDirectoryReportsCompilationError() throws Exception {
        // Path.toUri() omits the trailing slash for a directory that does not exist.
        String missing = registryDir.resolve("missing").toUri().toString() + "/";
        NavigatorTestCompiler.Result result =
                compile("{\"$ref\":\"https://example.test/base.json\"}", missing);
        assertFalse(result.success);
        assertTrue(result.diagnostics().contains("schema registry directory not found"),
                result.diagnostics());
    }

    private static NavigatorTestCompiler.Result compile(String schema, String registryDir)
            throws Exception {
        return compile(schema, registryDir, "");
    }

    private static NavigatorTestCompiler.Result compile(
            String schema, String registryDir, String registryBaseUri) throws Exception {
        return compile(schema, registryDir, registryBaseUri, true);
    }

    private static NavigatorTestCompiler.Result compile(
            String schema, String registryDir, String registryBaseUri, boolean fallback) throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Check.java",
                "package fixture; import org.sjf4j.annotation.schema.*; "
                        + "@CompiledValidator(fallback=" + fallback + ", registryDir="
                        + JavaWriter.stringLiteral(registryDir) + ", registryBaseUri="
                        + JavaWriter.stringLiteral(registryBaseUri) + ") "
                        + "public interface Check { @ValidJsonSchema("
                        + JavaWriter.stringLiteral(schema)
                        + ") boolean valid(Object value); }");
        return NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
    }
}
