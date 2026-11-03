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

/** Regression for draft2020-12 enum.json#14: an empty enum rejects all values. */
class CompiledValidatorEmptyEnumProcessorTest {

    @Test
    void emptyEnumRejectsEveryInstanceWithoutUnreachableGeneratedCode() throws Exception {
        String schema = "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"enum\":[]}";
        String source = "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator(fallback=false) public interface Validator {"
                + "@ValidJsonSchema(" + JavaWriter.stringLiteral(schema)
                + ") boolean valid(Object value); "
                + "@ValidJsonSchema(\"{\\\"type\\\":\\\"string\\\",\\\"enum\\\":[]}\") "
                + "boolean text(String value); "
                + "@ValidJsonSchema(" + JavaWriter.stringLiteral(schema)
                + ") void requireValid(Object value); "
                + "}";
        NavigatorTestCompiler.Result compiled = NavigatorTestCompiler.compile(
                Map.of("fixture/Validator.java", source), CodegenProcessor.class);
        assertTrue(compiled.success, compiled.diagnostics());
        String generated = compiled.generatedSource("fixture/Validator_Impl.java");
        assertFalse(generated.contains("return false;\n        return true;"), generated);

        try (URLClassLoader loader = compiled.classLoader(getClass().getClassLoader())) {
            Class<?> type = loader.loadClass("fixture.Validator");
            @SuppressWarnings({"rawtypes", "unchecked"})
            Object validator = CompiledInstances.create((Class) type);
            Method valid = type.getMethod("valid", Object.class);
            for (Object instance : new Object[]{"foo", 42, null, Map.of(), List.of(), false}) {
                assertEquals(Boolean.FALSE, valid.invoke(validator, instance),
                        "empty enum must reject " + instance);
            }
            assertEquals(Boolean.FALSE, type.getMethod("text", String.class).invoke(validator, "foo"));
            assertEquals(Boolean.FALSE, type.getMethod("text", String.class).invoke(validator, (Object) null));

            InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                    () -> type.getMethod("requireValid", Object.class).invoke(validator, "foo"));
            assertInstanceOf(ValidationException.class, thrown.getCause());
        }
    }
}
