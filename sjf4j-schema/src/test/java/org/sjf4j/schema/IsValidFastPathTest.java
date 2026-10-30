package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class IsValidFastPathTest {
    private static void check(String schema, Object valid, Object invalid) {
        SchemaPlan plan = JsonSchema.fromJson(schema).createPlan();
        assertTrue(plan.isValid(valid));
        assertTrue(plan.validate(valid, true, false).isValid());
        assertFalse(plan.isValid(invalid));
        assertFalse(plan.validate(invalid, true, false).isValid());
    }

    @Test
    void booleanSchemasAndSimpleFailures() {
        assertTrue(JsonSchema.fromJson("true").createPlan().isValid(null));
        assertFalse(JsonSchema.fromJson("false").createPlan().isValid(null));
        check("{\"type\":\"integer\"}", 1, "bad");
        check("{\"const\":{\"value\":1}}",
                Collections.singletonMap("value", 1), Collections.singletonMap("value", 2));
        check("{\"required\":[\"name\"]}",
                Collections.singletonMap("name", "ok"), Collections.emptyMap());
        check("{\"type\":\"array\",\"minItems\":2}",
                Arrays.asList(1, 2), Collections.singletonList(1));
    }

    @Test
    void ignoredBranchFailuresDoNotMakeParentInvalid() {
        check("{\"anyOf\":[{\"type\":\"number\"},{\"type\":\"string\"}]}",
                "ok", true);
        check("{\"oneOf\":[{\"type\":\"number\"},{\"type\":\"string\"}]}",
                "ok", true);
        check("{\"not\":{\"type\":\"number\"}}", "ok", 1);
        check("{\"type\":\"array\",\"contains\":{\"type\":\"string\"}}",
                Arrays.asList(1, "ok"), Arrays.asList(1, 2));
        check("{\"if\":{\"type\":\"string\"},\"then\":{\"minLength\":3},\"else\":{\"type\":\"integer\"}}",
                1, "x");
    }

    @Test
    void evaluatedAnnotationsAndStrictFormat() {
        SchemaPlan plan = JsonSchema.fromJson("{\"type\":\"object\","
                + "\"properties\":{\"name\":{\"type\":\"string\"}},"
                + "\"unevaluatedProperties\":{\"type\":\"integer\"}}").createPlan();
        Map<String, Object> valid = new HashMap<>();
        valid.put("name", "ok");
        valid.put("count", 1);
        assertTrue(plan.isValid(valid));
        valid.put("count", "invalid");
        assertFalse(plan.isValid(valid));
        assertFalse(plan.validate(valid, true, false).isValid());
    }
}
