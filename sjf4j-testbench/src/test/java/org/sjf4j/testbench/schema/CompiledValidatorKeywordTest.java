package org.sjf4j.testbench.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.schema.ValidationException;
import org.sjf4j.schema.ValidationResult;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Positive and negative cases for each directly generated keyword family. */
class CompiledValidatorKeywordTest {
    private static final String STRINGS = """
            {"type":"object","properties":{"text":
                {"type":"string","minLength":2,"maxLength":4,"enum":["aa","bbb","cccc"]}}}
            """;
    private static final String NUMBERS = """
            {"type":"object","properties":{"decimal":
                {"type":"number","minimum":1,"maximum":5,"exclusiveMinimum":1,
                 "exclusiveMaximum":5}}}
            """;
    private static final String INTEGER = """
            {"type":"object","properties":{"integer":
                {"type":"integer","minimum":1,"maximum":3}}}
            """;
    private static final String ARRAYS = """
            {"type":"object","properties":{"matrix":{
                "type":"array","minItems":1,"maxItems":2,
                "items":{"type":"array","maxItems":2,
                    "items":{"type":"string","minLength":2}}}}}
            """;
    private static final String REFS = """
            {"type":"object","properties":{"text":{"$ref":"#/$defs/word"}},
             "$defs":{"word":{"type":"string","minLength":2,"maxLength":4}}}
            """;
    private static final String ROOT_REF = """
            {"$ref":"#/$defs/model","$defs":{"model":{"type":"object",
             "properties":{"text":{"type":"string","minLength":2}}}}}
            """;
    private static final String CLOSED = """
            {"type":"object","additionalProperties":false,
             "properties":{"text":true,"decimal":true,"integer":true,"matrix":true}}
            """;
    private static final String DYNAMIC = """
            {"type":"object","required":["text"],"additionalProperties":false,
             "properties":{"text":true}}
            """;
    private static final String MIN_LENGTH = """
            {"type":"object","properties":{"text":{"type":"string","minLength":2}}}
            """;
    private static final String MAX_LENGTH = """
            {"type":"object","properties":{"text":{"type":"string","maxLength":4}}}
            """;
    private static final String MINIMUM = """
            {"type":"object","properties":{"decimal":{"type":"number","minimum":1}}}
            """;
    private static final String MAXIMUM = """
            {"type":"object","properties":{"decimal":{"type":"number","maximum":5}}}
            """;
    private static final String EXCLUSIVE_MINIMUM = """
            {"type":"object","properties":{"decimal":{"type":"number","exclusiveMinimum":1}}}
            """;
    private static final String EXCLUSIVE_MAXIMUM = """
            {"type":"object","properties":{"decimal":{"type":"number","exclusiveMaximum":5}}}
            """;
    private static final String NULLABLE = """
            {"type":"object","required":["required"],
             "properties":{"required":{"type":"string","minLength":1},
                           "optional":{"type":"string","minLength":2}}}
            """;

    public static final class Values {
        private final String text;
        private final BigDecimal decimal;
        private final Integer integer;
        private final List<List<String>> matrix;

        public Values() { this("aa", BigDecimal.valueOf(2), 2, List.of(List.of("ab"))); }
        public Values(String text, BigDecimal decimal, Integer integer, List<List<String>> matrix) {
            this.text = text;
            this.decimal = decimal;
            this.integer = integer;
            this.matrix = matrix;
        }
        public String getText() { return text; }
        public BigDecimal getDecimal() { return decimal; }
        public Integer getInteger() { return integer; }
        public List<List<String>> getMatrix() { return matrix; }
    }

    @CompiledValidator(fallback = false)
    public interface Direct {
        @ValidJsonSchema(STRINGS) boolean strings(Values value);
        @ValidJsonSchema(MIN_LENGTH) boolean minLength(Values value);
        @ValidJsonSchema(MAX_LENGTH) boolean maxLength(Values value);
        @ValidJsonSchema(MINIMUM) boolean minimum(Values value);
        @ValidJsonSchema(MAXIMUM) boolean maximum(Values value);
        @ValidJsonSchema(EXCLUSIVE_MINIMUM) boolean exclusiveMinimum(Values value);
        @ValidJsonSchema(EXCLUSIVE_MAXIMUM) boolean exclusiveMaximum(Values value);
        @ValidJsonSchema(ref = "classpath:/schema/compiled-validator-ref.json#/$defs/model")
        boolean resourceFragment(Values value);
        @ValidJsonSchema(ref = "classpath:/schema/compiled-validator-wrapper.json")
        boolean externalRef(Values value);

        @ValidJsonSchema(NUMBERS) boolean numbers(Values value);
        @ValidJsonSchema(INTEGER) boolean integer(Values value);
        @ValidJsonSchema(ARRAYS) boolean arrays(Values value);
        @ValidJsonSchema(REFS) boolean ref(Values value);
        @ValidJsonSchema(ROOT_REF) boolean rootRef(Values value);
        @ValidJsonSchema(CLOSED) boolean closed(Values value);
        @ValidJsonSchema(DYNAMIC) boolean map(Map<String, Object> value);
        @ValidJsonSchema(DYNAMIC) boolean jsonObject(JsonObject value);
        @ValidJsonSchema(DYNAMIC) boolean jojo(Open value);
    }

    public static final class Open extends JsonObject {
        public Open() { super(); }
    }

    public static final class Bean {
        private final String required;
        private final String optional;
        public Bean() { this("ok", null); }
        public Bean(String required, String optional) {
            this.required = required;
            this.optional = optional;
        }
        public String getRequired() { return required; }
        public String getOptional() { return optional; }
    }

    @CompiledValidator(fallback = false, nullAsMissing = true)
    public interface Missing {
        @ValidJsonSchema(NULLABLE) boolean valid(Bean bean);
        @ValidJsonSchema(ARRAYS) boolean arrayValues(Values value);
    }

    @CompiledValidator(fallback = true, nullAsMissing = true)
    public interface MissingDiagnostics {
        @ValidJsonSchema(NULLABLE) void require(Bean bean);
        @ValidJsonSchema(NULLABLE) ValidationResult validate(Bean bean);
        @ValidJsonSchema(DYNAMIC) boolean map(Map<String, Object> map);
    }

    private static Values value(String text, BigDecimal decimal, Integer integer,
                                List<List<String>> matrix) {
        return new Values(text, decimal, integer, matrix);
    }

    @Test
    void stringsMinMaxAndEnum() {
        Direct v = CompiledInstances.create(Direct.class);
        assertTrue(v.strings(new Values()));
        assertFalse(v.strings(value("a", BigDecimal.ONE, 1, List.of())));
        assertFalse(v.strings(value("ccccc", BigDecimal.ONE, 1, List.of())));
        assertFalse(v.strings(value("zz", BigDecimal.ONE, 1, List.of())));
        assertTrue(v.strings(value("bbb", BigDecimal.ONE, 1, List.of())));
    }

    @Test
    void eachLengthKeywordHasIsolatedPassingAndFailingCases() {
        Direct v = CompiledInstances.create(Direct.class);
        assertFalse(v.minLength(value("a", BigDecimal.ONE, 1, List.of())));
        assertTrue(v.minLength(value("ab", BigDecimal.ONE, 1, List.of())));
        assertTrue(v.maxLength(value("a", BigDecimal.ONE, 1, List.of())));
        assertFalse(v.maxLength(value("abcde", BigDecimal.ONE, 1, List.of())));
    }

    @Test
    void eachNumericBoundHasIsolatedBoundaryCases() {
        Direct v = CompiledInstances.create(Direct.class);
        assertTrue(v.minimum(value("a", BigDecimal.ONE, 1, List.of())));
        assertFalse(v.minimum(value("a", new BigDecimal("0.99"), 1, List.of())));
        assertTrue(v.maximum(value("a", BigDecimal.valueOf(5), 1, List.of())));
        assertFalse(v.maximum(value("a", new BigDecimal("5.01"), 1, List.of())));
        assertFalse(v.exclusiveMinimum(value("a", BigDecimal.ONE, 1, List.of())));
        assertTrue(v.exclusiveMinimum(value("a", new BigDecimal("1.01"), 1, List.of())));
        assertFalse(v.exclusiveMaximum(value("a", BigDecimal.valueOf(5), 1, List.of())));
        assertTrue(v.exclusiveMaximum(value("a", new BigDecimal("4.99"), 1, List.of())));
    }

    @Test
    void numberIntegerAndExclusiveBounds() {
        Direct v = CompiledInstances.create(Direct.class);
        assertTrue(v.numbers(new Values()));
        for (String n : new String[]{"0", "1", "5", "6"}) {
            assertFalse(v.numbers(value("aa", new BigDecimal(n), 2, List.of())));
        }
        assertTrue(v.numbers(value("aa", new BigDecimal("1.01"), 2, List.of())));
        assertTrue(v.integer(new Values()));
        assertTrue(v.integer(value("aa", BigDecimal.ONE, 3, List.of())));
        assertFalse(v.integer(value("aa", BigDecimal.ONE, 0, List.of())));
        assertFalse(v.integer(value("aa", BigDecimal.ONE, 4, List.of())));
    }

    @Test
    void recursiveItemsAndCollectionBounds() {
        Direct v = CompiledInstances.create(Direct.class);
        assertTrue(v.arrays(new Values()));
        assertFalse(v.arrays(value("aa", BigDecimal.ONE, 2, List.of())));
        assertFalse(v.arrays(value("aa", BigDecimal.ONE, 2, List.of(List.of("aa", "bb", "cc")))));
        assertFalse(v.arrays(value("aa", BigDecimal.ONE, 2, List.of(List.of("x")))));
        assertFalse(v.arrays(value("aa", BigDecimal.ONE, 2,
                List.of(List.of("aa"), List.of("bb"), List.of("cc")))));
        assertTrue(v.arrays(value("aa", BigDecimal.ONE, 2, List.of(List.of("aa", "bb")))));
    }

    @Test
    void localRefsCompileWithoutRootPlan() {
        Direct v = CompiledInstances.create(Direct.class);
        assertTrue(v.ref(new Values()));
        assertFalse(v.ref(value("x", BigDecimal.ONE, 1, List.of())));
        assertFalse(v.ref(value("xxxxx", BigDecimal.ONE, 1, List.of())));
        assertTrue(v.rootRef(new Values()));
        assertFalse(v.rootRef(value("x", BigDecimal.ONE, 1, List.of())));
        assertTrue(v.resourceFragment(new Values()));
        assertFalse(v.resourceFragment(value("x", BigDecimal.ONE, 1, List.of())));
        assertTrue(v.externalRef(new Values()));
        assertFalse(v.externalRef(value("x", BigDecimal.ONE, 1, List.of())));
    }

    @Test
    void additionalPropertiesAndRequiredOnPojoMapJsonObjectAndJojo() {
        Direct v = CompiledInstances.create(Direct.class);
        assertTrue(v.closed(new Values()));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("text", null);
        assertTrue(v.map(data)); // Required tests presence, not non-null.
        data.put("extra", 1);
        assertFalse(v.map(data));
        data.remove("extra");
        assertTrue(v.jsonObject(new JsonObject(data)));
        JsonObject unknown = new JsonObject(data);
        unknown.put("extra", 1);
        assertFalse(v.jsonObject(unknown));
        Open jojo = new Open();
        jojo.put("text", null);
        assertTrue(v.jojo(jojo));
        jojo.put("extra", 1);
        assertFalse(v.jojo(jojo));
        data.clear();
        assertFalse(v.map(data));
    }

    @Test
    void optionalNullAsMissingForTypedAndDynamicValues() {
        Missing direct = CompiledInstances.create(Missing.class);
        assertTrue(direct.valid(new Bean("ok", null)));
        assertFalse(direct.valid(new Bean(null, "aa")));
        assertFalse(direct.valid(new Bean("ok", "x")));
        assertTrue(direct.valid(new Bean("ok", "abc")));
        // Null elements belong to arrays and must not be treated as missing.
        assertFalse(direct.arrayValues(value("aa", BigDecimal.ONE, 1,
                List.of(Arrays.asList("aa", null)))));
        assertTrue(direct.arrayValues(value("aa", BigDecimal.ONE, 1,
                List.of(List.of("aa", "bb")))));

        MissingDiagnostics fallback = CompiledInstances.create(MissingDiagnostics.class);
        assertDoesNotThrow(() -> fallback.require(new Bean("ok", null)));
        assertThrows(ValidationException.class,
                () -> fallback.require(new Bean(null, null)));
        assertTrue(fallback.validate(new Bean("ok", null)).isValid());
        assertFalse(fallback.validate(new Bean(null, "aa")).isValid());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("text", null);
        assertFalse(fallback.map(data)); // Required null is treated as absent.
        data.put("text", "ok");
        data.put("extra", null);
        assertTrue(fallback.map(data)); // Null extra member is absent.
        data.put("extra", 3);
        assertFalse(fallback.map(data));
    }
}
