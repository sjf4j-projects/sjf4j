package org.sjf4j.testbench.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.CompiledInstances;
import org.sjf4j.annotation.schema.CompiledValidator;
import org.sjf4j.annotation.schema.ValidJsonSchema;

import static org.junit.jupiter.api.Assertions.*;

/** Draft 2020-12 content vocabulary is annotation-only for validation. */
public class CompiledValidatorContentAnnotationsTest {
    @CompiledValidator(fallback = false)
    public interface Checks {
        @ValidJsonSchema("{\"contentMediaType\":\"application/json\"}")
        boolean media(Object value);

        @ValidJsonSchema("{\"contentEncoding\":\"base64\"}")
        boolean encoding(Object value);

        @ValidJsonSchema("{\"contentMediaType\":\"application/json\","
                + "\"contentEncoding\":\"base64\","
                + "\"contentSchema\":{\"type\":\"object\","
                + "\"required\":[\"foo\"],\"properties\":{\"foo\":{\"type\":\"string\"}}}}")
        boolean contentSchema(Object value);

        @ValidJsonSchema("{\"type\":\"string\",\"minLength\":2,"
                + "\"contentMediaType\":\"application/json\",\"contentEncoding\":\"base64\"}")
        boolean withIndependentAssertion(Object value);
    }

    @Test
    void contentAnnotationsNeverActAsValidationAssertions() {
        Checks checks = CompiledInstances.create(Checks.class);
        assertTrue(checks.media("not json"));
        assertTrue(checks.media(3));
        assertTrue(checks.encoding("%not-base64%"));
        assertTrue(checks.contentSchema("%also-not-valid%"));
        assertTrue(checks.contentSchema("e30="));
        assertTrue(checks.contentSchema(null));
    }

    @Test
    void unrelatedAssertionsStillApply() {
        Checks checks = CompiledInstances.create(Checks.class);
        assertFalse(checks.withIndependentAssertion(123));
        assertFalse(checks.withIndependentAssertion("x"));
        assertTrue(checks.withIndependentAssertion("%not-valid-base64%"));
    }
}
