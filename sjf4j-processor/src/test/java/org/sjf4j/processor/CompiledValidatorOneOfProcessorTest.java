package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.processor.code.JavaWriter;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Direct oneOf dispatch requires public and provably constant discriminator members. */
class CompiledValidatorOneOfProcessorTest {
    private static final String SCHEMA = "{\"type\":\"object\",\"properties\":{\"items\":{\"type\":\"array\",\"items\":{\"oneOf\":[{\"$ref\":\"#/$defs/a\"},{\"$ref\":\"#/$defs/b\"}]}}},\"$defs\":{\"a\":{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"kind\",\"name\"],\"properties\":{\"kind\":{\"type\":\"string\",\"enum\":[\"a\"]},\"name\":{\"type\":\"string\",\"minLength\":2}}},\"b\":{\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"kind\",\"count\"],\"properties\":{\"kind\":{\"type\":\"string\",\"enum\":[\"b\"]},\"count\":{\"type\":\"integer\",\"minimum\":1}}}}}";

    @Test
    void compilesDisjointPublicConstantSubtypesWithoutItemPlanOnFastPath() throws Exception {
        NavigatorTestCompiler.Result result = compile(true);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Validator_Impl.java");
        assertTrue(generated.contains("getClass() == A.class")
                || generated.contains("getClass() == fixture.A.class"), generated);
        assertTrue(generated.contains("getClass() == B.class")
                || generated.contains("getClass() == fixture.B.class"), generated);
        assertTrue(generated.contains(".getName()"), generated);
        assertTrue(generated.contains(".getCount()"), generated);
        assertFalse(generated.contains("SUB_0_ITEM.isValid("), generated);
        assertTrue(generated.contains("matchCount"), generated);
    }

    @Test
    void strictFormatKeepsDirectSubtypeDispatch() throws Exception {
        String schema = SCHEMA.replace("\"minLength\":2",
                "\"minLength\":2,\"format\":\"email\"");
        NavigatorTestCompiler.Result result = compile(true, schema, true);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Validator_Impl.java");
        assertTrue(generated.contains("getClass() == A.class")
                || generated.contains("getClass() == fixture.A.class"), generated);
        assertTrue(generated.contains("FormatValidator.of(\"email\")"), generated);
        assertFalse(generated.contains(".isValid(value, true)"), generated);
        assertTrue(generated.contains("matchCount"), generated);
    }

    @Test
    void fullyCompiledOneOfDoesNotRequireSchemaPlan() throws Exception {
        NavigatorTestCompiler.Result result = compile(true, SCHEMA, false, false);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Validator_Impl.java");
        assertTrue(generated.contains("getClass() =="), generated);
        assertTrue(generated.contains("matchCount"), generated);
        assertFalse(generated.contains(".isValid("), generated);
    }

    @Test
    void hiddenNodeMembersUseDynamicSubtypePredicates() throws Exception {
        NavigatorTestCompiler.Result result = compile(true, SCHEMA, false, false, true);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Validator_Impl.java");
        assertTrue(generated.contains("getClass() =="), generated);
        assertTrue(generated.contains("matchCount"), generated);
        assertFalse(generated.contains(".isValid("), generated);
    }

    @Test
    void nonPublicAnnotatedConstantUsesDynamicSubtypePredicates() throws Exception {
        NavigatorTestCompiler.Result result = compile(false);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Validator_Impl.java");
        assertTrue(generated.contains("getClass() =="), generated);
        assertFalse(generated.contains(".isValid("), generated);
        assertTrue(generated.contains("keySetInObject("), generated);
    }

    @Test
    void getterDiscriminatorsCompileAllPotentialOneOfBranches() throws Exception {
        // Both subtypes expose exactly the same keys. Without a provable
        // discriminator constant neither branch can be eliminated.
        String schema = SCHEMA.replace("count", "name")
                .replace("integer", "string").replace("minimum", "minLength");
        NavigatorTestCompiler.Result result = compile(true, schema, false, false, false, true);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Validator_Impl.java");
        assertTrue(generated.contains("getClass() =="), generated);
        assertTrue(generated.contains("getKind()"), generated);
        assertTrue(generated.contains("\"a\".equals("), generated);
        assertTrue(generated.contains("\"b\".equals("), generated);
        // Two per-subtype branches, plus the generic unknown-subtype path.
        assertTrue(generated.split(java.util.regex.Pattern.quote("getKind()"), -1).length - 1 >= 4, generated);
        assertTrue(generated.contains("matchCount"), generated);
        assertFalse(generated.contains(".isValid("), generated);
    }

    @Test
    void overlappingDiscriminatorBranchesCannotUseGuardedDispatch() throws Exception {
        // Two branches requiring the same tag cannot be treated as disjoint,
        // even when @OneOfMapping claims different subtype tags.
        String schema = SCHEMA.replace("\"enum\":[\"b\"]", "\"enum\":[\"a\"]");
        NavigatorTestCompiler.Result result = compile(true, schema, false, false, false, true);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Validator_Impl.java");
        assertTrue(generated.contains("getClass() =="), generated);
        assertFalse(generated.contains("\"a\".equals("), generated);
        assertFalse(generated.contains("\"b\".equals("), generated);
        assertTrue(generated.contains("matchCount"), generated);
    }

    @Test
    void privateAnnotatedFieldWithPublicGetterIsOneReadableDiscriminator() throws Exception {
        NavigatorTestCompiler.Result result = compile(true, SCHEMA, false, false, false, true, true);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Validator_Impl.java");
        assertTrue(generated.contains("getClass() =="), generated);
        assertTrue(generated.contains("getKind()"), generated);
        assertTrue(generated.contains("matchCount"), generated);
        assertFalse(generated.contains(".isValid("), generated);
    }

    @Test
    void privateBackingFieldRenamesPublicGetterToDiscriminatorKey() throws Exception {
        String schema = SCHEMA.replace("kind", "@type");
        NavigatorTestCompiler.Result result = compile(true, schema, false, false,
                false, true, true, "@type");
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Validator_Impl.java");
        assertTrue(generated.contains("getClass() =="), generated);
        assertTrue(generated.contains("getKind()"), generated);
        assertTrue(generated.contains("matchCount"), generated);
        assertFalse(generated.contains(".isValid("), generated);
    }

    private static NavigatorTestCompiler.Result compile(boolean exposed) throws Exception {
        return compile(exposed, SCHEMA, false);
    }

    private static NavigatorTestCompiler.Result compile(boolean exposed, String schema,
                                                        boolean strictFormat) throws Exception {
        return compile(exposed, schema, strictFormat, true);
    }

    private static NavigatorTestCompiler.Result compile(boolean exposed, String schema,
                                                        boolean strictFormat, boolean fallback) throws Exception {
        return compile(exposed, schema, strictFormat, fallback, false);
    }

    private static NavigatorTestCompiler.Result compile(boolean exposed, String schema,
                                                        boolean strictFormat, boolean fallback,
                                                        boolean hiddenNodeMember) throws Exception {
        return compile(exposed, schema, strictFormat, fallback, hiddenNodeMember, false);
    }

    private static NavigatorTestCompiler.Result compile(boolean exposed, String schema,
                                                        boolean strictFormat, boolean fallback,
                                                        boolean hiddenNodeMember, boolean getterDiscriminator) throws Exception {
        return compile(exposed, schema, strictFormat, fallback, hiddenNodeMember, getterDiscriminator, false);
    }

    private static NavigatorTestCompiler.Result compile(boolean exposed, String schema,
                                                        boolean strictFormat, boolean fallback,
                                                        boolean hiddenNodeMember, boolean getterDiscriminator,
                                                        boolean privateBacking) throws Exception {
        return compile(exposed, schema, strictFormat, fallback, hiddenNodeMember,
                getterDiscriminator, privateBacking, "kind");
    }

    private static NavigatorTestCompiler.Result compile(boolean exposed, String schema,
                                                        boolean strictFormat, boolean fallback,
                                                        boolean hiddenNodeMember, boolean getterDiscriminator,
                                                        boolean privateBacking, String discriminatorName) throws Exception {
        String visibility = exposed ? "public " : "private ";
        Map<String, String> sources = new LinkedHashMap<String, String>();
        sources.put("fixture/Variant.java",
                "package fixture; import org.sjf4j.annotation.node.*; "
                + "@OneOf(discriminatorKey=\"" + discriminatorName + "\", mappings={"
                + "@OneOfMapping(type=A.class,when=\"a\"),"
                + "@OneOfMapping(type=B.class,when=\"b\")}) "
                + "public interface Variant {}");
        sources.put("fixture/A.java",
                "package fixture; import org.sjf4j.annotation.node.*; "
                + "public final class A implements Variant { "
                + (privateBacking ? "@NodeProperty(\"" + discriminatorName + "\") private final String kind = \"a\"; "
                        + "public String getKind(){return kind;} "
                        : getterDiscriminator ? "@NodeProperty(\"kind\") public String getKind(){return \"a\";} "
                        : "@NodeProperty(\"kind\") " + visibility + "final String kind = \"a\"; ")
                + (hiddenNodeMember ? "@NodeIgnore private String hidden = \"x\"; " : "")
                + "public String getName() {return \"good\";} }");
        sources.put("fixture/B.java",
                "package fixture; import org.sjf4j.annotation.node.*; "
                + "public final class B implements Variant { "
                + (privateBacking ? "@NodeProperty(\"" + discriminatorName + "\") private final String kind = \"b\"; "
                        + "public String getKind(){return kind;} "
                        : getterDiscriminator ? "@NodeProperty(\"kind\") public String getKind(){return \"b\";} "
                        : "@NodeProperty(\"kind\") public final String kind = \"b\"; ")
                + (getterDiscriminator ? "public String getName(){return \"good\";} " : "")
                + (getterDiscriminator ? "" : "public int getCount() {return 2;} ") + "}");
        sources.put("fixture/Model.java",
                "package fixture; import java.util.List; public final class Model { "
                + "public List<Variant> getItems() { return java.util.Arrays.<Variant>asList(new A(),new B()); }}");
        sources.put("fixture/Validator.java",
                "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator(fallback=" + fallback + ") public interface Validator { "
                + "@ValidJsonSchema(" + JavaWriter.stringLiteral(schema) + ") "
                + (strictFormat ? "boolean valid(Model value, boolean strictFormat); }"
                        : "boolean valid(Model value); }"));
        return NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
    }
}
