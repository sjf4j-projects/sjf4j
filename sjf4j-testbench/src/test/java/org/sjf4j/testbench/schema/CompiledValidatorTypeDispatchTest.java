package org.sjf4j.testbench.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Typed getter paths use Java values directly. Erased Object and dynamic Map
 * values use strict Nodes conversions after checking their JSON shape.
 */
class CompiledValidatorTypeDispatchTest {
    public enum Kind { FIRST, SECOND }

    public static final class Typed {
        private final String text;
        private final Kind kind;
        private final BigDecimal number;
        private final List<String> words;

        public Typed() { this("good", Kind.FIRST, BigDecimal.valueOf(2), List.of("aa")); }

        public Typed(String text, Kind kind, BigDecimal number, List<String> words) {
            this.text = text;
            this.kind = kind;
            this.number = number;
            this.words = words;
        }

        public String getText() { return text; }
        public Kind getKind() { return kind; }
        public BigDecimal getNumber() { return number; }
        public List<String> getWords() { return words; }
    }

    public static final class Erased {
        private final Object value;
        public Erased() { this("good"); }
        public Erased(Object value) { this.value = value; }
        public Object getValue() { return value; }
    }

    @CompiledValidator(fallback = false)
    public interface Validator {
        @ValidJsonSchema("""
                {"type":"object","properties":{"text":
                    {"type":"string","minLength":2,"maxLength":5}}}
                """)
        boolean text(Typed v);

        @ValidJsonSchema("""
                {"type":"object","properties":{"kind":
                    {"type":"string","enum":["FIRST","SECOND"]}}}
                """)
        boolean enumeration(Typed v);

        @ValidJsonSchema("""
                {"type":"object","properties":{"number":
                    {"type":"number","exclusiveMinimum":1}}}
                """)
        boolean number(Typed v);

        @ValidJsonSchema("""
                {"type":"object","properties":{"words":
                    {"type":"array","minItems":1,
                     "items":{"type":"string","minLength":2}}}}
                """)
        boolean list(Typed v);

        @ValidJsonSchema("""
                {"type":"object","properties":{"value":
                    {"type":"string","minLength":2}}}
                """)
        boolean objectString(Erased v);

        @ValidJsonSchema("""
                {"type":"object","required":["text","count","words"],
                 "properties":{
                     "text":{"type":"string","minLength":2},
                     "count":{"type":"integer","minimum":1},
                     "words":{"type":"array","minItems":1,
                              "items":{"type":"string","minLength":2}}}}
                """)
        boolean map(Map<String, Object> v);
    }

    @Test
    void staticallyTypedPropertiesUseDirectTypeSemantics() {
        Validator v = CompiledInstances.create(Validator.class);
        assertTrue(v.text(new Typed()));
        assertFalse(v.text(new Typed(null, Kind.FIRST, BigDecimal.ONE, List.of("aa"))));
        assertFalse(v.text(new Typed("abcdef", Kind.FIRST, BigDecimal.ONE, List.of("aa"))));
        assertTrue(v.enumeration(new Typed()));
        assertTrue(v.enumeration(new Typed("ok", Kind.SECOND, BigDecimal.ONE, List.of("aa"))));
        assertFalse(v.enumeration(new Typed("ok", null, BigDecimal.ONE, List.of("aa"))));
        assertTrue(v.number(new Typed()));
        assertFalse(v.number(new Typed("ok", Kind.FIRST, BigDecimal.ONE, List.of("aa"))));
        assertTrue(v.list(new Typed()));
        assertFalse(v.list(new Typed("ok", Kind.FIRST, BigDecimal.ONE, List.of())));
        assertFalse(v.list(new Typed("ok", Kind.FIRST, BigDecimal.ONE, Arrays.asList("aa", null))));
    }

    @Test
    void dynamicObjectValuesUseStrictNodesConversions() {
        Validator v = CompiledInstances.create(Validator.class);
        assertTrue(v.objectString(new Erased("hello")));
        assertFalse(v.objectString(new Erased(123)));
        assertFalse(v.objectString(new Erased(null)));

        // UUID is represented as a JSON string by SJF4J's built-in value codec.
        assertTrue(v.objectString(new Erased(UUID.randomUUID())));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("text", "hello");
        data.put("count", new BigDecimal("2.0"));
        data.put("words", List.of("aa", "bbb"));
        assertTrue(v.map(data));

        data.put("text", 123);
        assertFalse(v.map(data));
        data.put("text", "hello");

        data.put("count", "2");
        assertFalse(v.map(data));
        data.put("count", BigDecimal.ONE);

        data.put("words", "not an array");
        assertFalse(v.map(data));
        data.put("words", Arrays.asList("aa", null));
        assertFalse(v.map(data));
        data.put("words", List.of("a"));
        assertFalse(v.map(data));
    }
}
