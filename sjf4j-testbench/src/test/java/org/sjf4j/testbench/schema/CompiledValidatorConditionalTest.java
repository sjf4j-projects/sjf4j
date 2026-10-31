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

/** Differential tests against the runtime conditional evaluator. */
class CompiledValidatorConditionalTest {
    private static final String ADDRESS = """
            {"if":{"required":["country"],"properties":{"country":{"const":"US"}}},
             "then":{"required":["state"]},
             "else":{"required":["postalCode"]}}
            """;
    private static final String TYPED = """
            {"if":{"properties":{"country":{"const":"US"}}},
             "then":{"properties":{"state":{"type":"string","minLength":2}}},
             "else":{"properties":{"postalCode":{"type":"string","minLength":4}}}}
            """;
    private static final String DYNAMIC = """
            {"if":{"required":["x"]},
             "then":{"required":["y"]},
             "else":{"not":{"const":"blocked"}}}
            """;
    private static final String SIBLINGS = """
            {"type":"object","properties":{"code":{"type":"string","minLength":2}},
             "if":{"required":["country"],"properties":{"country":{"const":"US"}}},
             "then":{"required":["state"]},"else":{"required":["postalCode"]},
             "allOf":[{"not":{"required":["forbidden"]}}]}
            """;
    private static final String IN_ARRAY = """
            {"type":"array","items":{
             "if":{"type":"integer","minimum":0},
             "then":{"type":"integer","maximum":10},
             "else":{"not":{"const":"bad"}}}}
            """;
    private static final String IF_ONLY = """
            {"if":{"type":"string","pattern":"^a"}}
            """;
    private static final String NO_IF = """
            {"then":false,"else":{"type":"integer","multipleOf":3}}
            """;
    private static final String TRUE_IF = """
            {"if":true,"then":{"const":3},
             "else":{"type":"integer","multipleOf":2}}
            """;
    private static final String FALSE_IF = """
            {"if":false,"then":{"type":"integer","multipleOf":2},
             "else":{"const":3}}
            """;
    private static final String FORMAT = """
            {"if":{"type":"string","format":"uuid"},
             "then":{"const":"123e4567-e89b-12d3-a456-426614174000"},
             "else":{"not":{"const":"blocked"}}}
            """;
    private static final String UNEVALUATED = """
            {"if":{"required":["x"]},"then":{"properties":{"x":true}},
             "else":{"properties":{"y":true}},"unevaluatedProperties":false}
            """;
    private static final String UNSUPPORTED = """
            {"if":{"type":"string","pattern":"^a"},
             "then":{"minLength":3},"else":{"const":"z"}}
            """;

    public static final class Person {
        private final String country;
        private final String state;
        private final String postalCode;
        public Person(@NodeProperty("country") String country,
                      @NodeProperty("state") String state,
                      @NodeProperty("postalCode") String postalCode) {
            this.country = country;
            this.state = state;
            this.postalCode = postalCode;
        }
        public String getCountry() { return country; }
        public String getState() { return state; }
        public String getPostalCode() { return postalCode; }
    }

    @CompiledValidator(fallback = false)
    public interface Direct {
        @ValidJsonSchema(ADDRESS) boolean address(Map<String, Object> value);
        @ValidJsonSchema(TYPED) boolean typed(Person value);
        @ValidJsonSchema(DYNAMIC) boolean dynamic(Object value);
        @ValidJsonSchema(SIBLINGS) boolean siblings(Map<String, Object> value);
        @ValidJsonSchema(IN_ARRAY) boolean array(List<Object> value);
        @ValidJsonSchema(IF_ONLY) boolean ifOnly(String value);
        @ValidJsonSchema(NO_IF) boolean noIf(String value);
        @ValidJsonSchema(TRUE_IF) boolean trueIf(Integer value);
        @ValidJsonSchema(FALSE_IF) boolean falseIf(Integer value);
        @ValidJsonSchema(FORMAT) boolean format(String value, boolean strictFormat);
    }

    @CompiledValidator(fallback = false)
    public interface NullableMember {
        @ValidJsonSchema(ADDRESS) boolean address(Map<String, Object> value);
    }

    @CompiledValidator
    public interface Runtime {
        @ValidJsonSchema(UNEVALUATED) boolean unevaluated(Map<String, Object> value);
        @ValidJsonSchema(UNSUPPORTED) boolean unsupported(String value);
    }

    @Test
    void validatesTypeLessObjectConditionsAndMissingBranches() {
        Direct compiled = CompiledInstances.create(Direct.class);
        SchemaPlan reference = JsonSchema.fromJson(ADDRESS).createPlan();
        for (Map<String,Object> map : Arrays.asList(
                Map.<String,Object>of("country","US","state","CA"),
                Map.<String,Object>of("country","US"),
                Map.<String,Object>of("country","CN","postalCode","10000"),
                Map.<String,Object>of("country","CN"),
                Map.<String,Object>of("postalCode","10000"),
                Map.<String,Object>of(),
                Map.<String,Object>of("country","US","postalCode","12345")
        )) {
            assertEquals(reference.isValid(map), compiled.address(map), map.toString());
        }
    }

    @Test
    void typedPojoAndDynamicObjectsAgreeWithSchemaPlan() {
        Direct compiled = CompiledInstances.create(Direct.class);
        SchemaPlan typed = JsonSchema.fromJson(TYPED).createPlan();
        for (Person p : Arrays.asList(
                new Person("US","CA",null), new Person("US","X",null),
                new Person("CN",null,"10000"), new Person("CN",null,"1"))) {
            assertEquals(typed.isValid(p), compiled.typed(p), String.valueOf(p.getCountry()));
        }
        SchemaPlan dynamic = JsonSchema.fromJson(DYNAMIC).createPlan();
        for (Object value : Arrays.asList(
                Map.<String,Object>of("x",1,"y",2),
                Map.<String,Object>of("x",1),
                Map.<String,Object>of("y",2),
                "blocked", "other", 42, List.of("x"))) {
            assertEquals(dynamic.isValid(value), compiled.dynamic(value), String.valueOf(value));
        }
    }

    @Test
    void combinesSiblingsAndConditionalsWithinItems() {
        Direct compiled = CompiledInstances.create(Direct.class);
        SchemaPlan siblings = JsonSchema.fromJson(SIBLINGS).createPlan();
        for (Map<String,Object> map : Arrays.asList(
                Map.<String,Object>of("code","ok","country","US","state","CA"),
                Map.<String,Object>of("code","x","country","US","state","CA"),
                Map.<String,Object>of("code","ok","country","US"),
                Map.<String,Object>of("code","ok","country","CN","postalCode","10000"),
                Map.<String,Object>of("code","ok","country","US","state","CA","forbidden",1))) {
            assertEquals(siblings.isValid(map), compiled.siblings(map), map.toString());
        }
        SchemaPlan array = JsonSchema.fromJson(IN_ARRAY).createPlan();
        for (List<Object> list : Arrays.asList(
                List.<Object>of(0,10), List.<Object>of(11), List.<Object>of(-1),
                List.<Object>of("bad"), List.<Object>of("fine", -1),
                Arrays.<Object>asList(3,null))) {
            assertEquals(array.isValid(list), compiled.array(list), list.toString());
        }
    }

    @Test
    void constantAndAbsentConditionsIgnoreUnselectedUnsupportedSchemas() {
        Direct compiled = CompiledInstances.create(Direct.class);
        SchemaPlan trueIf = JsonSchema.fromJson(TRUE_IF).createPlan();
        SchemaPlan falseIf = JsonSchema.fromJson(FALSE_IF).createPlan();
        for (int n : new int[]{1,2,3,4}) {
            assertEquals(trueIf.isValid(n), compiled.trueIf(n), "true if: " + n);
            assertEquals(falseIf.isValid(n), compiled.falseIf(n), "false if: " + n);
        }
        SchemaPlan lone = JsonSchema.fromJson(IF_ONLY).createPlan();
        SchemaPlan absent = JsonSchema.fromJson(NO_IF).createPlan();
        for (String s : new String[]{"a", "blocked", "other"}) {
            assertEquals(lone.isValid(s), compiled.ifOnly(s));
            assertEquals(absent.isValid(s), compiled.noIf(s));
        }
    }

    @Test
    void strictFormatPropagatesToCondition() {
        Direct compiled = CompiledInstances.create(Direct.class);
        SchemaPlan reference = JsonSchema.fromJson(FORMAT).createPlan();
        for (String s : new String[]{"blocked","other","not-a-uuid",
                "123e4567-e89b-12d3-a456-426614174000"}) {
            for (boolean strict : new boolean[]{false,true}) {
                assertEquals(reference.isValid(s,strict), compiled.format(s,strict),
                        s + " strict=" + strict);
            }
        }
    }

    @Test
    void nullMemberAndStatefulFallbackRemainCorrect() {
        NullableMember compiled = CompiledInstances.create(NullableMember.class);
        Map<String,Object> map = new LinkedHashMap<>();
        map.put("country","US");
        SchemaPlan address = JsonSchema.fromJson(ADDRESS).createPlan();
        assertFalse(compiled.address(map)); // Missing state fails required.
        map.put("state",null);
        assertTrue(compiled.address(map)); // Present null satisfies required.
        assertEquals(address.isValid(map), compiled.address(map));
        map.put("state","CA");
        assertTrue(compiled.address(map));
        Runtime fallback = CompiledInstances.create(Runtime.class);
        SchemaPlan evaluated = JsonSchema.fromJson(UNEVALUATED).createPlan();
        for (Map<String,Object> item : Arrays.asList(
                Map.<String,Object>of("x",1), Map.<String,Object>of("y",1),
                Map.<String,Object>of("x",1,"other",2), Map.<String,Object>of())) {
            assertEquals(evaluated.isValid(item), fallback.unevaluated(item), item.toString());
        }
        SchemaPlan unsupported = JsonSchema.fromJson(UNSUPPORTED).createPlan();
        for (String s : new String[]{"abc","ab","z","no"}) {
            assertEquals(unsupported.isValid(s), fallback.unsupported(s), s);
        }
    }
}
