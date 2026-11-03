package org.sjf4j.testbench.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.JsonObject;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.schema.ValidationException;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Independent object keyword semantics without runtime SchemaPlan fallback. */
public class CompiledValidatorPropertyNamesDependentSchemasTest {
    @CompiledValidator(fallback = false)
    public interface Checks {
        @ValidJsonSchema("{\"propertyNames\":{\"pattern\":\"^a+$\"}}")
        boolean names(Object value);

        @ValidJsonSchema("{\"propertyNames\":false}")
        boolean noNames(Object value);

        @ValidJsonSchema("{\"propertyNames\":{\"maxLength\":5},"
                + "\"additionalProperties\":{\"type\":\"number\"}}")
        boolean namesAndAdditional(Object value);

        @ValidJsonSchema("{\"dependentSchemas\":{\"foo\":true,\"bar\":false}}")
        boolean booleanDependencies(Object value);

        @ValidJsonSchema("{\"dependentSchemas\":{\"a\":{\"required\":[\"b\"]},"
                + "\"b\":{\"properties\":{\"b\":{\"type\":\"integer\"}}}}}")
        boolean wholeObjectDependencies(Object value);

        @ValidJsonSchema("{\"properties\":{\"foo2\":{}},"
                + "\"dependentSchemas\":{\"foo\":{},\"foo2\":{\"properties\":{\"bar\":{}}}},"
                + "\"additionalProperties\":false}")
        boolean noCoverageFromDependencies(Object value);

        @ValidJsonSchema("{\"dependentSchemas\":{\"foo\":{\"required\":[\"bar\"]}}}")
        boolean pojoDependency(Bean value);

        @ValidJsonSchema("{\"propertyNames\":{\"maxLength\":3}}")
        boolean pojoName(Bean value);

        @ValidJsonSchema("{\"propertyNames\":{\"maxLength\":3}}")
        void requireValidNames(Object value);
    }

    public static final class Bean {
        public Object foo;
        public Object bar;
        public Bean() {}
        public Bean(Object foo, Object bar) {
            this.foo = foo;
            this.bar = bar;
        }
    }

    @Test
    void namesAreStringsEvenWhenTheirValuesAreNull() {
        Checks v = CompiledInstances.create(Checks.class);
        assertTrue(v.names(Map.of("aa", 1)));
        assertFalse(v.names(Map.of("ab", 1)));
        assertTrue(v.names(Map.of()));
        assertTrue(v.names(42));
        assertTrue(v.names(null));
        assertTrue(v.names(new JsonObject(Map.of("aaa", 1))));
        assertFalse(v.names(new JsonObject(Map.of("b", 1))));
        assertTrue(v.noNames(Map.of()));
        assertFalse(v.noNames(Map.of("a", 1)));
        assertTrue(v.noNames("not an object"));
        Map<String, Object> withNull = new LinkedHashMap<>();
        withNull.put("aaaaaa", null);
        assertFalse(v.namesAndAdditional(withNull));
        withNull.clear();
        withNull.put("apple", 4);
        assertTrue(v.namesAndAdditional(withNull));
        withNull.put("pear", "wrong");
        assertFalse(v.namesAndAdditional(withNull));
    }

    @Test
    void dependenciesApplyToTheWholeObjectAndTriggerOnPresence() {
        Checks v = CompiledInstances.create(Checks.class);
        assertTrue(v.booleanDependencies(Map.of("foo", "anything")));
        assertFalse(v.booleanDependencies(Map.of("bar", "anything")));
        assertTrue(v.booleanDependencies(Map.of()));
        assertTrue(v.booleanDependencies(7));

        assertTrue(v.wholeObjectDependencies(Map.of("a", 1, "b", 2)));
        assertFalse(v.wholeObjectDependencies(Map.of("a", 1)));
        assertFalse(v.wholeObjectDependencies(Map.of("a", 1, "b", "2")));
        assertTrue(v.wholeObjectDependencies(Map.of("b", 2)));
        assertFalse(v.wholeObjectDependencies(Map.of("b", 2.2)));
        Map<String, Object> withNullTrigger = new LinkedHashMap<>();
        withNullTrigger.put("a", null);
        assertFalse(v.wholeObjectDependencies(withNullTrigger));
        withNullTrigger.put("b", 3);
        assertTrue(v.wholeObjectDependencies(withNullTrigger));
    }

    @Test
    void dependenciesNeverExtendSiblingAdditionalPropertiesCoverage() {
        Checks v = CompiledInstances.create(Checks.class);
        assertFalse(v.noCoverageFromDependencies(Map.of("foo", 1)));
        assertFalse(v.noCoverageFromDependencies(Map.of("bar", 1)));
        assertFalse(v.noCoverageFromDependencies(Map.of("foo2", 1, "bar", 1)));
        assertTrue(v.noCoverageFromDependencies(Map.of("foo2", 1)));
    }

    @Test
    void propertyNameFailureUsesParentKeywordAndNamePath() {
        Checks validator = CompiledInstances.create(Checks.class);
        ValidationException failure = assertThrows(ValidationException.class,
                () -> validator.requireValidNames(Map.of("tooLong", 1)));
        assertEquals("propertyNames", failure.getResult().getLastError().getKeyword());
        assertEquals("/tooLong",
                failure.getResult().getLastError().getInstancePs().rootedPointerExpr());
        assertEquals("/propertyNames",
                failure.getResult().getLastError().getKeywordPs().rootedPointerExpr());
    }

    @Test
    void pojoAndObntUseIdenticalSchemaMeaning() {
        Checks v = CompiledInstances.create(Checks.class);
        assertTrue(v.pojoDependency(new Bean(1, 2)));
        assertTrue(v.pojoDependency(new Bean(null, 2)));
        assertTrue(v.pojoDependency(null));
        assertTrue(v.pojoName(new Bean(1, 2)));
    }
}
