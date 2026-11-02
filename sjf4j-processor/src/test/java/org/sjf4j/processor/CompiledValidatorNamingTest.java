package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.processor.code.JavaWriter;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Compile actual validators to exercise collision handling at emission time. */
class CompiledValidatorNamingTest {

    private static final String OBJECT_SCHEMA = "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\",\"minLength\":2}}}";
    private static final String DYNAMIC_SCHEMA = "{\"type\":\"object\",\"additionalProperties\":false}";

    @Test
    void inlinedNamesAvoidSourceParameterCollisions() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Model.java",
                "package fixture; public final class Model { public String getName() { return \"hello\"; } }");
        sources.put("fixture/Check.java",
                "package fixture; import org.sjf4j.annotation.schema.*; "
                        + "@CompiledValidator(fallback=false) public interface Check { "
                        + "@ValidJsonSchema(" + JavaWriter.stringLiteral(OBJECT_SCHEMA) + ") "
                        + "boolean nameConflict(Model name); "
                        + "@ValidJsonSchema(" + JavaWriter.stringLiteral(OBJECT_SCHEMA) + ") "
                        + "boolean lengthConflict(Model length); "
                        + "@ValidJsonSchema(" + JavaWriter.stringLiteral(DYNAMIC_SCHEMA) + ") "
                        + "boolean keyConflict(org.sjf4j.JsonObject key); }");

        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());

        String generated = result.generatedSource("fixture/Check_Impl.java");
        assertTrue(generated.contains("String name2 = name.getName()"), generated);
        assertTrue(generated.contains("int length2 = "), generated);
        assertTrue(generated.contains("for (String key2 : key.keySet())"), generated);
        assertFalse(generated.contains("_property0"), generated);
        assertFalse(generated.contains("_text0_"), generated);
        assertFalse(generated.contains("_schema_M"), generated);
    }

    @Test
    void repeatedCompilationProducesIdenticalSource() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Check.java",
                "package fixture; import org.sjf4j.annotation.schema.*;"
                + " @CompiledValidator(fallback=false) public interface Check {"
                + " @ValidJsonSchema(\"{\\\"oneOf\\\":[{\\\"type\\\":\\\"string\\\"},{\\\"type\\\":\\\"number\\\"}]}\")"
                + " boolean check(Object value);"
                + " }");
        NavigatorTestCompiler.Result first =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        NavigatorTestCompiler.Result second =
                NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(first.success, first.diagnostics());
        assertTrue(second.success, second.diagnostics());
        assertEquals(first.generatedSource("fixture/Check_Impl.java"),
                second.generatedSource("fixture/Check_Impl.java"));
    }

    @Test
    void helperNamesAreSemanticAndIndependentOfMethodSerial() throws Exception {
        String schema = "{\"oneOf\":[{\"type\":\"string\"},{\"type\":\"number\"}]}";
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Check.java",
                "package fixture; import org.sjf4j.annotation.schema.*; "
                        + "@CompiledValidator(fallback=false) public interface Check { "
                        + "@ValidJsonSchema(" + JavaWriter.stringLiteral(schema) + ") "
                        + "boolean first(Object value); "
                        + "@ValidJsonSchema(" + JavaWriter.stringLiteral(schema) + ") "
                        + "boolean second(Object value); }");
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());

        String generated = result.generatedSource("fixture/Check_Impl.java");
        assertTrue(generated.contains("private static boolean _matchesSchema("), generated);
        assertTrue(generated.contains("private static boolean _matchesSchema2("), generated);
        assertTrue(generated.contains("matchCount"), generated);
        assertFalse(generated.contains("_matches_M"), generated);
    }
}
