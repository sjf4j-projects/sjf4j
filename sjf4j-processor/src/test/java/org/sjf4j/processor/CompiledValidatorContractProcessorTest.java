package org.sjf4j.processor;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Annotation processing contract: a fallback-disabled validator must be fully compiled. */
class CompiledValidatorContractProcessorTest {

    private static final String MODEL =
            "package fixture; public final class Model { "
                    + "public String getName() { return \"ok\"; } }";

    @Test
    void compilesRequiredOnlyObjectWithoutProperties() throws Exception {
        NavigatorTestCompiler.Result result = compile(
                "@CompiledValidator(fallback=false) public interface Check { "
                        + "@ValidJsonSchema(" + org.sjf4j.processor.code.JavaWriter.stringLiteral(
                                "{\"type\":\"object\",\"required\":[\"name\"]}") + ") "
                        + "boolean valid(Model value); }");
        assertTrue(result.success, result.diagnostics());
        assertFalse(result.generatedSource("fixture/Check_Impl.java").contains(".isValid("));
    }

    @Test
    void missingRequiredOnFinalPojoCompilesToReject() throws Exception {
        NavigatorTestCompiler.Result result = compile(
                "@CompiledValidator(fallback=false) public interface Check { "
                        + "@ValidJsonSchema(" + org.sjf4j.processor.code.JavaWriter.stringLiteral(
                                "{\"type\":\"object\",\"required\":[\"missing\"]}") + ") "
                        + "boolean valid(Model value); }");
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Check_Impl.java");
        assertFalse(generated.contains(".isValid("), generated);
        assertTrue(generated.contains("return false;"), generated);
    }

    @Test
    void typeLessMissingRequiredStillAcceptsNull() throws Exception {
        NavigatorTestCompiler.Result result = compile(
                "@CompiledValidator(fallback=false) public interface Check { "
                        + "@ValidJsonSchema(" + org.sjf4j.processor.code.JavaWriter.stringLiteral(
                                "{\"required\":[\"missing\"]}") + ") "
                        + "boolean valid(Model value); }");
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Check_Impl.java");
        assertFalse(generated.contains(".isValid("), generated);
        assertTrue(generated.contains("== null"), generated);
    }

    @Test
    void jojoUsesContextAwareMemberAccess() throws Exception {
        NavigatorTestCompiler.Result result = compile(
                "@CompiledValidator(fallback=false) public interface Check { "
                        + "@ValidJsonSchema(" + org.sjf4j.processor.code.JavaWriter.stringLiteral(
                                "{\"type\":\"object\",\"required\":[\"b\"],"
                                + "\"properties\":{\"b\":{\"type\":\"string\",\"minLength\":2}},"
                                + "\"additionalProperties\":false}") + ") "
                        + "class Variant extends org.sjf4j.JsonObject {} "
                        + "boolean valid(Variant value); }");
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Check_Impl.java");
        assertTrue(generated.contains(".containsKey(\"b\")"), generated);
        assertTrue(generated.contains("CompiledSupport.scalarValueInObject("), generated);
        assertTrue(generated.contains(".keySet()"), generated);
        assertFalse(generated.contains("Nodes.getInObject("), generated);
    }

    @Test
    void dependentRequiredCompilesWithoutSchemaPlan() throws Exception {
        String schema = "{\"type\":\"object\",\"dependentRequired\":{\"a\":[\"b\",\"c\"]}}";
        String source = "package fixture; import java.util.*; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator(fallback=false) public interface Check { "
                + "@ValidJsonSchema(" + org.sjf4j.processor.code.JavaWriter.stringLiteral(schema) + ") "
                + "boolean valid(Map<String,Object> value); }";
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Check.java", source), CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Check_Impl.java");
        assertTrue(generated.contains("keySetInObject"), generated);
        assertFalse(generated.contains(".isValid("), generated);
    }

    @Test
    void dependentRequiredOnFinalPojoCompilesToConstantReject() throws Exception {
        String schema = "{\"type\":\"object\",\"dependentRequired\":{\"name\":[\"missing\"]}}";
        NavigatorTestCompiler.Result result = compile(
                "@CompiledValidator(fallback=false) public interface Check { "
                        + "@ValidJsonSchema(" + org.sjf4j.processor.code.JavaWriter.stringLiteral(schema) + ") "
                        + "boolean valid(Model value); }");
        assertTrue(result.success, result.diagnostics());
        assertTrue(result.generatedSource("fixture/Check_Impl.java").contains("return false;"));
    }

    @Test
    void directlyCompilesExplicitOlderDrafts() throws Exception {
        for (String dialect : new String[]{
                "http://json-schema.org/draft-07/schema#",
                "https://json-schema.org/draft/2019-09/schema"}) {
            String schema = "{\"$schema\":\"" + dialect + "\",\"type\":\"object\"}";
            String source = "package fixture; import java.util.*; import org.sjf4j.annotation.schema.*; "
                    + "@CompiledValidator(fallback=false) public interface Check { "
                    + "@ValidJsonSchema(" + org.sjf4j.processor.code.JavaWriter.stringLiteral(schema) + ") "
                    + "boolean valid(Map<String,Object> value); }";
            NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                    "fixture/Check.java", source), CodegenProcessor.class);
            assertTrue(result.success, dialect + ": " + result.diagnostics());
            assertFalse(result.generatedSource("fixture/Check_Impl.java").contains(".isValid("));
        }
    }

    @Test
    void nestedDraft2019UsesItsOwnDialect() throws Exception {
        String schema = "{\"$defs\":{\"nested\":{\"$schema\":"
                + "\"https://json-schema.org/draft/2019-09/schema\",\"type\":\"string\"}},"
                + "\"$ref\":\"#/$defs/nested\"}";
        NavigatorTestCompiler.Result result = compile(
                "@CompiledValidator(fallback=false) public interface Check { "
                        + "@ValidJsonSchema(" + org.sjf4j.processor.code.JavaWriter.stringLiteral(schema) + ") "
                        + "boolean valid(Model value); }");
        assertTrue(result.success, result.diagnostics());
        assertFalse(result.generatedSource("fixture/Check_Impl.java").contains(".isValid("));
    }

    @Test
    void staticNamedAnchorCompilesDirectly() throws Exception {
        String schema = "{\"$defs\":{\"word\":{\"$anchor\":\"word\",\"minLength\":2}},"
                + "\"properties\":{\"name\":{\"$ref\":\"#word\"}}}";
        NavigatorTestCompiler.Result result = compile(
                "@CompiledValidator(fallback=false) public interface Check { "
                        + "@ValidJsonSchema(" + org.sjf4j.processor.code.JavaWriter.stringLiteral(schema) + ") "
                        + "boolean valid(Model value); }");
        assertTrue(result.success, result.diagnostics());
        assertFalse(result.generatedSource("fixture/Check_Impl.java").contains(".isValid("));
    }

    @Test
    void nestedDefsPointerCompilesDirectly() throws Exception {
        String schema = "{\"$defs\":{\"group\":{\"$defs\":{\"word\":{\"minLength\":2}}}},"
                + "\"properties\":{\"name\":{\"$ref\":\"#/$defs/group/$defs/word\"}}}";
        NavigatorTestCompiler.Result result = compile(
                "@CompiledValidator(fallback=false) public interface Check { "
                        + "@ValidJsonSchema(" + org.sjf4j.processor.code.JavaWriter.stringLiteral(schema) + ") "
                        + "boolean valid(Model value); }");
        assertTrue(result.success, result.diagnostics());
        assertFalse(result.generatedSource("fixture/Check_Impl.java").contains(".isValid("));
    }

    @Test
    void embeddedIdAnchorResolvesThroughCanonicalResource() throws Exception {
        String schema = "{\"$defs\":{\"nested\":{\"$id\":\"nested.json\","
                + "\"$anchor\":\"inner\",\"minLength\":2}},"
                + "\"properties\":{\"name\":{\"$ref\":\"nested.json#inner\"}}}";
        NavigatorTestCompiler.Result result = compile(
                "@CompiledValidator(fallback=false) public interface Check { "
                        + "@ValidJsonSchema(" + org.sjf4j.processor.code.JavaWriter.stringLiteral(schema) + ") "
                        + "boolean valid(Model value); }");
        assertTrue(result.success, result.diagnostics());
        assertFalse(result.generatedSource("fixture/Check_Impl.java").contains(".isValid("));
    }

    @Test
    void recursiveRefWithUnevaluatedRequiresFallback() throws Exception {
        String schema = "{\"$schema\":\"https://json-schema.org/draft/2019-09/schema\","
                + "\"$recursiveAnchor\":true,\"$recursiveRef\":\"#\","
                + "\"unevaluatedProperties\":false}";
        String source = "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator(fallback=false) public interface Check { "
                + "@ValidJsonSchema(" + org.sjf4j.processor.code.JavaWriter.stringLiteral(schema) + ") "
                + "boolean valid(Object value); }";
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(
                Map.of("fixture/Check.java", source), CodegenProcessor.class);
        assertFalse(result.success);
        assertTrue(result.diagnostics().contains("$recursiveRef + unevaluatedProperties"),
                result.diagnostics());
    }

    @Test
    void legacySchemaDependenciesWithUnevaluatedRequireFallback() throws Exception {
        String schema = "{\"$schema\":\"https://json-schema.org/draft/2019-09/schema\","
                + "\"properties\":{\"a\":true},"
                + "\"dependencies\":{\"a\":{\"properties\":{\"b\":true}}},"
                + "\"unevaluatedProperties\":false}";
        NavigatorTestCompiler.Result result = compile(
                "@CompiledValidator(fallback=false) public interface Check { "
                        + "@ValidJsonSchema(" + org.sjf4j.processor.code.JavaWriter.stringLiteral(schema) + ") "
                        + "boolean valid(Model value); }");
        assertFalse(result.success);
        assertTrue(result.diagnostics().contains("dependencies + unevaluatedProperties"),
                result.diagnostics());
    }

    @Test
    void rejectsUnsupportedSchemaWhenFallbackDisabled() throws Exception {
        String schema = "{\"type\":\"object\",\"allOf\":[{\"anyOf\":[{\"properties\":{\"name\":true}}]}],"
                + "\"unevaluatedProperties\":false}";
        NavigatorTestCompiler.Result result = compile(
                "@CompiledValidator(fallback=false) public interface Check { "
                        + "@ValidJsonSchema(" + org.sjf4j.processor.code.JavaWriter.stringLiteral(schema) + ") "
                        + "boolean valid(Model value); }");
        assertFalse(result.success);
        assertTrue(result.diagnostics().contains("allOf + unevaluatedProperties"), result.diagnostics());
    }


    @Test
    void neutralApplicatorCompilesRegardlessOfJavaShape() throws Exception {
        // Schema semantics, not a Java parameter's static shape, decide fallback.
        String schema = "{\"allOf\":[true],\"unevaluatedItems\":false}";
        for (String type : new String[]{"String", "int", "Model"}) {
            String source = "package fixture; import org.sjf4j.annotation.schema.*; "
                    + "@CompiledValidator(fallback=false) public interface Check { "
                    + "@ValidJsonSchema(" + org.sjf4j.processor.code.JavaWriter.stringLiteral(schema)
                    + ") boolean valid(" + type + " value); }";
            NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                    "fixture/Model.java", MODEL, "fixture/Check.java", source),
                    CodegenProcessor.class);
            assertTrue(result.success, type + "\n" + result.diagnostics());
            assertFalse(result.generatedSource("fixture/Check_Impl.java").contains(".isValid("));
        }
    }


    @Test
    void acceptsCoverageNeutralRequireValidWithoutFallback() throws Exception {
        NavigatorTestCompiler.Result result = compile(
                "@CompiledValidator(fallback=false) public interface Check { "
                        + "@ValidJsonSchema(" + org.sjf4j.processor.code.JavaWriter.stringLiteral(
                                "{\"allOf\":[true],\"unevaluatedProperties\":false}") + ") "
                        + "void requireValid(Model value); }");
        assertTrue(result.success, result.diagnostics());
        assertFalse(result.generatedSource("fixture/Check_Impl.java").contains(".isValid("));
    }


    @Test
    void rejectsResultWithoutExplicitFallback() throws Exception {
        NavigatorTestCompiler.Result result = compile(
                "@CompiledValidator public interface Check { "
                        + "@ValidJsonSchema(" + org.sjf4j.processor.code.JavaWriter.stringLiteral("true") + ") "
                        + "org.sjf4j.schema.ValidationResult validate(Model value); }");
        assertFalse(result.success);
        assertTrue(result.diagnostics().contains("ValidationResult methods require @CompiledValidator(fallback=true)"),
                result.diagnostics());
    }

    @Test
    void allowsResultWithExplicitFallback() throws Exception {
        NavigatorTestCompiler.Result result = compile(
                "@CompiledValidator(fallback=true) public interface Check { "
                        + "@ValidJsonSchema(" + org.sjf4j.processor.code.JavaWriter.stringLiteral("true") + ") "
                        + "org.sjf4j.schema.ValidationResult validate(Model value); }");
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Check_Impl.java");
        assertTrue(generated.contains(".validate("), generated);
        // Generated fallback resources must be visible through the generated
        // validator's defining loader, not SchemaLoader's parent loader.
        assertTrue(generated.contains(".class.getClassLoader()"), generated);
        assertTrue(generated.contains("SchemaLoader.Local("), generated);
    }

    @Test
    void defaultFallbackRejectsUnsupportedChecks() throws Exception {
        String schema = "{\"anyOf\":[true],\"unevaluatedProperties\":false}";
        NavigatorTestCompiler.Result result = compile(
                "@CompiledValidator public interface Check { "
                        + "@ValidJsonSchema(" + org.sjf4j.processor.code.JavaWriter.stringLiteral(schema) + ") "
                        + "boolean valid(Model value); }");
        assertFalse(result.success);
        assertTrue(result.diagnostics().contains("anyOf + unevaluatedProperties"), result.diagnostics());
    }


    @Test
    void allowsStrictFormatParameterOnFullyDirectMethod() throws Exception {
        NavigatorTestCompiler.Result result = compile(
                "@CompiledValidator(fallback=false) public interface Check { "
                        + "@ValidJsonSchema(\"{\\\"type\\\":\\\"object\\\","
                        + "\\\"properties\\\":{\\\"name\\\":{\\\"type\\\":\\\"string\\\","
                        + "\\\"minLength\\\":2}}}\") "
                        + "boolean valid(Model value, boolean strict); }");
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Check_Impl.java");
        assertTrue(generated.contains("boolean strict"), generated);
        assertTrue(generated.contains(".codePointCount("), generated);
        assertFalse(generated.contains("SchemaFallbacks.SchemaPlan.ROOT.isValid"), generated);
    }

    @Test
    void rejectsBoxedStrictFormatParameter() throws Exception {
        NavigatorTestCompiler.Result result = compile(
                "@CompiledValidator public interface Check { "
                        + "@ValidJsonSchema(\"true\") "
                        + "boolean valid(Model value, Boolean strict); }");
        assertFalse(result.success);
        assertTrue(result.diagnostics().contains("optional primitive boolean"), result.diagnostics());
    }

    @Test
    void creekPhaseGeneratesDirectChecksAndOnlyLocalizedItemFallback() throws Exception {
        String model = "package fixture; import java.util.*; import java.math.*; "
                + "public final class Model { public enum Kind { THIS, THAT } "
                + "public BigDecimal getDecimal() { return BigDecimal.ONE; } "
                + "public Kind getAnEnum() { return Kind.THIS; } "
                + "public String getName() { return \"good\"; } "
                + "public List<String> getList() { return List.of(\"ok\"); } "
                + "public List<Object> getPolymorphicTypes() { return List.of(\"A\"); } }";
        String schema = "{"
                + "\"$schema\":\"https://json-schema.org/draft/2020-12/schema\","
                + "\"title\":\"Creek phase\", \"type\":\"object\", \"additionalProperties\":false,"
                + "\"required\":[\"decimal\",\"name\",\"list\",\"polymorphicTypes\"],"
                + "\"properties\":{"
                + "\"decimal\":{\"type\":\"number\",\"exclusiveMinimum\":0},"
                + "\"anEnum\":{\"type\":\"string\",\"enum\":[\"THIS\",\"THAT\"]},"
                + "\"name\":{\"type\":\"string\",\"minLength\":2},"
                + "\"list\":{\"type\":\"array\",\"items\":{\"type\":\"string\"}},"
                + "\"polymorphicTypes\":{\"type\":\"array\",\"minItems\":1,"
                + "\"items\":{\"oneOf\":[{\"type\":\"string\",\"enum\":[\"A\"]},"
                + "{\"type\":\"number\",\"minimum\":1}]}}}}";
        String validator = "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator(fallback=true) public interface Check { "
                + "@ValidJsonSchema(" + org.sjf4j.processor.code.JavaWriter.stringLiteral(schema)
                + ") boolean valid(Model value, boolean strictFormat); }";
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Model.java", model,
                "fixture/Check.java", validator), CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());

        String generated = result.generatedSource("fixture/Check_Impl.java");
        assertTrue(generated.contains("Numbers.compare"), generated);
        assertTrue(generated.contains("codePointCount"), generated);
        assertTrue(generated.contains("for (") && generated.contains(".size()"), generated);
        assertTrue(generated.contains("private static boolean _matchesSchema"), generated);
        assertTrue(generated.contains("matchCount"), generated);
        assertFalse(generated.contains("_ITEM.isValid("), generated);
        assertFalse(generated.contains(".ROOT.isValid("), generated);
    }

    @Test
    void generatesNativeTypedOperationsAndNodesOnlyForObjectProperties() throws Exception {
        String model = "package fixture; import java.math.BigDecimal; import java.util.List; "
                + "public final class Model { "
                + "public String getText() { return \"aa\"; } "
                + "public BigDecimal getDecimal() { return BigDecimal.ONE; } "
                + "public List<String> getWords() { return List.of(\"aa\"); } "
                + "public Object getUnknown() { return \"aa\"; } }";
        String schema = "{\"type\":\"object\",\"properties\":{"
                + "\"text\":{\"type\":\"string\",\"minLength\":2},"
                + "\"decimal\":{\"type\":\"number\",\"minimum\":1},"
                + "\"words\":{\"type\":\"array\",\"items\":{\"type\":\"string\",\"minLength\":2}},"
                + "\"unknown\":{\"type\":\"string\",\"maxLength\":4}}}";
        String source = "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator(fallback=false) public interface Check { "
                + "@ValidJsonSchema(" + org.sjf4j.processor.code.JavaWriter.stringLiteral(schema)
                + ") boolean valid(Model value); }";
        NavigatorTestCompiler.Result result = NavigatorTestCompiler.compile(Map.of(
                "fixture/Model.java", model, "fixture/Check.java", source),
                CodegenProcessor.class);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Check_Impl.java");

        assertTrue(generated.contains("getText()") && generated.contains(".codePointCount("), generated);
        assertTrue(generated.contains("getDecimal()") && generated.contains("Numbers.compare("), generated);
        assertTrue(generated.contains("for (") && generated.contains(" item"), generated);
        assertFalse(generated.contains("instanceof String"), generated);
        assertFalse(generated.contains("instanceof Number"), generated);
        assertTrue(generated.contains("Nodes.toString("), generated);
        assertFalse(generated.contains("org.sjf4j.Nodes.toNumber("), generated);
        assertFalse(generated.contains(".ROOT.isValid("), generated);
    }

    @Test
    void annotatedStrictFormatMethodResolvesBothReflectionParameters() throws Exception {
        String schema = "{\"type\":\"object\",\"properties\":{\"name\":{\"format\":\"email\"}}}";
        NavigatorTestCompiler.Result result = compile(
                "@CompiledValidator(fallback=true) public interface Check { "
                        + "@ValidJsonSchema(" + org.sjf4j.processor.code.JavaWriter.stringLiteral(schema) + ") "
                        + "boolean valid(Model value, boolean strictFormat); }");
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Check_Impl.java");
        // The format keyword now compiles independently; the method keeps
        // the strictFormat parameter without installing a reflection fallback.
        assertTrue(generated.contains("public boolean valid(Model value, boolean strictFormat)"), generated);
        assertTrue(generated.contains("FormatValidator.of(\"email\")"), generated);
        assertFalse(generated.contains("getMethod(\"valid\""), generated);
    }

    private static NavigatorTestCompiler.Result compile(String source) throws Exception {
        return NavigatorTestCompiler.compile(Map.of(
                "fixture/Model.java", MODEL,
                "fixture/Check.java", "package fixture; "
                        + "import org.sjf4j.annotation.schema.*; " + source),
                CodegenProcessor.class);
    }
}
