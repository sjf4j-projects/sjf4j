package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.node.NodeProperty;
import org.sjf4j.annotation.node.NodeValue;
import org.sjf4j.annotation.node.RawToValue;
import org.sjf4j.annotation.node.ValueToRaw;

import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Runtime schema object traversal with explicit property codecs and evaluated-property frames. */
class SchemaObjectTraversalTest {
    static class Bean {
        public String name = "Ada";
        public int age = 35;
    }

    static class Jojo extends JsonObject {
        public String name = "Ada";
        public int age = 35;
    }

    static class Times {
        @NodeProperty(valueFormat = "epochMillis")
        public Instant epoch = Instant.ofEpochMilli(0);

        public Instant iso = Instant.EPOCH;
    }

    @NodeValue
    static class EncodedObject {
        final String name;
        EncodedObject(String name) { this.name = name; }
        @ValueToRaw public Map<String, Object> toRaw() { return Map.of("name", name); }
        @RawToValue public static EncodedObject fromRaw(Map<String, Object> raw) {
            return new EncodedObject((String) raw.get("name"));
        }
    }

    @NodeValue
    static class EncodedArray {
        final List<Integer> contents;
        EncodedArray(List<Integer> contents) { this.contents = contents; }
        @ValueToRaw public List<Integer> toRaw() { return contents; }
        @RawToValue public static EncodedArray fromRaw(List<Integer> raw) {
            return new EncodedArray(raw);
        }
    }

    static class ValueHolder {
        public EncodedObject wrapped = new EncodedObject("Ada");
        public EncodedArray array = new EncodedArray(List.of(1, 2));
    }

    static class Formatted {
        @NodeProperty(valuePattern = "yyyy/MM/dd")
        public LocalDate day = LocalDate.of(2026, 11, 2);
    }

    static SchemaPlan schema(String json) {
        return JsonSchema.fromJson(json).createPlan();
    }

    @Test
    void objectRepresentationsSharePropertyMatchingAndUnevaluatedSemantics() {
        SchemaPlan all = schema("{\"properties\":{\"name\":{\"type\":\"string\"}},"
                + "\"patternProperties\":{\"^a\":{\"type\":\"integer\"}},"
                + "\"additionalProperties\":false}");
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", "Ada");
        map.put("age", 35);
        assertTrue(all.isValid(map));
        assertTrue(all.isValid(new Bean()));

        Jojo jojo = new Jojo();
        assertTrue(all.isValid(jojo));
        jojo.put("dynamic", 1);
        assertFalse(all.isValid(jojo));
        assertFalse(all.validate(jojo, false, false).isValid());

        SchemaPlan unevaluated = schema("{\"properties\":{\"name\":true},"
                + "\"patternProperties\":{\"^a\":true},"
                + "\"unevaluatedProperties\":false}");
        assertTrue(unevaluated.isValid(map));
        assertTrue(unevaluated.isValid(new Bean()));
        assertFalse(unevaluated.isValid(jojo));
        assertFalse(schema("{\"unevaluatedProperties\":false}").isValid(new Bean()));
    }

    @Test
    void explicitPropertyCodecOverridesDefaultAndWorksThroughUnevaluatedProperties() {
        Times times = new Times();
        SchemaPlan plan = schema("{\"properties\":{"
                + "\"epoch\":{\"type\":\"number\"},\"iso\":{\"type\":\"string\"}},"
                + "\"unevaluatedProperties\":false}");
        assertTrue(plan.isValid(times));
        assertTrue(plan.validate(times).isValid());
        assertFalse(schema("{\"properties\":{\"epoch\":{\"type\":\"string\"}}}").isValid(times));
        assertTrue(schema("{\"unevaluatedProperties\":{\"type\":\"number\"}}")
                .isValid(new EpochOnly()));

        SchemaPlan twoPlans = schema("{\"properties\":{\"epoch\":{\"type\":\"number\"}},"
                + "\"patternProperties\":{\"^epoch$\":{\"minimum\":0}}}");
        assertTrue(twoPlans.isValid(times));
    }

    static class EpochOnly {
        @NodeProperty(valueFormat = "epochMillis")
        public Instant epoch = Instant.EPOCH;
    }

    @Test
    void rawMapBackedValueRemainsAtomic() {
        ValueHolder holder = new ValueHolder();
        SchemaPlan mapShape = schema("{\"properties\":{\"wrapped\":{\"type\":\"object\"}}}");
        assertFalse(mapShape.isValid(holder));
        assertFalse(schema("{\"properties\":{\"wrapped\":{\"type\":\"string\"}}}")
                .validate(holder).isValid());
        assertTrue(schema("{\"properties\":{\"wrapped\":{\"properties\":{\"name\":false}}}}")
                .isValid(holder));
        // Array-shaped raw encodings are also non-traversable value nodes.
        assertFalse(schema("{\"properties\":{\"array\":{\"type\":\"array\"}}}").isValid(holder));
        assertTrue(schema("{\"properties\":{\"array\":{\"items\":false}}}").isValid(holder));
    }

    @Test
    void propertyValuePatternIsAppliedBeforeStringAssertions() {
        SchemaPlan formatted = schema("{\"properties\":{\"day\":"
                + "{\"type\":\"string\",\"pattern\":\"^2026/11/02$\"}}}");
        assertTrue(formatted.isValid(new Formatted()));
        assertFalse(schema("{\"properties\":{\"day\":{\"const\":\"2026-11-02\"}}}")
                .isValid(new Formatted()));
    }

    @Test
    void evaluatedBitsCoversZeroSixtyFourAndOverflow() {
        for (int size : new int[]{0, 1, 63, 64, 65, 128}) {
            EvaluationBits bits = new EvaluationBits();
            assertEquals(size == 0, bits.covers(size), "empty " + size);
            bits.set(0, size);
            assertTrue(bits.covers(size), "complete " + size);
            assertFalse(bits.covers(size + 1), "extra " + size);
        }
    }

    @Test
    void unevaluatedPropertiesAboveSixtyFour() {
        StringBuilder json = new StringBuilder("{\"properties\":{");
        Map<String, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < 70; i++) {
            if (i > 0) json.append(',');
            json.append("\"f").append(i).append("\":true");
            values.put("f" + i, i);
        }
        json.append("},\"unevaluatedProperties\":false}");
        SchemaPlan plan = schema(json.toString());
        assertTrue(plan.isValid(values));
        values.put("extra", 1);
        assertFalse(plan.isValid(values));
    }
}
