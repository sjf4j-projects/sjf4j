package org.sjf4j.processor;

import org.junit.jupiter.api.Test;
import org.sjf4j.processor.code.JavaWriter;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Direct oneOf dispatch requires public and provably constant JSON discriminator members. */
class CompiledValidatorOneOfProcessorTest {
    private static final String SCHEMA = """
            {"type":"object","properties":{"items":{"type":"array","items":{"oneOf":[
              {"$ref":"#/$defs/a"},{"$ref":"#/$defs/b"}]}}},
             "$defs":{
               "a":{"type":"object","additionalProperties":false,
                    "required":["kind","name"],"properties":{
                    "kind":{"type":"string","enum":["a"]},
                    "name":{"type":"string","minLength":2}}},
               "b":{"type":"object","additionalProperties":false,
                    "required":["kind","count"],"properties":{
                    "kind":{"type":"string","enum":["b"]},
                    "count":{"type":"integer","minimum":1}}}}
            }
            """;

    @Test
    void compilesDisjointPublicConstantSubtypesWithoutItemPlanOnFastPath() throws Exception {
        NavigatorTestCompiler.Result result = compile(true);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Validator_Impl.java");
        assertTrue(generated.matches("(?s).*getClass\\(\\) == (fixture\\.)?A\\.class.*"), generated);
        assertTrue(generated.matches("(?s).*getClass\\(\\) == (fixture\\.)?B\\.class.*"), generated);
        assertTrue(generated.contains(".getName()"), generated);
        assertTrue(generated.contains(".getCount()"), generated);
        assertTrue(generated.contains("SUB_0_ITEM.isValid("), generated);
    }

    @Test
    void nonPublicAnnotatedConstantDoesNotGrantGeneratedJavaAccess() throws Exception {
        NavigatorTestCompiler.Result result = compile(false);
        assertTrue(result.success, result.diagnostics());
        String generated = result.generatedSource("fixture/Validator_Impl.java");
        assertFalse(generated.contains("getClass() =="), generated);
        assertTrue(generated.contains(".isValid("), generated);
    }

    private static NavigatorTestCompiler.Result compile(boolean exposed) throws Exception {
        String visibility = exposed ? "public " : "private ";
        Map<String, String> sources = new LinkedHashMap<>();
        sources.put("fixture/Variant.java", """
                package fixture;
                import org.sjf4j.annotation.node.*;
                @OneOf(discriminatorKey="kind", mappings={
                    @OneOfMapping(type=A.class,when="a"),
                    @OneOfMapping(type=B.class,when="b")
                })
                public interface Variant { }
                """);
        sources.put("fixture/A.java", """
                package fixture;
                import org.sjf4j.annotation.node.*;
                public final class A implements Variant {
                    @NodeProperty("kind") %sfinal String kind = "a";
                    public String getName() { return "good"; }
                }
                """.formatted(visibility));
        sources.put("fixture/B.java", """
                package fixture;
                import org.sjf4j.annotation.node.*;
                public final class B implements Variant {
                    @NodeProperty("kind") public final String kind = "b";
                    public int getCount() { return 2; }
                }
                """);
        sources.put("fixture/Model.java", """
                package fixture;
                import java.util.List;
                public final class Model {
                    public List<Variant> getItems() { return List.of(new A(), new B()); }
                }
                """);
        sources.put("fixture/Validator.java",
                "package fixture; import org.sjf4j.annotation.schema.*; "
                + "@CompiledValidator public interface Validator { "
                + "@ValidJsonSchema(" + JavaWriter.stringLiteral(SCHEMA) + ") "
                + "boolean valid(Model value); }");
        return NavigatorTestCompiler.compile(sources, CodegenProcessor.class);
    }
}
