package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sjf4j.CompiledInstances;
import org.sjf4j.processor.code.JavaWriter;

import java.lang.reflect.Method;
import java.net.URI;
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
        String generated = compiled.generatedSource("fixture/Check_Impl.java");
        assertTrue(generated.contains("registryLoader.listDirectory()"), generated);
        assertTrue(generated.contains(".indexIfAbsent(uri, registryLoader.load(uri))"), generated);

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
    void missingRegistryDirectoryReportsCompilationError() throws Exception {
        String missing = registryDir.resolve("missing").toUri().toString();
        NavigatorTestCompiler.Result result =
                compile("{\"$ref\":\"https://example.test/base.json\"}", missing);
        assertFalse(result.success);
        assertTrue(result.diagnostics().contains("schema registry directory not found"),
                result.diagnostics());
    }

    private static NavigatorTestCompiler.Result compile(String schema, String registryDir)
            throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Check.java",
                "package fixture; import org.sjf4j.annotation.schema.*; "
                        + "@CompiledValidator(fallback=true, registryDir="
                        + JavaWriter.stringLiteral(registryDir) + ") "
                        + "public interface Check { @ValidJsonSchema("
                        + JavaWriter.stringLiteral(schema)
                        + ") boolean valid(Object value); }");
        return NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
    }
}
