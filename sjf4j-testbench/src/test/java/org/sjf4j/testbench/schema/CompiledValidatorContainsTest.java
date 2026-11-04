package org.sjf4j.testbench.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;
import org.sjf4j.schema.ValidationException;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Array contains constraints are independent of sibling item assertions. */
public class CompiledValidatorContainsTest {
    @CompiledValidator(fallback = false)
    public interface Checks {
        @ValidJsonSchema("{\"contains\":{\"minimum\":5}}")
        boolean anyAtLeastFive(Object value);

        @ValidJsonSchema("{\"contains\":true}")
        boolean nonEmpty(Object value);

        @ValidJsonSchema("{\"contains\":false}")
        boolean noPossibleMatch(Object value);

        @ValidJsonSchema("{\"contains\":{\"const\":1},\"minContains\":0,\"maxContains\":1}")
        boolean zeroOrOne(Object value);

        @ValidJsonSchema("{\"contains\":{\"const\":1},\"minContains\":2,\"maxContains\":2}")
        boolean exactlyTwo(Object value);

        @ValidJsonSchema("{\"minContains\":7,\"maxContains\":0}")
        boolean ignoredWithoutContains(Object value);

        @ValidJsonSchema("{\"items\":{\"multipleOf\":2},\"contains\":{\"multipleOf\":3}}")
        boolean itemsAndContains(Object value);

        @ValidJsonSchema("{\"contains\":{\"type\":\"null\"},\"minContains\":1}")
        boolean nullMatch(Object value);

        @ValidJsonSchema("{\"contains\":{\"if\":false,\"else\":true}}")
        boolean conditionalMatch(Object value);

        @ValidJsonSchema("{\"contains\":{\"const\":1},\"maxContains\":0}")
        void requireNoOnes(Object value);

        @ValidJsonSchema("{\"contains\":{\"minimum\":5},\"maxContains\":1}")
        boolean primitiveArray(int[] value);
    }

    @Test
    void defaultMinimumAndBooleanSubschemas() {
        Checks v = CompiledInstances.create(Checks.class);
        assertTrue(v.anyAtLeastFive(List.of(0, 5)));
        assertFalse(v.anyAtLeastFive(List.of(1, 2)));
        assertFalse(v.anyAtLeastFive(List.of()));
        assertTrue(v.anyAtLeastFive("not array"));
        assertTrue(v.anyAtLeastFive(null));
        assertTrue(v.nonEmpty(List.of(1)));
        assertFalse(v.nonEmpty(List.of()));
        assertFalse(v.noPossibleMatch(List.of(1)));
        assertFalse(v.noPossibleMatch(List.of()));
        assertTrue(v.noPossibleMatch(3));
        assertTrue(v.conditionalMatch(List.of("anything")));
        assertFalse(v.conditionalMatch(List.of()));
    }

    @Test
    void countBoundsIncludeZeroAndIgnoreLoneBounds() {
        Checks v = CompiledInstances.create(Checks.class);
        assertTrue(v.zeroOrOne(List.of()));
        assertTrue(v.zeroOrOne(List.of(1)));
        assertFalse(v.zeroOrOne(List.of(1, 1)));
        assertTrue(v.exactlyTwo(List.of(1, 1, 2)));
        assertFalse(v.exactlyTwo(List.of(1)));
        assertFalse(v.exactlyTwo(List.of(1, 1, 1)));
        assertTrue(v.ignoredWithoutContains(List.of(1, 1)));
        assertTrue(v.ignoredWithoutContains(3));
        assertTrue(v.primitiveArray(new int[]{3, 5}));
        assertFalse(v.primitiveArray(new int[]{5, 6}));
        assertTrue(v.primitiveArray(null));
    }

    @Test
    void containsIsIndependentOfItemsAndNullIsMatchable() {
        Checks v = CompiledInstances.create(Checks.class);
        assertTrue(v.itemsAndContains(List.of(6, 2)));
        assertFalse(v.itemsAndContains(List.of(4, 2)));
        assertFalse(v.itemsAndContains(List.of(3, 6)));
        assertTrue(v.nullMatch(Arrays.asList(null, "x")));
        assertFalse(v.nullMatch(List.of(1, "x")));
    }

    @Test
    void aggregateFailureReportsContainsKeywordAtArrayPath() {
        Checks v = CompiledInstances.create(Checks.class);
        ValidationException ex = assertThrows(ValidationException.class,
                () -> v.requireNoOnes(List.of(1)));
        assertEquals("maxContains", ex.getResult().getLastError().getKeyword());
        assertEquals("", ex.getResult().getLastError().getInstancePs().rootedPointerExpr());
        assertEquals("/maxContains", ex.getResult().getLastError().getKeywordPs().rootedPointerExpr());
    }
}
