package org.sjf4j.testbench.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.schema.ValidationException;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each array keyword is independent; only prefixItems/items share their local index scope. */
public class CompiledValidatorArrayItemsTest {
    @CompiledValidator(fallback = false)
    public interface Checks {
        @ValidJsonSchema("{\"prefixItems\":[{\"type\":\"integer\"},{\"type\":\"string\"}],"
                + "\"items\":{\"type\":\"boolean\"}}")
        boolean positionalAndRest(Object value);

        @ValidJsonSchema("{\"prefixItems\":[{\"type\":\"integer\"}],\"items\":false}")
        boolean noExtra(Object value);

        @ValidJsonSchema("{\"prefixItems\":[true,false],\"items\":true}")
        boolean booleanPrefixes(Object value);

        @ValidJsonSchema("{\"prefixItems\":[{\"type\":\"null\"}]}")
        boolean nullPrefix(Object value);

        @ValidJsonSchema("{\"allOf\":[{\"prefixItems\":[{\"minimum\":3}]}],"
                + "\"items\":{\"minimum\":5}}")
        boolean applicatorDoesNotShiftItems(Object value);

        @ValidJsonSchema("{\"uniqueItems\":true}")
        boolean unique(Object value);

        @ValidJsonSchema("{\"uniqueItems\":false}")
        boolean allowDuplicates(Object value);

        @ValidJsonSchema("{\"prefixItems\":[{\"type\":\"string\"}],"
                + "\"uniqueItems\":true,\"items\":false}")
        boolean constrainedUnique(Object value);

        @ValidJsonSchema("{\"prefixItems\":[{\"type\":\"integer\"}],\"items\":false}")
        boolean primitiveArray(int[] value);

        @ValidJsonSchema("{\"prefixItems\":[{\"type\":\"string\"}],\"items\":false}")
        void requirePrefix(Object value);
    }

    @Test
    void prefixAndItemsUseTheirOwnIndices() {
        Checks v = CompiledInstances.create(Checks.class);
        assertTrue(v.positionalAndRest(List.of(1, "two", true, false)));
        assertTrue(v.positionalAndRest(List.of(1, "two")));
        assertTrue(v.positionalAndRest(List.of(1)));
        assertTrue(v.positionalAndRest(List.of()));
        assertFalse(v.positionalAndRest(List.of("one", "two", true)));
        assertFalse(v.positionalAndRest(List.of(1, false)));
        assertFalse(v.positionalAndRest(List.of(1, "two", 3)));
        assertTrue(v.positionalAndRest("not an array"));
        assertTrue(v.positionalAndRest(null));
        assertTrue(v.positionalAndRest(new Object[]{1, "two", true}));
        assertFalse(v.positionalAndRest(new Object[]{1, "two", 2}));
    }

    @Test
    void booleanAndNullPrefixSchemas() {
        Checks v = CompiledInstances.create(Checks.class);
        assertTrue(v.noExtra(List.of()));
        assertTrue(v.noExtra(List.of(12)));
        assertFalse(v.noExtra(List.of(12, 1)));
        assertFalse(v.noExtra(List.of("12")));

        assertTrue(v.booleanPrefixes(List.of(1)));
        assertFalse(v.booleanPrefixes(List.of(1, 2)));
        assertTrue(v.booleanPrefixes(List.of()));
        assertTrue(v.nullPrefix(Arrays.asList((Object) null, "anything")));
        assertFalse(v.nullPrefix(List.of(1)));

        assertTrue(v.primitiveArray(new int[]{3}));
        assertFalse(v.primitiveArray(new int[]{3, 4}));
        assertTrue(v.primitiveArray(null));
    }

    @Test
    void applicatorPrefixNeverChangesSiblingItemsOffset() {
        Checks v = CompiledInstances.create(Checks.class);
        assertTrue(v.applicatorDoesNotShiftItems(List.of(5, 6)));
        assertFalse(v.applicatorDoesNotShiftItems(List.of(4, 6)));
        assertFalse(v.applicatorDoesNotShiftItems(List.of(6, 4)));
        assertTrue(v.applicatorDoesNotShiftItems(List.of()));
    }

    @Test
    void uniqueItemsUsesJsonStructuralEquality() {
        Checks v = CompiledInstances.create(Checks.class);
        assertTrue(v.unique(List.of(1, 2, 3)));
        assertFalse(v.unique(List.of(1, 2, 1)));
        assertFalse(v.unique(List.of(1, 1.0)));
        assertFalse(v.unique(Arrays.asList(null, null)));
        assertFalse(v.unique(List.of(Map.of("a", 1, "b", 2),
                Map.of("b", 2, "a", 1))));
        assertFalse(v.unique(List.of(List.of(1, 2), List.of(1, 2))));
        assertTrue(v.unique("not an array"));
        assertTrue(v.unique(null));
        assertTrue(v.allowDuplicates(List.of(1, 1, 1)));
        assertTrue(v.constrainedUnique(List.of("ok")));
        assertFalse(v.constrainedUnique(List.of("ok", "ok")));
        assertFalse(v.constrainedUnique(List.of("ok", 1)));
    }

    @Test
    void requireValidReportsActualPrefixIndex() {
        Checks v = CompiledInstances.create(Checks.class);
        ValidationException ex = assertThrows(ValidationException.class,
                () -> v.requirePrefix(List.of(1)));
        assertEquals("/0", ex.getResult().getLastError().getInstancePs().rootedPointerExpr());
        assertEquals("/prefixItems/0/type",
                ex.getResult().getLastError().getKeywordPs().rootedPointerExpr());
    }
}
