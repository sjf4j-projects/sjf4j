package org.sjf4j.testbench.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deep JSON-semantic literals must compile with fallback=false. */
public class CompiledValidatorStructuralLiteralTest {
    @CompiledValidator(fallback = false)
    public interface Checks {
        @ValidJsonSchema("{\"const\":{\"foo\":\"bar\",\"baz\":[1,true,null]}}")
        boolean objectConstant(Object node);

        @ValidJsonSchema("{\"const\":[{\"foo\":\"bar\"}]}")
        boolean arrayConstant(Object node);

        @ValidJsonSchema("{\"enum\":[6,\"foo\",[],true,{\"foo\":12}]}")
        boolean mixedEnum(Object node);

        @ValidJsonSchema("{\"const\":[false]}")
        boolean falseArray(Object node);

        @ValidJsonSchema("{\"enum\":[[1],{\"a\":false}]}")
        boolean structuralEnum(Object node);

        @ValidJsonSchema("{\"const\":{\"active\":true,\"n\":1}}")
        boolean pojoValue(Bean value);
    }

    public static final class Bean {
        public boolean active;
        public int n;
        public Bean() {}
        public Bean(boolean active, int n) { this.active = active; this.n = n; }
    }

    @Test
    void objectAndArrayConstUseDeepJsonEquality() {
        Checks v = CompiledInstances.create(Checks.class);
        Map<String, Object> left = new LinkedHashMap<>();
        left.put("baz", Arrays.asList(1.0, true, null));
        left.put("foo", "bar");
        assertTrue(v.objectConstant(left));
        assertTrue(v.objectConstant(new JsonObject(left)));
        assertFalse(v.objectConstant(Map.of("foo", "bar", "baz", List.of(1, false))));
        assertTrue(v.arrayConstant(List.of(Map.of("foo", "bar"))));
        assertFalse(v.arrayConstant(List.of(Map.of("foo", "other"))));
    }

    @Test
    void mixedEnumAndBooleanNumberDistinction() {
        Checks v = CompiledInstances.create(Checks.class);
        assertTrue(v.mixedEnum(6.0));
        assertTrue(v.mixedEnum("foo"));
        assertTrue(v.mixedEnum(List.of()));
        assertTrue(v.mixedEnum(Map.of("foo", 12)));
        assertFalse(v.mixedEnum(Map.of("foo", "12")));
        assertTrue(v.falseArray(List.of(false)));
        assertFalse(v.falseArray(List.of(0)));
        assertTrue(v.structuralEnum(List.of(1.0)));
        assertTrue(v.structuralEnum(Map.of("a", false)));
        assertFalse(v.structuralEnum(Map.of("a", 0)));
        assertTrue(v.pojoValue(new Bean(true, 1)));
        assertFalse(v.pojoValue(new Bean(false, 1)));
    }
}
