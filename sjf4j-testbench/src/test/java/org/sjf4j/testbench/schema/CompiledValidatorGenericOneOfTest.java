package org.sjf4j.testbench.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaPlan;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** General JSON Schema oneOf: exactly one matching branch, even without a discriminator. */
class CompiledValidatorGenericOneOfTest {
    private static final String OVERLAPPING = """
            {"type":"object","properties":{"number":{"oneOf":[
              {"type":"integer","minimum":0,"maximum":20},
              {"type":"integer","minimum":10,"maximum":30}]}}}
            """;
    private static final String ROOT_OBJECT = """
            {"oneOf":[
              {"type":"object","required":["x"],"properties":{"x":{"type":"integer"}}},
              {"type":"object","required":["y"],"properties":{"y":{"type":"integer"}}}
            ]}
            """;
    private static final String ARRAY_ITEMS = """
            {"type":"object","properties":{"values":{"type":"array","items":{"oneOf":[
              {"type":"string","minLength":2},{"const":null}]}}}}
            """;
    private static final String REFS = """
            {"oneOf":[{"$ref":"#/$defs/positive"},{"$ref":"#/$defs/underTen"}],
             "$defs":{
               "positive":{"type":"integer","minimum":0},
               "underTen":{"type":"integer","maximum":10}
             }}
            """;
    private static final String NESTED = """
            {"oneOf":[
              {"oneOf":[{"const":1},{"const":2},{"const":3}]},
              {"const":3}
            ]}
            """;
    private static final String BOOLEANS = """
            {"oneOf":[false,{"const":"yes"}]}
            """;
    private static final String TWO_TRUE = """
            {"oneOf":[true,true]}
            """;
    private static final String FORMATS = """
            {"type":"object","properties":{"text":{"oneOf":[
              {"type":"string","format":"uuid"},{"const":"fallback"}]}}}
            """;

    public static final class NumberModel {
        private final Integer number;
        public NumberModel(@NodeProperty("number") Integer number) { this.number = number; }
        public Integer getNumber() { return number; }
    }

    public static final class ArrayModel {
        private final List<Object> values;
        public ArrayModel(@NodeProperty("values") List<Object> values) { this.values = values; }
        public List<Object> getValues() { return values; }
    }

    public static final class TextModel {
        private final String text;
        public TextModel(@NodeProperty("text") String text) { this.text = text; }
        public String getText() { return text; }
    }

    @CompiledValidator(fallback = false)
    public interface Direct {
        @ValidJsonSchema(OVERLAPPING) boolean overlapping(NumberModel model);
        @ValidJsonSchema(ROOT_OBJECT) boolean rootObject(Map<String, Object> input);
        @ValidJsonSchema(ARRAY_ITEMS) boolean arrayItems(ArrayModel input);
        @ValidJsonSchema(REFS) boolean refs(Integer input);
        @ValidJsonSchema(NESTED) boolean nested(Integer input);
        @ValidJsonSchema(BOOLEANS) boolean booleans(String input);
        @ValidJsonSchema(TWO_TRUE) boolean twoTrue(String input);
        @ValidJsonSchema(FORMATS) boolean formats(TextModel input, boolean strictFormat);
    }

    @Test
    void supportsOverlappingBranchesWithoutDiscriminator() {
        Direct compiled = CompiledInstances.create(Direct.class);
        SchemaPlan reference = JsonSchema.fromJson(OVERLAPPING).createPlan();
        for (int value : new int[]{-1, 0, 5, 10, 15, 20, 25, 30, 31}) {
            NumberModel model = new NumberModel(value);
            assertEquals(reference.isValid(model), compiled.overlapping(model),
                    "value=" + value);
        }
        assertTrue(compiled.overlapping(new NumberModel(5)));
        assertFalse(compiled.overlapping(new NumberModel(15)));
        assertTrue(compiled.overlapping(new NumberModel(25)));
        assertFalse(compiled.overlapping(new NumberModel(31)));
    }

    @Test
    void compilesRootObjectBranchesAndCountsAllMatchingBranches() {
        Direct compiled = CompiledInstances.create(Direct.class);
        SchemaPlan reference = JsonSchema.fromJson(ROOT_OBJECT).createPlan();
        for (Map<String, Object> map : Arrays.asList(
                Map.<String, Object>of("x", 1), Map.<String, Object>of("y", 2), Map.<String, Object>of("x", 1, "y", 2),
                Map.<String, Object>of(), Map.<String, Object>of("x", "wrong"), Map.<String, Object>of("y", 2, "x", "wrong")
        )) {
            assertEquals(reference.isValid(map), compiled.rootObject(map), map.toString());
        }
    }

    @Test
    void compilesNestedArrayBranchesIncludingNullLiteral() {
        Direct compiled = CompiledInstances.create(Direct.class);
        SchemaPlan reference = JsonSchema.fromJson(ARRAY_ITEMS).createPlan();
        for (ArrayModel model : Arrays.asList(
                new ArrayModel(List.of("ab")),
                new ArrayModel(Arrays.asList((Object) null)),
                new ArrayModel(List.of("x")),
                new ArrayModel(List.of(5)),
                new ArrayModel(Arrays.asList("ok", null, "valid"))
        )) {
            assertEquals(reference.isValid(model), compiled.arrayItems(model),
                    String.valueOf(model.getValues()));
        }
    }

    @Test
    void resolvesRefsAndNestedOneOfWithoutSchemaPlan() {
        Direct compiled = CompiledInstances.create(Direct.class);
        SchemaPlan refs = JsonSchema.fromJson(REFS).createPlan();
        SchemaPlan nested = JsonSchema.fromJson(NESTED).createPlan();
        for (int value : new int[]{-1, 1, 3, 10, 11, 50}) {
            assertEquals(refs.isValid(value), compiled.refs(value), "ref " + value);
            assertEquals(nested.isValid(value), compiled.nested(value), "nested " + value);
        }
        assertFalse(compiled.refs(1));
        assertFalse(compiled.nested(3));
        assertTrue(compiled.nested(2));
    }

    @Test
    void handlesBooleanSchemasAndStrictFormatBranches() {
        Direct compiled = CompiledInstances.create(Direct.class);
        SchemaPlan booleans = JsonSchema.fromJson(BOOLEANS).createPlan();
        SchemaPlan twoTrue = JsonSchema.fromJson(TWO_TRUE).createPlan();
        for (String s : new String[]{"yes", "other"}) {
            assertEquals(booleans.isValid(s), compiled.booleans(s));
            assertEquals(twoTrue.isValid(s), compiled.twoTrue(s));
        }

        SchemaPlan reference = JsonSchema.fromJson(FORMATS).createPlan();
        for (String value : new String[]{"not-a-uuid", "fallback",
                "123e4567-e89b-12d3-a456-426614174000"}) {
            TextModel model = new TextModel(value);
            for (boolean strict : new boolean[]{false, true}) {
                assertEquals(reference.isValid(model, strict), compiled.formats(model, strict),
                        value + " strict=" + strict);
            }
        }
    }
}
