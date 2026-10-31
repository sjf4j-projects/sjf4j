package org.sjf4j.testbench.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaPlan;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for child-schema URIs, local fallbacks,
 * and integer values beyond the range of a Java long.
 */
class CompiledValidatorFallbackBoundariesTest {
    private static final String ROOT_ARRAY = """
            {"type":"array","items":{
              "type":"object",
              "patternProperties":{"^x":{"type":"string","minLength":2}}
            }}
            """;

    private static final String NESTED_ARRAY = """
            {"type":"array","items":{"type":"array","items":{
              "type":"object",
              "patternProperties":{"^x":{"type":"string","minLength":2}}
            }}}
            """;

    private static final String LOCAL_FALLBACK = """
            {"type":"object","required":["nested"],"properties":{"nested":{
              "type":"object","required":["x"],
              "patternProperties":{"^x":true}
            }}}
            """;

    private static final String ARRAY_LOCAL_FALLBACK = """
            {"type":"array","items":{"type":"object","required":["x"],
              "patternProperties":{"^x":true}}}
            """;

    private static final String SMALL_DIVISOR = """
            {"type":"integer","multipleOf":3}
            """;
    private static final String HUGE_DIVISOR = """
            {"type":"integer","multipleOf":18446744073709551616}
            """;

    @CompiledValidator
    public interface Arrays {
        @ValidJsonSchema(ROOT_ARRAY) boolean root(List<Map<String, Object>> values);
        @ValidJsonSchema(NESTED_ARRAY) boolean nested(List<List<Map<String, Object>>> values);
    }

    @CompiledValidator
    public interface LocalFallback {
        @ValidJsonSchema(LOCAL_FALLBACK) boolean object(Map<String, Object> value);
        @ValidJsonSchema(ARRAY_LOCAL_FALLBACK) boolean array(List<Map<String, Object>> value);
    }

    @CompiledValidator(fallback = false)
    public interface PreciseInteger {
        @ValidJsonSchema(SMALL_DIVISOR) boolean small(BigInteger value);
        @ValidJsonSchema(HUGE_DIVISOR) boolean huge(BigInteger value);
    }

    private static void assertParity(SchemaPlan reference, boolean actual, Object input) {
        assertEquals(reference.isValid(input), actual, String.valueOf(input));
    }

    @Test
    void rootAndNestedArrayItemsResolveFallbackPointers() {
        Arrays validator = CompiledInstances.create(Arrays.class);
        SchemaPlan root = JsonSchema.fromJson(ROOT_ARRAY).createPlan();
        SchemaPlan nested = JsonSchema.fromJson(NESTED_ARRAY).createPlan();

        for (Map<String, Object> element : List.of(
                Map.<String, Object>of(),
                Map.<String, Object>of("xName", "ok"),
                Map.<String, Object>of("xName", "x"),
                Map.<String, Object>of("other", 1))) {
            List<Map<String, Object>> single = List.of(element);
            assertParity(root, validator.root(single), single);
            List<List<Map<String, Object>>> pair = List.of(single);
            assertParity(nested, validator.nested(pair), pair);
        }
        assertFalse(validator.root(List.of(Map.of("xName", "x"))));
        assertTrue(validator.root(List.of(Map.of("xName", "ab"))));
        assertFalse(validator.nested(List.of(List.of(Map.of("xName", "x")))));
    }

    @Test
    void localFallbackPreservesNullMemberPresence() {
        LocalFallback compiled = CompiledInstances.create(LocalFallback.class);
        SchemaPlan objectPlan = JsonSchema.fromJson(LOCAL_FALLBACK).createPlan();
        SchemaPlan arrayPlan = JsonSchema.fromJson(ARRAY_LOCAL_FALLBACK).createPlan();
        for (Object x : java.util.Arrays.asList(null, "present")) {
            Map<String, Object> nested = new LinkedHashMap<>();
            nested.put("x", x);
            Map<String, Object> outer = Map.of("nested", nested);
            assertParity(objectPlan, compiled.object(outer), outer);
            List<Map<String, Object>> values = List.of(nested);
            assertParity(arrayPlan, compiled.array(values), values);
        }
        Map<String, Object> nullMember = new LinkedHashMap<>();
        nullMember.put("x", null);
        assertTrue(compiled.object(Map.of("nested", nullMember)));
        assertTrue(compiled.array(List.of(nullMember)));
        assertFalse(compiled.object(Map.of("nested", Map.of())));
        assertFalse(compiled.array(List.of(Map.of())));
    }

    @Test
    void hugeIntegerValuesAndDivisorsKeepExactRuntimeParity() {
        PreciseInteger compiled = CompiledInstances.create(PreciseInteger.class);
        SchemaPlan small = JsonSchema.fromJson(SMALL_DIVISOR).createPlan();
        SchemaPlan huge = JsonSchema.fromJson(HUGE_DIVISOR).createPlan();

        BigInteger twoTo64 = BigInteger.ONE.shiftLeft(64);
        for (BigInteger input : List.of(
                BigInteger.ZERO, BigInteger.ONE,
                BigInteger.valueOf(-3), BigInteger.valueOf(3),
                BigInteger.valueOf(Long.MAX_VALUE),
                twoTo64, twoTo64.add(BigInteger.ONE), twoTo64.add(BigInteger.TWO),
                twoTo64.negate(), twoTo64.multiply(BigInteger.valueOf(3)),
                twoTo64.multiply(BigInteger.valueOf(3)).add(BigInteger.ONE))) {
            assertParity(small, compiled.small(input), input);
            assertParity(huge, compiled.huge(input), input);
        }
        assertFalse(small.isValid(twoTo64));
        assertTrue(small.isValid(twoTo64.add(BigInteger.TWO)));
        assertTrue(huge.isValid(twoTo64));
        assertFalse(huge.isValid(twoTo64.add(BigInteger.ONE)));
    }
}
