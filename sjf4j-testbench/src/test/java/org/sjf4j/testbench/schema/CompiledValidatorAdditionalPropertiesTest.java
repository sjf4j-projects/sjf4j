package org.sjf4j.testbench.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** JSON Schema keyword semantics are independent of type and POJO/OBNT access. */
public class CompiledValidatorAdditionalPropertiesTest {
    @CompiledValidator(fallback = false)
    public interface Checks {
        @ValidJsonSchema("{\"properties\":{\"foo\":{}},\"additionalProperties\":{\"type\":\"boolean\"}}")
        boolean schemaValues(Object value);

        @ValidJsonSchema("{\"properties\":{\"foo\":{}},"
                + "\"patternProperties\":{\"^v\":{}},\"additionalProperties\":false}")
        boolean patternsAndFalse(Object value);

        @ValidJsonSchema("{\"properties\":{\"foo\":{\"minLength\":1}},"
                + "\"patternProperties\":{\"o\":{\"type\":\"string\"},\"^fo\":{\"minLength\":3}},"
                + "\"additionalProperties\":{\"type\":\"boolean\"}}")
        boolean intersectingKeywords(Object value);

        @ValidJsonSchema("{\"allOf\":[{\"properties\":{\"foo\":{}}}],"
                + "\"additionalProperties\":{\"type\":\"boolean\"}}")
        boolean noCoverageFromAllOf(Object value);

        @ValidJsonSchema("{\"additionalProperties\":{\"type\":\"null\"}}")
        boolean nullProperties(Object value);

        @ValidJsonSchema("{\"properties\":{\"label\":{}},"
                + "\"additionalProperties\":{\"type\":\"boolean\"}}")
        boolean pojo(Bean value);

        @ValidJsonSchema("{\"additionalProperties\":{\"type\":\"number\",\"minimum\":1000}}")
        boolean codecAdditional(CodecBean value);

        @ValidJsonSchema("{\"patternProperties\":{\"^mil\":{\"type\":\"number\",\"minimum\":1000}}}")
        boolean codecPattern(CodecBean value);
    }

    public static final class CodecBean {
        @NodeProperty(valueFormat = "epochMillis")
        public Instant millis = Instant.ofEpochMilli(2_000);
    }

    public static final class Bean {
        public String label;
        public Object flag;
        public Bean() {}
        public Bean(String label, Object flag) {
            this.label = label;
            this.flag = flag;
        }
    }

    @Test
    void schemaValuedAdditionalPropertiesIsTypeConditional() {
        Checks check = CompiledInstances.create(Checks.class);
        assertTrue(check.schemaValues(Map.of("foo", 1, "extra", true)));
        assertFalse(check.schemaValues(Map.of("foo", 1, "extra", 2)));
        assertTrue(check.schemaValues(new JsonObject(Map.of("foo", "anything", "extra", false))));
        assertTrue(check.schemaValues("not an object"));
        assertTrue(check.schemaValues(null));
    }

    @Test
    void siblingPatternsCoverNamesButApplicatorsDoNot() {
        Checks check = CompiledInstances.create(Checks.class);
        assertTrue(check.patternsAndFalse(Map.of("foo", 1, "vroom", 2)));
        assertFalse(check.patternsAndFalse(Map.of("foo", 1, "other", 2)));
        assertTrue(check.patternsAndFalse(new JsonObject(Map.of("vroom", 2))));
        assertTrue(check.patternsAndFalse(7));

        assertTrue(check.intersectingKeywords(Map.of("foo", "good", "extra", true)));
        assertFalse(check.intersectingKeywords(Map.of("foo", "hi")));
        assertFalse(check.intersectingKeywords(Map.of("foobar", 2)));
        assertFalse(check.intersectingKeywords(Map.of("other", true))); // "o" matches: pattern value must be a string.
        assertTrue(check.intersectingKeywords(Map.of("bar", true)));

        assertFalse(check.noCoverageFromAllOf(Map.of("foo", 1, "bar", true)));
        assertTrue(check.noCoverageFromAllOf(Map.of("foo", true, "bar", true)));
    }

    @Test
    void dynamicPropertyChecksRespectPojoValueFormats() {
        Checks check = CompiledInstances.create(Checks.class);
        CodecBean value = new CodecBean();
        assertTrue(check.codecAdditional(value));
        assertTrue(check.codecPattern(value));
        value.millis = Instant.ofEpochMilli(100);
        assertFalse(check.codecAdditional(value));
        assertFalse(check.codecPattern(value));
        value.millis = null;
        assertTrue(check.codecAdditional(value)); // null does not match number.
        assertTrue(check.codecPattern(value));
    }

    @Test
    void nullValuesAndPojoGettersUseTheSameSchemaSemantics() {
        Checks check = CompiledInstances.create(Checks.class);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("foo", null);
        assertTrue(check.nullProperties(values));
        values.put("bar", 1);
        assertFalse(check.nullProperties(values));

        assertTrue(check.pojo(new Bean("hello", true)));
        assertFalse(check.pojo(new Bean("hello", "not a boolean")));
        assertTrue(check.pojo(null)); // Object-only keywords skip null.
    }
}
