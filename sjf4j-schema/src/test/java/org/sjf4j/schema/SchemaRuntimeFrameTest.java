package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.exception.NodeException;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SchemaRuntimeFrameTest {
    private static SchemaPlan plan(String json) {
        return JsonSchema.fromJson(json).createPlan();
    }

    @Test
    void sequentialReferencesAreNotCycles() {
        SchemaPlan schema = plan("{\"$defs\":{\"text\":{\"type\":\"string\"}},"
                + "\"allOf\":[{\"$ref\":\"#/$defs/text\"},"
                + "{\"$ref\":\"#/$defs/text\"},{\"$ref\":\"#/$defs/text\"}]}");
        assertTrue(schema.isValid("hello"));
        assertFalse(schema.isValid(42));
        assertTrue(schema.validate("hello").isValid());
    }

    @Test
    void recursiveSchemaCanAdvanceToChildInstance() {
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
    void directReferenceLoopIsRejected() {
        SchemaPlan schema = plan("{\"$ref\":\"#\"}");
        assertThrows(NodeException.class, () -> schema.isValid("x"));
    }

    @Test
    void booleanOnlyAndDiagnosticPathsAgreeOnFailures() {
        SchemaPlan schema = plan("{\"type\":\"object\","
                + "\"properties\":{\"value\":{\"type\":\"integer\",\"minimum\":0}}}");
        Map<String, Object> invalid = Collections.singletonMap("value", -1);
        assertFalse(schema.isValid(invalid));
        ValidationResult first = schema.validate(invalid, true, false);
        assertFalse(first.isValid());
        assertTrue(schema.validate(Collections.singletonMap("value", 1)).isValid());
    }

    @Test
    void requiredSuccessMustNotSkipFollowingProperties() {
        SchemaPlan schema = plan("{\"type\":\"object\",\"required\":[\"id\"],"
                + "\"properties\":{\"id\":{\"type\":\"integer\",\"minimum\":1}}}");
        assertTrue(schema.validate(Collections.singletonMap("id", 2), true, false).isValid());
        assertFalse(schema.validate(Collections.singletonMap("id", 0), true, false).isValid());
        assertFalse(schema.isValid(Collections.singletonMap("id", 0)));
    }

    @Test
    void sizeSuccessMustNotSkipFollowingItems() {
        SchemaPlan schema = plan("{\"type\":\"array\",\"maxItems\":4,"
                + "\"items\":{\"type\":\"integer\",\"minimum\":1}}");
        assertTrue(schema.isValid(Collections.singletonList(2)));
        assertFalse(schema.isValid(Collections.singletonList(0)));
        assertFalse(schema.validate(Collections.singletonList(0), true, false).isValid());
    }

    @Test
    void inlineEvaluatedMaskHandlesBoundaryAndOverflow() {
        EvaluationBits a = new EvaluationBits();
        a.set(0);
        a.set(62, 66);
        assertTrue(a.get(0));
        assertTrue(a.get(62));
        assertTrue(a.get(63));
        assertTrue(a.get(64));
        assertTrue(a.get(65));
        assertFalse(a.get(66));

        EvaluationBits b = new EvaluationBits();
        b.set(130);
        a.or(b);
        assertTrue(a.get(130));
        assertFalse(a.get(129));
    }

    @Test
    void nestedBranchesKeepUnevaluatedLocationsIsolated() {
        SchemaPlan schema = plan("{\"anyOf\":["
                + "{\"properties\":{\"a\":{\"type\":\"integer\"}}},"
                + "{\"properties\":{\"b\":{\"type\":\"string\"}}}],"
                + "\"unevaluatedProperties\":false}");
        Map<String, Object> matching = new HashMap<>();
        matching.put("a", 1);
        matching.put("b", "ok");
        assertTrue(schema.isValid(matching));
        assertTrue(schema.validate(matching).isValid());
        matching.put("c", true);
        assertFalse(schema.isValid(matching));
    }
}
