package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.processor.code.JavaWriter;

import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CompiledValidatorNullPolicyTest {

    private static NavigatorTestCompiler.Result compile(String schema, String type) throws Exception {
        String source = "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator(fallback=false) public interface Check { "
                + "@ValidJsonSchema(" + JavaWriter.stringLiteral(schema) + ") "
                + "boolean valid(" + type + " value); }";
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                Map.of("fixture/Check.java", source), CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        return result;
    }

    @Test
    void typeAssertionAndShapeGuardShareOneNullCheck() throws Exception {
        NavigatorTestCompiler.Result result = compile(
                "{\"type\":\"string\",\"minLength\":2}", "String");
        String source = result.generatedSource("fixture/Check_Impl.java");
        assertEquals(1, source.split("value == null", -1).length - 1, source);
        assertFalse(source.contains("value != null"), source);
        assertValid(result, String.class, null, false);
        assertValid(result, String.class, "a", false);
        assertValid(result, String.class, "ab", true);
    }

    @Test
    void shapeOnlyAndNullableUnionKeepApplicabilityGuard() throws Exception {
        for (String schema : new String[]{"{\"minLength\":2}",
                "{\"type\":[\"string\",\"null\"],\"minLength\":2}"}) {
            NavigatorTestCompiler.Result result = compile(schema, "String");
            String source = result.generatedSource("fixture/Check_Impl.java");
            assertTrue(source.contains("value != null"), source);
            assertValid(result, String.class, null, true);
            assertValid(result, String.class, "a", false);
            assertValid(result, String.class, "ab", true);
        }
    }

    @Test
    void objectGuardDoesNotMakeItsChildrenNonNull() throws Exception {
        NavigatorTestCompiler.Result result = compile(
                "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\",\"minLength\":2}}}",
                "java.util.Map<String,Object>");
        String source = result.generatedSource("fixture/Check_Impl.java");
        assertEquals(1, source.split("value == null", -1).length - 1, source);
        assertFalse(source.contains("value != null"), source);
        assertValid(result, Map.class, null, false);
        assertValid(result, Map.class, Map.of(), true);
        Map<String, Object> missing = new java.util.HashMap<>();
        missing.put("name", null);
        assertValid(result, Map.class, missing, false);
        assertValid(result, Map.class, Map.of("name", "ab"), true);
    }

    @Test
    void dynamicNullTypeAcceptsJavaNull() throws Exception {
        NavigatorTestCompiler.Result result = compile("{\"type\":\"null\"}", "Object");
        assertValid(result, Object.class, null, true);
        assertValid(result, Object.class, "not null", false);
    }

    @Test
    void sharedReferenceDoesNotInheritCallerNullability() throws Exception {
        String schema = "{\"$defs\":{\"text\":{\"minLength\":2}},\"type\":\"object\","
                + "\"properties\":{\"a\":{\"allOf\":[{\"type\":\"string\"},{\"$ref\":\"#/$defs/text\"}]},"
                + "\"b\":{\"$ref\":\"#/$defs/text\"}}}";
        NavigatorTestCompiler.Result result = compile(schema, "java.util.Map<String,Object>");
        Map<String,Object> input = new java.util.HashMap<>();
        input.put("a", "ok");
        input.put("b", null);
        assertValid(result, Map.class, input, true);
        input.put("a", null);
        assertValid(result, Map.class, input, false);
    }

    private static void assertValid(NavigatorTestCompiler.Result result, Class<?> type,
                                    Object value, boolean expected) throws Exception {
        try (URLClassLoader loader = result.classLoader(CompiledValidatorNullPolicyTest.class.getClassLoader())) {
            Class<?> impl = loader.loadClass("fixture.Check_Impl");
            Object validator = impl.getConstructor().newInstance();
            Method method = impl.getMethod("valid", type);
            assertEquals(expected, method.invoke(validator, value));
        }
    }
}
