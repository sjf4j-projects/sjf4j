package org.sjf4j.schema;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluationBitsTest {
    private static final int[] SIZES = {8, 32, 64, 65, 128};

    @Test
    void matchesBitSetAcrossInlineAndOverflowBoundaries() {
        for (int size : SIZES) {
            BitSet expected = new BitSet();
            EvaluationBits actual = new EvaluationBits();

            for (int i = 0; i < size; i += 3) {
                expected.set(i);
                actual.set(i);
            }
            expected.set(size / 2, size);
            actual.set(size / 2, size);
            expected.set(63, 66);
            actual.set(63, 66);
            expected.set(70, 70);
            actual.set(70, 70);

            BitSet other = new BitSet();
            EvaluationBits otherActual = new EvaluationBits();
            for (int i = 1; i < size; i += 4) {
                other.set(i);
                otherActual.set(i);
            }
            other.set(127);
            otherActual.set(127);
            expected.or(other);
            actual.or(otherActual);

            for (int i = 0; i <= 130; i++) {
                assertEquals(expected.get(i), actual.get(i), "size=" + size + " index=" + i);
            }

            // The overflow storage must not be shared after OR.
            otherActual.set(130);
            assertFalse(actual.get(130));
        }
    }

    @Test
    void rangeMarkingPreservesUnevaluatedItemsAtBoundary() {
        for (int size : SIZES) {
            StringBuilder schema = new StringBuilder("{\"prefixItems\":[");
            List<Integer> values = new ArrayList<>();
            for (int i = 0; i < size; i++) {
                if (i > 0) schema.append(',');
                schema.append("true");
                values.add(i);
            }
            schema.append("],\"unevaluatedItems\":false}");
            SchemaPlan plan = JsonSchema.fromJson(schema.toString()).createPlan();

            assertTrue(plan.validate(values).isValid(), "size=" + size);
            values.add(size);
            assertFalse(plan.validate(values).isValid(), "size=" + size + " with extra item");
        }
    }

    @Test
    void allOfMergesEvaluatedPropertiesAbove64() {
        for (int size : SIZES) {
            StringBuilder schema = new StringBuilder("{\"allOf\":[");
            for (int parity = 0; parity < 2; parity++) {
                if (parity != 0) schema.append(',');
                schema.append("{\"properties\":{");
                boolean first = true;
                for (int i = parity; i < size; i += 2) {
                    if (!first) schema.append(',');
                    first = false;
                    schema.append("\"p").append(i).append("\":true");
                }
                schema.append("}}");
            }
            schema.append("],\"unevaluatedProperties\":false}");

            Map<String, Object> values = new LinkedHashMap<>();
            for (int i = 0; i < size; i++) values.put("p" + i, i);
            SchemaPlan plan = JsonSchema.fromJson(schema.toString()).createPlan();

            assertTrue(plan.validate(values).isValid(), "size=" + size);
            values.put("extra", 1);
            assertFalse(plan.validate(values).isValid(), "size=" + size + " with extra property");
        }
    }
}
