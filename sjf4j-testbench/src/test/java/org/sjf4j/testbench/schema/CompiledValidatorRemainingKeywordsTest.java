package org.sjf4j.testbench.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaPlan;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Compare the last lightweight compiled keyword families to runtime SchemaPlan. */
class CompiledValidatorRemainingKeywordsTest {
    private static final String PATTERN = """
            {"type":"string","pattern":"^ab[0-9]+$","minLength":3}
            """;
    private static final String MULTIPLE = """
            {"type":"number","multipleOf":0.1,"minimum":0}
            """;
    private static final String INTEGER_MULTIPLE = """
            {"type":"integer","multipleOf":3}
            """;
    private static final String OBJECT_COUNT = """
            {"type":"object","minProperties":1,"maxProperties":2}
            """;
    private static final String TYPELESS_COUNT = """
            {"minProperties":2,"maxProperties":3}
            """;
    private static final String TYPES = """
            {"type":"object","properties":{
             "flag":{"type":"boolean"},
             "missing":{"type":"null"},
             "value":{"type":["number","string","null"]}}}
            """;
    private static final String UNION = """
            {"type":["integer","string"]}
            """;
    private static final String UNION_LITERAL = """
            {"type":["string","number"],"enum":["yes",3]}
            """;
    private static final String BOOLEAN = """
            {"type":"boolean"}
            """;
    private static final String COMBINED = """
            {"type":"object","properties":{
              "text":{"type":"string","pattern":"^[a-z]+$"},
              "quantity":{"type":"number","multipleOf":0.25}},
             "minProperties":1,"maxProperties":2}
            """;

    public static final class CountPojo {
        private final int a = 1;
        private final String b = "b";
        public int getA() { return a; }
        public String getB() { return b; }
    }

    @CompiledValidator(fallback = false)
    public interface Direct {
        @ValidJsonSchema(PATTERN) boolean pattern(String value);
        @ValidJsonSchema(MULTIPLE) boolean multiple(BigDecimal value);
        @ValidJsonSchema(INTEGER_MULTIPLE) boolean integerMultiple(Integer value);
        @ValidJsonSchema(OBJECT_COUNT) boolean size(Map<String,Object> value);
        @ValidJsonSchema(OBJECT_COUNT) boolean pojo(CountPojo value);
        @ValidJsonSchema(TYPELESS_COUNT) boolean typeLess(Object value);
        @ValidJsonSchema(TYPES) boolean typedMembers(Map<String,Object> value);
        @ValidJsonSchema(UNION) boolean union(Object value);
        @ValidJsonSchema(UNION_LITERAL) boolean unionLiteral(Object value);
        @ValidJsonSchema(BOOLEAN) boolean bool(Boolean value);
        @ValidJsonSchema(COMBINED) boolean combined(Map<String,Object> value);
    }

    private static void agrees(SchemaPlan plan, boolean actual, Object value) {
        assertEquals(plan.isValid(value), actual, String.valueOf(value));
    }

    @Test
    void regexUsesSearchSemanticsAndReusesCompiledPatterns() {
        Direct compiled = CompiledInstances.create(Direct.class);
        SchemaPlan reference = JsonSchema.fromJson(PATTERN).createPlan();
        for (String s : new String[]{"ab1","ab123","xxab1","a","AB2","abcd","ab0\n"}) {
            agrees(reference, compiled.pattern(s), s);
        }
    }

    @Test
    void fractionalAndIntegralMultiplesAgreeWithRuntime() {
        Direct compiled = CompiledInstances.create(Direct.class);
        SchemaPlan fractional = JsonSchema.fromJson(MULTIPLE).createPlan();
        for (String s : new String[]{"0","0.1","0.2","0.3","1.0","0.31","-0.1","10.01"}) {
            BigDecimal v = new BigDecimal(s);
            agrees(fractional, compiled.multiple(v), v);
        }
        SchemaPlan integer = JsonSchema.fromJson(INTEGER_MULTIPLE).createPlan();
        for (int n : new int[]{-6,-3,-2,0,1,2,3,6,7,100,101}) {
            agrees(integer, compiled.integerMultiple(n), n);
        }
    }

    @Test
    void objectSizeWorksForMapsPojoAndTypeLessSchema() {
        Direct compiled = CompiledInstances.create(Direct.class);
        SchemaPlan sized = JsonSchema.fromJson(OBJECT_COUNT).createPlan();
        for (Map<String,Object> map : Arrays.asList(
                Map.<String,Object>of(), Map.<String,Object>of("a",1),
                Map.<String,Object>of("a",1,"b",2),
                Map.<String,Object>of("a",1,"b",2,"c",3))) {
            agrees(sized, compiled.size(map), map);
        }
        CountPojo pojo = new CountPojo();
        agrees(sized, compiled.pojo(pojo), pojo);
        SchemaPlan typeLess = JsonSchema.fromJson(TYPELESS_COUNT).createPlan();
        for (Object value : Arrays.asList(
                Map.<String,Object>of(), Map.<String,Object>of("a",1),
                Map.<String,Object>of("a",1,"b",2),
                Map.<String,Object>of("a",1,"b",2,"c",3),
                Map.<String,Object>of("a",1,"b",2,"c",3,"d",4),
                "plain", 17, List.of("abc"))) {
            agrees(typeLess, compiled.typeLess(value), value);
        }
    }

    @Test
    void booleanNullAndUnionTypesAcceptOnlyTheirJsonShapes() {
        Direct compiled = CompiledInstances.create(Direct.class);
        SchemaPlan members = JsonSchema.fromJson(TYPES).createPlan();
        for (Object value : Arrays.asList(true, false, "yes", 1, 2.0, List.of(1))) {
            Map<String,Object> map = new LinkedHashMap<>();
            map.put("flag", value);
            map.put("missing", null);
            map.put("value", value);
            agrees(members, compiled.typedMembers(map), map);
        }
        Map<String,Object> valid = new LinkedHashMap<>();
        valid.put("flag", true);
        valid.put("missing", null);
        valid.put("value", "yes");
        agrees(members, compiled.typedMembers(valid), valid);
        assertTrue(compiled.typedMembers(valid));
        SchemaPlan union = JsonSchema.fromJson(UNION).createPlan();
        for (Object value : Arrays.asList("x", 1, 1.0, 2.2, true, Map.of("a", 1))) {
            agrees(union, compiled.union(value), value);
        }
        SchemaPlan unionLiteral = JsonSchema.fromJson(UNION_LITERAL).createPlan();
        for (Object value : Arrays.asList("yes","no",3,3.0,4,false)) {
            agrees(unionLiteral, compiled.unionLiteral(value), value);
        }
        SchemaPlan bool = JsonSchema.fromJson(BOOLEAN).createPlan();
        for (boolean b : new boolean[]{true,false}) agrees(bool, compiled.bool(b), b);
    }

    @Test
    void allNewKeywordsWorkInsideExistingObjectCompiler() {
        Direct compiled = CompiledInstances.create(Direct.class);
        SchemaPlan reference = JsonSchema.fromJson(COMBINED).createPlan();
        for (Map<String,Object> map : Arrays.asList(
                Map.<String,Object>of(), Map.<String,Object>of("text","abc"),
                Map.<String,Object>of("text","abc","quantity",1.25),
                Map.<String,Object>of("text","123","quantity",1.25),
                Map.<String,Object>of("quantity",1.35),
                Map.<String,Object>of("text","abc","quantity",1.25,"extra",1))) {
            agrees(reference, compiled.combined(map), map);
        }
    }
}
