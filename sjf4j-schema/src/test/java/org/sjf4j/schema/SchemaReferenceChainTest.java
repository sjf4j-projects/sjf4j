package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.exception.NodeException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaReferenceChainTest {

    private static SchemaPlan plan(String json) {
        return JsonSchema.fromJson(json).createPlan();
    }

    @Test
    void sequentialReferencesToSamePlanDoNotFormCycle() {
        SchemaPlan schema = plan("{\"$defs\":{\"text\":{\"type\":\"string\"}},"
                + "\"allOf\":[{\"$ref\":\"#/$defs/text\"},"
                + "{\"$ref\":\"#/$defs/text\"},{\"$ref\":\"#/$defs/text\"}]}");

        assertTrue(schema.isValid("hello"));
        assertTrue(schema.validate("hello").isValid());
        assertFalse(schema.isValid(42));
    }

    @Test
    void nestedReferencesAreReleasedBetweenSiblingBranches() {
        SchemaPlan schema = plan("{\"$defs\":{"
                + "\"outer\":{\"$ref\":\"#/$defs/inner\"},"
                + "\"inner\":{\"type\":\"string\"}},"
                + "\"allOf\":[{\"$ref\":\"#/$defs/outer\"},"
                + "{\"$ref\":\"#/$defs/outer\"},{\"$ref\":\"#/$defs/outer\"}]}");

        assertTrue(schema.isValid("hello"));
        assertTrue(schema.validate("hello").isValid());
    }

    @Test
    void recursiveReferenceCanAdvanceToChildInstance() {
        SchemaPlan schema = plan("{\"$defs\":{\"node\":{\"type\":\"object\","
                + "\"properties\":{\"child\":{\"$ref\":\"#/$defs/node\"}}}},"
                + "\"$ref\":\"#/$defs/node\"}");
        Map<String, Object> nested = new HashMap<>();
        nested.put("child", Collections.singletonMap("child", Collections.emptyMap()));

        assertTrue(schema.isValid(nested));
        assertTrue(schema.validate(nested).isValid());
        assertFalse(schema.isValid(Collections.singletonMap("child", 7)));
    }

    @Test
    void directAndMutualReferenceCyclesAreRejected() {
        SchemaPlan direct = plan("{\"$ref\":\"#\"}");
        assertThrows(NodeException.class, () -> direct.isValid("value"));

        SchemaPlan mutual = plan("{\"$defs\":{"
                + "\"a\":{\"$ref\":\"#/$defs/b\"},"
                + "\"b\":{\"$ref\":\"#/$defs/a\"}},"
                + "\"$ref\":\"#/$defs/a\"}");
        assertThrows(NodeException.class, () -> mutual.isValid("value"));
    }

    @Test
    void sequentialDynamicReferencesToSamePlanDoNotFormCycle() {
        SchemaPlan schema = plan("{\"$defs\":{\"text\":{"
                + "\"$dynamicAnchor\":\"text\",\"type\":\"string\"}},"
                + "\"allOf\":[{\"$dynamicRef\":\"#text\"},"
                + "{\"$dynamicRef\":\"#text\"},{\"$dynamicRef\":\"#text\"}]}");

        assertTrue(schema.isValid("hello"));
        assertTrue(schema.validate("hello").isValid());
        assertFalse(schema.isValid(42));
    }
}
