package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sjf4j.CompiledInstances;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression for the internal javac resource bridge used by official schema fixtures.
 * No public Map-taking CodegenProcessor constructor or annotation option is needed.
 */
class CompiledValidatorExternalIndexTest {

    @TempDir
    Path directory;

    @Test
    void retainsOnlyThePublicDefaultProcessorConstructor() throws Exception {
        Constructor<?>[] constructors = CodegenProcessor.class.getConstructors();
        assertEquals(1, constructors.length);
        assertEquals(0, constructors[0].getParameterCount());
        assertTrue(CodegenProcessor.class.getConstructor().newInstance()
                .getSupportedOptions().contains("sjf4j.internal.schemaExternalIndex"));
    }

    @Test
    void isolatesTheSameRetrievalUriAcrossInterfacesWithinOneJavacInvocation()
            throws Exception {
        String logical = "https://example.test/shared";
        Path first = directory.resolve("string.json");
        Path second = directory.resolve("integer.json");
        Files.writeString(first, "{\"type\":\"string\"}");
        Files.writeString(second, "{\"type\":\"integer\"}");
        Path index = directory.resolve("sources.tsv");
        Files.writeString(index, "class\turi\tsource\n"
                + "fixture.First\t" + logical + "\t" + first.toUri() + "\n"
                + "fixture.Second\t" + logical + "\t" + second.toUri() + "\n");

        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/First.java", source("First", logical));
        sources.put("fixture/Second.java", source("Second", logical));
        NavigatorTestCompiler.Result without = NavigatorTestCompiler.compile(
                sources, CodegenProcessor.class);
        assertFalse(without.success, "No external resources should resolve without a registry");

        NavigatorTestCompiler.Result compiled = NavigatorTestCompiler.compile(
                sources, CodegenProcessor.class,
                "-Asjf4j.internal.schemaExternalIndex=" + index.toAbsolutePath());
        assertTrue(compiled.success, compiled.diagnostics());
        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            assertValidation(loader, "fixture.First", "value", true);
            assertValidation(loader, "fixture.First", 42, false);
            assertValidation(loader, "fixture.Second", "value", false);
            assertValidation(loader, "fixture.Second", 42, true);
        }
    }

    private static String source(String simpleName, String retrievalUri) {
        return "package fixture; "
                + "import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator(fallback=false) "
                + "public interface " + simpleName + " { "
                + "@ValidJsonSchema(\"{\\\"$ref\\\":\\\"" + retrievalUri + "\\\"}\") "
                + "boolean valid(Object value); }";
    }

    private static void assertValidation(URLClassLoader loader, String name, Object instance,
                                         boolean expected) throws Exception {
        Class<?> type = loader.loadClass(name);
        Object validator = CompiledInstances.create(type);
        Method method = type.getMethod("valid", Object.class);
        assertEquals(expected, method.invoke(validator, instance), name);
    }
}
