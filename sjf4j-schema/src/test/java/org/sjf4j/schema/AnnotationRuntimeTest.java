package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonObject;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/** Runtime annotation collection and branch transaction semantics. */
class AnnotationRuntimeTest {
    private static final ValidationOptions ANNOTATIONS = ValidationOptions.builder()
            .collectAnnotations(true).build();

    private static ValidationResult validate(String schema, Object value) {
        return JsonSchema.fromJson(schema).createPlan().validate(value, ANNOTATIONS);
    }

    private static List<String> values(ValidationResult result) {
        return result.getAnnotations().stream()
                .map(m -> String.valueOf(m.getValue()))
                .collect(Collectors.toList());
    }

    @Test
    void collectsMetadataIncludingFalseAndNull() {
        ValidationResult result = validate("{\"title\":\"User\",\"description\":\"Info\","
                + "\"default\":null,\"examples\":[1,2],"
                + "\"deprecated\":false,\"readOnly\":false,\"writeOnly\":true}", 42);
        assertTrue(result.isValid());
        assertEquals(7, result.getAnnotations().size());
        assertEquals(Arrays.asList("title", "description", "default", "examples",
                "deprecated", "readOnly", "writeOnly"),
                result.getAnnotations().stream().map(ValidationResult.Message::getKeyword)
                        .collect(Collectors.toList()));
        assertNull(result.getAnnotations().get(2).getValue());
        assertEquals(Boolean.FALSE, result.getAnnotations().get(4).getValue());
        assertEquals("/title", result.getAnnotations().get(0).getKeywordPs().rootedPointerExpr());
        assertEquals("", result.getAnnotations().get(0).getInstancePs().rootedPointerExpr());
    }

    @Test
    void failedSchemaDropsItsOwnMetadata() {
        ValidationResult invalid = validate("{\"title\":\"Age\",\"minimum\":18}", 16);
        assertFalse(invalid.isValid());
        assertTrue(invalid.getAnnotations().isEmpty());
        assertFalse(invalid.getErrors().isEmpty());

        ValidationResult valid = validate("{\"title\":\"Age\",\"minimum\":18}", 20);
        assertTrue(valid.isValid());
        assertEquals(Collections.singletonList("Age"), values(valid));
    }

    @Test
    void nestedPropertiesAndItemsHaveCorrectInstancePaths() {
        ValidationResult objectResult = validate(
                "{\"properties\":{\"name\":{\"title\":\"Name\"}}}",
                JsonObject.of("name", "Ada"));
        assertTrue(objectResult.isValid());
        assertEquals("/name", objectResult.getAnnotations().get(0).getInstancePs().rootedPointerExpr());
        assertEquals("/properties/name/title",
                objectResult.getAnnotations().get(0).getKeywordPs().rootedPointerExpr());

        ValidationResult arrayResult = validate("{\"items\":{\"title\":\"Item\"}}",
                Arrays.asList(1, 2));
        assertTrue(arrayResult.isValid());
        assertEquals(2, arrayResult.getAnnotations().size());
        assertEquals("/0", arrayResult.getAnnotations().get(0).getInstancePs().rootedPointerExpr());
        assertEquals("/1", arrayResult.getAnnotations().get(1).getInstancePs().rootedPointerExpr());
    }

    @Test
    void anyOfCollectsAllSuccessfulBranches() {
        ValidationResult result = validate(
                "{\"anyOf\":[{\"type\":\"integer\",\"title\":\"Integer\"},"
                        + "{\"type\":\"number\",\"title\":\"Number\"},"
                        + "{\"type\":\"string\",\"title\":\"String\"}]}", 42);
        assertTrue(result.isValid());
        assertEquals(Arrays.asList("Integer", "Number"), values(result));

        ValidationResult withFailFast = JsonSchema.fromJson(
                "{\"anyOf\":[{\"title\":\"A\"},{\"title\":\"B\"}]}")
                .createPlan().validate(1, ValidationOptions.builder()
                        .collectAnnotations(true).failFast(true).build());
        assertEquals(Arrays.asList("A", "B"), values(withFailFast));
    }

    @Test
    void oneOfAllOfAndNotDiscardFailedBranches() {
        ValidationResult only = validate("{\"oneOf\":["
                + "{\"type\":\"string\",\"title\":\"String\"},"
                + "{\"type\":\"integer\",\"title\":\"Integer\"}]}", 2);
        assertTrue(only.isValid());
        assertEquals(Collections.singletonList("Integer"), values(only));

        ValidationResult multiple = validate(
                "{\"oneOf\":[{\"title\":\"A\"},{\"title\":\"B\"}]}", 2);
        assertFalse(multiple.isValid());
        assertTrue(multiple.getAnnotations().isEmpty());

        ValidationResult all = validate(
                "{\"allOf\":[{\"title\":\"A\"},{\"title\":\"B\",\"type\":\"string\"}]}", 1);
        assertFalse(all.isValid());
        assertTrue(all.getAnnotations().isEmpty());

        ValidationResult not = validate("{\"title\":\"Outer\","
                + "\"not\":{\"type\":\"integer\",\"title\":\"Inner\"}}", "text");
        assertTrue(not.isValid());
        assertEquals(Collections.singletonList("Outer"), values(not));
    }

    @Test
    void ifKeepsSuccessfulConditionAnnotations() {
        String schema = "{\"if\":{\"type\":\"string\",\"title\":\"If\"},"
                + "\"then\":{\"title\":\"Then\"},\"else\":{\"title\":\"Else\"}}";
        assertEquals(Arrays.asList("If", "Then"), values(validate(schema, "text")));
        assertEquals(Collections.singletonList("Else"), values(validate(schema, 42)));
    }

    @Test
    void containsMustScanAllMatches() {
        ValidationResult result = validate(
                "{\"contains\":{\"type\":\"integer\",\"title\":\"Match\"}}",
                Arrays.asList(1, "x", 2));
        assertTrue(result.isValid());
        assertEquals(2, result.getAnnotations().size());
        assertEquals("/0", result.getAnnotations().get(0).getInstancePs().rootedPointerExpr());
        assertEquals("/2", result.getAnnotations().get(1).getInstancePs().rootedPointerExpr());
    }

    @Test
    void propertyNamesDoesNotExposeNameAnnotations() {
        Map<String, Object> value = new HashMap<>();
        value.put("name", 1);
        ValidationResult result = validate(
                "{\"propertyNames\":{\"title\":\"Key\",\"type\":\"string\"},"
                        + "\"title\":\"Object\"}", value);
        assertTrue(result.isValid());
        assertEquals(Collections.singletonList("Object"), values(result));
    }

    @Test
    void formatAnnotationRequiresStringInstance() {
        ValidationResult string = validate("{\"format\":\"email\"}", "foo@bar.com");
        assertTrue(string.isValid());
        assertEquals(Collections.singletonList("email"), values(string));

        ValidationResult number = validate("{\"format\":\"email\"}", 42);
        assertTrue(number.isValid());
        assertTrue(number.getAnnotations().isEmpty());
    }

    @Test
    void contentAnnotationsRequireStringAndMediaTypeForSchema() {
        String schema = "{\"contentEncoding\":\"base64\","
                + "\"contentMediaType\":\"application/json\","
                + "\"contentSchema\":{\"type\":\"number\"}}";
        ValidationResult string = validate(schema, "42");
        assertTrue(string.isValid());
        assertEquals(Arrays.asList("base64", "application/json", "{type=number}"),
                values(string));

        ValidationResult number = validate(schema, 42);
        assertTrue(number.isValid());
        assertTrue(number.getAnnotations().isEmpty());

        ValidationResult withoutMediaType = validate(
                "{\"contentSchema\":{\"type\":\"number\"}}", "42");
        assertTrue(withoutMediaType.isValid());
        assertTrue(withoutMediaType.getAnnotations().isEmpty());
    }

    @Test
    void unknownKeywordAnnotationsPreserveNameAndValue() {
        ValidationResult result = validate("{\"x-custom\":false,"
                + "\"feature.name\":{\"enabled\":true},\"x-null\":null}", 42);
        assertTrue(result.isValid());
        assertEquals(Arrays.asList("x-custom", "feature.name", "x-null"),
                result.getAnnotations().stream().map(ValidationResult.Message::getKeyword)
                        .collect(Collectors.toList()));
        assertEquals(Boolean.FALSE, result.getAnnotations().get(0).getValue());
        assertNull(result.getAnnotations().get(2).getValue());

        ValidationResult known = validate("{\"type\":\"number\",\"$comment\":\"note\"}", 42);
        assertTrue(known.isValid());
        assertTrue(known.getAnnotations().isEmpty());
    }

    @Test
    void disabledCollectionDoesNotChangeLegacyResults() {
        SchemaPlan plan = JsonSchema.fromJson(
                "{\"type\":\"object\",\"title\":\"Object\","
                        + "\"required\":[\"missing\"]}").createPlan();
        ValidationResult old = plan.validate(JsonObject.of());
        ValidationResult disabled = plan.validate(JsonObject.of(),
                ValidationOptions.builder().build());
        assertEquals(old.isValid(), disabled.isValid());
        assertEquals(old.count(), disabled.count());
        assertTrue(old.getAnnotations().isEmpty());
        assertTrue(disabled.getAnnotations().isEmpty());
    }
}
