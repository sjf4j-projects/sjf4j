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

/** Boolean applicators remain equivalent to runtime SchemaPlan, including overlap. */
class CompiledValidatorApplicatorTest {
    private static final String ALL_OBJECT = """
            {"allOf":[
              {"type":"object","required":["x"],
               "properties":{"x":{"type":"integer","minimum":1}}},
              {"type":"object","properties":{"y":{"type":"string","minLength":2}}}
            ]}
            """;
    private static final String ANY_OVERLAP = """
            {"anyOf":[
              {"type":"integer","minimum":0,"maximum":20},
              {"type":"integer","minimum":10,"maximum":30}
            ]}
            """;
    private static final String NOT_LITERAL = """
            {"not":{"const":"blocked"}}
            """;
    private static final String SIBLINGS = """
            {"type":"object","properties":{"number":{
              "type":"integer","minimum":0,
              "allOf":[{"type":"integer","maximum":30}],
              "anyOf":[{"const":2},{"const":4}],
              "not":{"const":4}
            }}}
            """;
    private static final String ARRAY = """
            {"type":"array","items":{"anyOf":[{"const":"ok"},{"const":null}]}}
            """;
    private static final String NESTED = """
            {"not":{"oneOf":[{"const":1},{"const":2}]}}
            """;
    private static final String BOOLEAN = """
            {"allOf":[true,{"not":false}]}
            """;
    private static final String ALL_FALSE = """
            {"allOf":[true,false,{"const":"yes"}]}
            """;
    private static final String ANY_FALSE = """
            {"anyOf":[false,{"const":"yes"}]}
            """;
    private static final String FORMAT = """
            {"anyOf":[{"type":"string","format":"uuid"},{"const":"fallback"}]}
            """;
    private static final String REF = """
            {"allOf":[{"$ref":"#/$defs/positive"},{"$ref":"#/$defs/underThirty"}],
             "$defs":{
               "positive":{"type":"integer","minimum":0},
               "underThirty":{"type":"integer","maximum":30}
             }}
            """;
    private static final String NULL_MISSING = """
            {"not":{"type":"object","required":["x"],"properties":{"x":true}}}
            """;
    private static final String FALLBACK = """
            {"anyOf":[{"type":"string","pattern":"^a"},{"const":"ok"}]}
            """;
    private static final String UNEVALUATED = """
            {"allOf":[{"type":"object","required":["x"],"properties":{"x":true}}],
             "unevaluatedProperties":false}
            """;

    public static final class NumberModel {
        private final Integer number;
        public NumberModel(@NodeProperty("number") Integer number) { this.number = number; }
        public Integer getNumber() { return number; }
    }

    @CompiledValidator(fallback = false)
    public interface Direct {
        @ValidJsonSchema(ALL_OBJECT) boolean all(Map<String, Object> input);
        @ValidJsonSchema(ANY_OVERLAP) boolean any(Integer input);
        @ValidJsonSchema(NOT_LITERAL) boolean not(String input);
        @ValidJsonSchema(SIBLINGS) boolean siblings(NumberModel input);
        @ValidJsonSchema(ARRAY) boolean array(List<Object> input);
        @ValidJsonSchema(NESTED) boolean nested(Integer input);
        @ValidJsonSchema(BOOLEAN) boolean booleanTrue(String input);
        @ValidJsonSchema(ALL_FALSE) boolean allFalse(String input);
        @ValidJsonSchema(ANY_FALSE) boolean anyFalse(String input);
        @ValidJsonSchema(FORMAT) boolean format(String input, boolean strictFormat);
        @ValidJsonSchema(REF) boolean refs(Integer input);
    }

    @CompiledValidator(fallback = false)
    public interface NullMemberApplicator {
        @ValidJsonSchema(NULL_MISSING) boolean notRequired(Map<String, Object> input);
    }

    @CompiledValidator
    public interface Fallback {
        @ValidJsonSchema(FALLBACK) boolean unsupported(String input);
        @ValidJsonSchema(UNEVALUATED) boolean unevaluated(Map<String, Object> input);
    }

    @Test
    void allOfChecksAllBranchesAndObjectProperties() {
        Direct compiled = CompiledInstances.create(Direct.class);
        SchemaPlan reference = JsonSchema.fromJson(ALL_OBJECT).createPlan();
        for (Map<String, Object> map : Arrays.asList(
                Map.<String, Object>of("x", 1),
                Map.<String, Object>of("x", 2, "y", "ok"),
                Map.<String, Object>of("x", 0),
                Map.<String, Object>of("x", 2, "y", "x"),
                Map.<String, Object>of("y", "ok"),
                Map.<String, Object>of())) {
            assertEquals(reference.isValid(map), compiled.all(map), map.toString());
        }
    }

    @Test
    void anyOfAcceptsMultipleMatchesAndShortCircuitsCorrectly() {
        Direct compiled = CompiledInstances.create(Direct.class);
        SchemaPlan reference = JsonSchema.fromJson(ANY_OVERLAP).createPlan();
        for (int n : new int[]{-1, 0, 1, 9, 10, 15, 20, 25, 30, 31}) {
            assertEquals(reference.isValid(n), compiled.any(n), "anyOf: " + n);
        }
        assertTrue(compiled.any(15));
        assertFalse(compiled.any(-1));
    }

    @Test
    void notInvertsEntireSubschemaAndHandlesBooleanSchemas() {
        Direct compiled = CompiledInstances.create(Direct.class);
        SchemaPlan reference = JsonSchema.fromJson(NOT_LITERAL).createPlan();
        for (String s : new String[]{"blocked", "ok", ""}) {
            assertEquals(reference.isValid(s), compiled.not(s), s);
            assertTrue(compiled.booleanTrue(s));
            assertFalse(compiled.allFalse(s));
            assertEquals(JsonSchema.fromJson(ANY_FALSE).createPlan().isValid(s),
                    compiled.anyFalse(s));
        }
        SchemaPlan nested = JsonSchema.fromJson(NESTED).createPlan();
        for (int n : new int[]{0, 1, 2, 3}) {
            assertEquals(nested.isValid(n), compiled.nested(n), "not(oneOf): " + n);
        }
    }

    @Test
    void applicatorsAndSiblingKeywordsAreConjoined() {
        Direct compiled = CompiledInstances.create(Direct.class);
        SchemaPlan reference = JsonSchema.fromJson(SIBLINGS).createPlan();
        for (int n : new int[]{-1, 0, 2, 4, 6, 31}) {
            NumberModel model = new NumberModel(n);
            assertEquals(reference.isValid(model), compiled.siblings(model), "number: " + n);
        }
        assertTrue(compiled.siblings(new NumberModel(2)));
        assertFalse(compiled.siblings(new NumberModel(4)));
    }

    @Test
    void collectionItemsRefsAndStrictFormatRetainRuntimeBehavior() {
        Direct compiled = CompiledInstances.create(Direct.class);
        SchemaPlan array = JsonSchema.fromJson(ARRAY).createPlan();
        for (List<Object> items : Arrays.asList(
                List.<Object>of("ok"), Arrays.<Object>asList((Object) null),
                List.<Object>of("bad"), List.<Object>of(123),
                Arrays.<Object>asList("ok", null))) {
            assertEquals(array.isValid(items), compiled.array(items), items.toString());
        }
        SchemaPlan refs = JsonSchema.fromJson(REF).createPlan();
        for (int n : new int[]{-1, 0, 12, 30, 31}) {
            assertEquals(refs.isValid(n), compiled.refs(n), "ref: " + n);
        }
        SchemaPlan format = JsonSchema.fromJson(FORMAT).createPlan();
        for (String s : new String[]{"not-a-uuid", "fallback",
                "123e4567-e89b-12d3-a456-426614174000"}) {
            for (boolean strict : new boolean[]{false, true}) {
                assertEquals(format.isValid(s, strict), compiled.format(s, strict),
                        s + " strict=" + strict);
            }
        }
    }

    @Test
    void nullMemberPresenceAppliesInsideApplicatorBranches() {
        NullMemberApplicator compiled = CompiledInstances.create(NullMemberApplicator.class);
        Map<String, Object> data = new LinkedHashMap<>();
        assertTrue(compiled.notRequired(data));
        data.put("x", null);
        assertFalse(compiled.notRequired(data)); // A null-valued member is present.
        data.put("x", 1);
        assertFalse(compiled.notRequired(data));
    }

    @Test
    void unsupportedBranchAndUnevaluatedScopeFallBackAsAWhole() {
        Fallback compiled = CompiledInstances.create(Fallback.class);
        SchemaPlan regex = JsonSchema.fromJson(FALLBACK).createPlan();
        for (String s : new String[]{"a", "abc", "ok", "no"}) {
            assertEquals(regex.isValid(s), compiled.unsupported(s), s);
        }
        SchemaPlan evaluated = JsonSchema.fromJson(UNEVALUATED).createPlan();
        for (Map<String, Object> map : Arrays.asList(
                Map.<String, Object>of("x", 1),
                Map.<String, Object>of("x", 1, "y", 2),
                Map.<String, Object>of("y", 1))) {
            assertEquals(evaluated.isValid(map), compiled.unevaluated(map), map.toString());
        }
    }
}
