package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.processor.code.JavaWriter;
import org.sjf4j.schema.ValidationException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Regression for Draft 2020-12 enum.json#14: empty enum rejects every instance. */
class CompiledValidatorEmptyEnumProcessorTest {
    private static final String EMPTY_ENUM =
            "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"enum\":[]}";

    @Test
    void emptyEnumRootCompilesAndRejectsAllOfficialInstanceShapes() throws Exception {
        NavigatorTestCompiler.Result compiled = compile(
                "@ValidJsonSchema(" + JavaWriter.stringLiteral(EMPTY_ENUM)
                        + ") boolean valid(Object value);");
        assertTrue(compiled.success, compiled.diagnostics());
        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            Class<?> type = loader.loadClass("fixture.Validator");
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object validator = CompiledInstances.create((Class) type);
            Method valid = type.getMethod("valid", Object.class);
            for (Object instance : new Object[]{"foo", 42, null, Map.of(), List.of(), false}) {
                assertEquals(Boolean.FALSE, valid.invoke(validator, instance),
                        "empty enum must reject " + instance);
            }
        }
    }

    @Test
    void typedEmptyEnumCompilesToConstantRejection() throws Exception {
        NavigatorTestCompiler.Result compiled = compile(
                "@ValidJsonSchema(\"{\\\"type\\\":\\\"string\\\",\\\"enum\\\":[]}\") "
                        + "boolean text(String value);");
        assertTrue(compiled.success, compiled.diagnostics());
        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            Class<?> type = loader.loadClass("fixture.Validator");
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object validator = CompiledInstances.create((Class) type);
            Method valid = type.getMethod("text", String.class);
            assertEquals(Boolean.FALSE, valid.invoke(validator, "foo"));
            assertEquals(Boolean.FALSE, valid.invoke(validator, (Object) null));
        }
    }

    @Test
    void emptyEnumRequireValidThrowsValidationException() throws Exception {
        NavigatorTestCompiler.Result compiled = compile(
                "@ValidJsonSchema(" + JavaWriter.stringLiteral(EMPTY_ENUM)
                        + ") void requireValid(Object value);");
        assertTrue(compiled.success, compiled.diagnostics());
        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            Class<?> type = loader.loadClass("fixture.Validator");
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object validator = CompiledInstances.create((Class) type);
            InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                    () -> type.getMethod("requireValid", Object.class).invoke(validator, "foo"));
            assertInstanceOf(ValidationException.class, thrown.getCause());
        }
    }

    private static NavigatorTestCompiler.Result compile(String method) throws Exception {
        String source = "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator(fallback=false) public interface Validator {"
                + method + "}";
        return NavigatorTestCompiler.compile(
                Map.of("fixture/Validator.java", source), CodegenProcessor.class);
    }
}
