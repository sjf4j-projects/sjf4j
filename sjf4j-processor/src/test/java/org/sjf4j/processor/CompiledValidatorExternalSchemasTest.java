package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sjf4j.CompiledInstances;
import org.sjf4j.schema.ValidationResult;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CompiledValidatorExternalSchemasTest {

    @TempDir
    Path directory;

    private static final String LOGICAL = "https://example.test/shared";

    @Test
    void processorRetainsPublicNoArgConstructor() {
        Constructor<?>[] constructors = CodegenProcessor.class.getConstructors();
        assertEquals(1, constructors.length);
        assertEquals(0, constructors[0].getParameterCount());
    }

    @Test
    void sameRetrievalUriCanResolveDifferentlyForTwoBindingsInOneJavacInvocation()
            throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/First.java", source("First",
                "{\"type\":\"string\"}", null, false));
        sources.put("fixture/Second.java", source("Second",
                "{\"type\":\"integer\"}", null, false));
        NavigatorTestCompiler.Result compiled = NavigatorTestCompiler.compile(
                sources, CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());
        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            assertValidation(loader, "fixture.First", "value", true);
            assertValidation(loader, "fixture.First", 42, false);
            assertValidation(loader, "fixture.Second", "value", false);
            assertValidation(loader, "fixture.Second", 42, true);
        }
    }

    @Test
    void externalSchemasRefAlsoWorksForGeneratedRuntimeFallback() throws Exception {
        Path mapping = directory.resolve("external.json");
        Files.writeString(mapping, "{\"" + LOGICAL + "\":{\"type\":\"integer\"}}");
        Map<String, String> sources = Map.of(
                "fixture/WithRef.java", source("WithRef", null, mapping.toUri().toString(), true));
        NavigatorTestCompiler.Result compiled = NavigatorTestCompiler.compile(
                sources, CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());
        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            Class<?> type = loader.loadClass("fixture.WithRef");
            Object instance = CompiledInstances.create(type);
            Method method = type.getMethod("valid", Object.class);
            assertFalse(((ValidationResult) method.invoke(instance, "value")).isValid());
            assertTrue(((ValidationResult) method.invoke(instance, 42)).isValid());
        }
    }

    @Test
    void inlineMappingTakesPriorityOverRef() throws Exception {
        Path mapping = directory.resolve("ignored.json");
        Files.writeString(mapping, "{\"" + LOGICAL + "\":{\"type\":\"integer\"}}");
        Map<String, String> sources = Map.of(
                "fixture/Priority.java", source("Priority",
                        "{\"type\":\"string\"}", mapping.toUri().toString(), false));
        NavigatorTestCompiler.Result compiled = NavigatorTestCompiler.compile(
                sources, CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());
        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            assertValidation(loader, "fixture.Priority", "value", true);
            assertValidation(loader, "fixture.Priority", 42, false);
        }
    }

    @Test
    void mappingsAreIsolatedBetweenMethodsOfTheSameValidator() throws Exception {
        String root = quote("{\"$ref\":\"" + LOGICAL + "\"}");
        String strings = quote("{\"" + LOGICAL + "\":{\"type\":\"string\"}}");
        String integers = quote("{\"" + LOGICAL + "\":{\"type\":\"integer\"}}");
        String source = "package fixture; "
                + "import org.sjf4j.annotation.schema.*; "
                + "import org.sjf4j.schema.ValidationResult; "
                + "@CompiledValidator(fallback=true) public interface Multi { "
                + "@ValidJsonSchema(value=" + root + ",externalSchemas=" + strings + ") "
                + "ValidationResult text(Object value); "
                + "@ValidJsonSchema(value=" + root + ",externalSchemas=" + integers + ") "
                + "ValidationResult number(Object value); }";
        NavigatorTestCompiler.Result compiled = NavigatorTestCompiler.compile(
                Map.of("fixture/Multi.java", source), CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());
        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            Class<?> type = loader.loadClass("fixture.Multi");
            Object validator = CompiledInstances.create(type);
            Method text = type.getMethod("text", Object.class);
            Method number = type.getMethod("number", Object.class);
            assertTrue(((ValidationResult) text.invoke(validator, "ok")).isValid());
            assertFalse(((ValidationResult) text.invoke(validator, 4)).isValid());
            assertTrue(((ValidationResult) number.invoke(validator, 4)).isValid());
            assertFalse(((ValidationResult) number.invoke(validator, "ok")).isValid());
        }
    }

    @Test
    void directorySourceCacheDoesNotOverridePerBindingExternalSchemas() throws Exception {
        Files.writeString(directory.resolve("word.json"), "{\"type\":\"boolean\"}");
        String target = "https://example.test/word.json";
        String root = quote("{\"$ref\":\"" + target + "\"}");
        String options = "registryDir=" + quote(directory.toUri().toString())
                + ",registryBaseUri=\"https://example.test/\",";
        String textMap = quote("{\"" + target + "\":{\"type\":\"string\"}}");
        String numberMap = quote("{\"" + target + "\":{\"type\":\"integer\"}}");
        String direct = "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator(" + options + "fallback=false) public interface Direct { "
                + "@ValidJsonSchema(value=" + root + ",externalSchemas=" + textMap + ") "
                + "boolean valid(Object value); }";
        String fallback = "package fixture; import org.sjf4j.annotation.schema.*; "
                + "import org.sjf4j.schema.ValidationResult; "
                + "@CompiledValidator(" + options + "fallback=true) public interface Fallback { "
                + "@ValidJsonSchema(value=" + root + ",externalSchemas=" + numberMap + ") "
                + "ValidationResult valid(Object value); }";
        NavigatorTestCompiler.Result compiled = NavigatorTestCompiler.compile(
                Map.of("fixture/Direct.java", direct, "fixture/Fallback.java", fallback),
                CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());
        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            assertValidation(loader, "fixture.Direct", "hello", true);
            assertValidation(loader, "fixture.Direct", 10, false);

            Class<?> type = loader.loadClass("fixture.Fallback");
            Object validator = CompiledInstances.create(type);
            Method valid = type.getMethod("valid", Object.class);
            assertTrue(((ValidationResult) valid.invoke(validator, 10)).isValid());
            assertFalse(((ValidationResult) valid.invoke(validator, "hello")).isValid());
        }
    }

    @Test
    void invalidExternalSchemaMappingFailsCompilation() throws Exception {
        Map<String, String> sources = Map.of(
                "fixture/Invalid.java", source("Invalid", "[]", null, false, true));
        NavigatorTestCompiler.Result compiled = NavigatorTestCompiler.compile(
                sources, CodegenProcessor.class);
        assertFalse(compiled.success);
    }

    private static String source(String simpleName, String schema, String ref, boolean fallback) {
        return source(simpleName, schema, ref, fallback, false);
    }

    private static String source(String simpleName, String schema, String ref,
                                 boolean fallback, boolean raw) {
        String mapping = schema == null ? "" : raw ? schema
                : "{\"" + LOGICAL + "\":" + schema + "}";
        String annotation = "value=" + quote("{\"$ref\":\"" + LOGICAL + "\"}");
        if (!mapping.isEmpty()) annotation += ",externalSchemas=" + quote(mapping);
        if (ref != null) annotation += ",externalSchemasRef=" + quote(ref);
        return "package fixture; "
                + "import org.sjf4j.annotation.schema.*; "
                + "import org.sjf4j.schema.ValidationResult; "
                + "@CompiledValidator(fallback=" + fallback + ") "
                + "public interface " + simpleName + " { "
                + "@ValidJsonSchema(" + annotation + ") "
                + (fallback ? "ValidationResult" : "boolean")
                + " valid(Object value); }";
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static void assertValidation(URLClassLoader loader, String name, Object instance,
                                         boolean expected) throws Exception {
        Class<?> type = loader.loadClass(name);
        Object validator = CompiledInstances.create(type);
        Method method = type.getMethod("valid", Object.class);
        assertEquals(expected, method.invoke(validator, instance), name);
    }
}
