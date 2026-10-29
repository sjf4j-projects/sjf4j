package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class ValidationContextResultTest {
    private static void check(String json, Object valid, Object invalid) {
        SchemaPlan plan = JsonSchema.fromJson(json).createPlan();
        assertTrue(plan.isValid(valid));
        assertTrue(plan.validate(valid).isValid());
        assertTrue(plan.validate(valid, true, false).isValid());
        assertFalse(plan.isValid(invalid));
        assertFalse(plan.validate(invalid).isValid());
        ValidationResult failure = plan.validate(invalid, true, false);
        assertFalse(failure.isValid());
        assertFalse(failure.getErrors().isEmpty());
    }

    @Test void scalarAndObjectFailures() {
        check("{\"type\":\"integer\"}", 4, "bad");
        check("{\"required\":[\"x\"]}", Collections.singletonMap("x", 1), Collections.emptyMap());
        check("{\"type\":\"array\",\"minItems\":2}", Arrays.asList(1, 2), Arrays.asList(1));
    }

    @Test void ignoredBranchesDoNotChangeResult() {
        check("{\"oneOf\":[{\"type\":\"string\"},{\"type\":\"integer\"}]}", "yes", true);
        check("{\"anyOf\":[{\"type\":\"string\"},{\"type\":\"integer\"}]}", 1, true);
        check("{\"not\":{\"type\":\"integer\"}}", "yes", 1);
        check("{\"type\":\"array\",\"contains\":{\"type\":\"string\"}}", Arrays.asList(1,"yes"), Arrays.asList(1,2));
        check("{\"if\":{\"type\":\"integer\"},\"then\":{\"minimum\":3},\"else\":{\"type\":\"string\"}}", "ok", 1);
    }

    @Test void unevaluatedResult() {
        check("{\"properties\":{\"x\":{\"type\":\"string\"}},\"unevaluatedProperties\":{\"type\":\"integer\"}}",
                Collections.singletonMap("extra",1), Collections.singletonMap("extra","wrong"));
    }
}
