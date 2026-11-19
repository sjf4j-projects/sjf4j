package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sjf4j.CompiledInstances;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.schema.ValidationResult;

import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** External schema retrieval URIs are scoped to each compiled schema binding. */
class CompiledValidatorExternalSchemaRefTest {

    @TempDir
    Path directory;

    @Test
    void resolvesRemoteIdsAndRelativeReferencesForDirectAndFallbackValidation() throws Exception {
        Path mapping = directory.resolve("external.json");
        Files.write(mapping, ("{"
                + "\"https://example.test/strict.json\":{"
                + "\"$id\":\"https://example.test/strict.json\",\"$ref\":\"base.json\"},"
                + "\"https://example.test/base.json\":{"
                + "\"type\":\"string\",\"minLength\":3}"
                + "}").getBytes(StandardCharsets.UTF_8));
        String schema = "{\"$ref\":\"https://example.test/strict.json\"}";

        NavigatorTestCompiler.Result missing = compile(schema, null, false, false, false);
        assertFalse(missing.success);
        assertTrue(missing.diagnostics().contains("cannot resolve schema resource"),
                missing.diagnostics());

        String ref = mapping.toUri().toString();
        NavigatorTestCompiler.Result direct = compile(schema, ref, false, false, false);
        assertTrue(direct.success, direct.diagnostics());
        try (URLClassLoader loader = direct.classLoader(getClass().getClassLoader())) {
            Class<?> type = loader.loadClass("fixture.Check");
            Object validator = CompiledInstances.create(type);
            Method valid = type.getMethod("valid", Object.class);
            assertEquals(Boolean.TRUE, valid.invoke(validator, "hello"));
            assertEquals(Boolean.FALSE, valid.invoke(validator, "no"));
        }

        NavigatorTestCompiler.Result fallback = compile(schema, ref, true, false, true);
        assertTrue(fallback.success, fallback.diagnostics());
        try (URLClassLoader loader = fallback.classLoader(getClass().getClassLoader())) {
            Class<?> type = loader.loadClass("fixture.Check");
            Object validator = CompiledInstances.create(type);
            Method valid = type.getMethod("valid", Object.class);
            assertTrue(((ValidationResult) valid.invoke(validator, "hello")).isValid());
            assertFalse(((ValidationResult) valid.invoke(validator, "no")).isValid());
        }
    }

    @Test
    void resolvesRemoteRootAnchorWithoutSchemaId() throws Exception {
        Path mapping = directory.resolve("anchors.json");
        Files.write(mapping, ("{\"http://localhost:1234/draft2020-12/word.json\":"
                + "{\"$defs\":{\"word\":{\"$anchor\":\"word\",\"minLength\":3}}}}")
                .getBytes(StandardCharsets.UTF_8));

        NavigatorTestCompiler.Result result = compile(
                "http://localhost:1234/draft2020-12/word.json#word",
                mapping.toUri().toString(), false, true, false);
        assertTrue(result.success, result.diagnostics());
        try (URLClassLoader loader = result.classLoader(getClass().getClassLoader())) {
            Class<?> type = loader.loadClass("fixture.Check");
            Object validator = CompiledInstances.create(type);
            Method valid = type.getMethod("valid", Object.class);
            assertEquals(Boolean.TRUE, valid.invoke(validator, "hello"));
            assertEquals(Boolean.FALSE, valid.invoke(validator, "no"));
        }
    }

    @Test
    void missingExternalSchemaMappingReportsCompilationError() throws Exception {
        String missing = directory.resolve("missing.json").toUri().toString();
        NavigatorTestCompiler.Result result =
                compile("{\"type\":\"string\"}", missing, false, false, false);
        assertFalse(result.success);
        assertTrue(result.diagnostics().contains("externalSchemasRef not found"),
                result.diagnostics());
    }

    private static NavigatorTestCompiler.Result compile(
            String schema, String mappingRef, boolean fallback,
            boolean remoteRoot, boolean validationResult) throws Exception {
        String schemaAnnotation = (remoteRoot ? "ref=" : "value=")
                + JavaWriter.stringLiteral(schema);
        if (mappingRef != null) {
            schemaAnnotation += ",externalSchemasRef=" + JavaWriter.stringLiteral(mappingRef);
        }
        String source = "package fixture; "
                + "import org.sjf4j.annotation.schema.*; "
                + "import org.sjf4j.schema.ValidationResult; "
                + "@CompiledValidator(fallback=" + fallback + ") "
                + "public interface Check { "
                + "@ValidJsonSchema(" + schemaAnnotation + ") "
                + (validationResult ? "ValidationResult" : "boolean")
                + " valid(Object value); }";
        return NavigatorTestCompiler.compile(
                Map.of("fixture/Check.java", source), CodegenProcessor.class);
    }
}
