package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.exception.SchemaException;
import org.sjf4j.JsonArray;
import org.sjf4j.Sjf4j;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SchemaValidationTest {

    @Test
    public void testTypeEnumConst() {
        String json =
                "{\n" +
                "  \"type\": \"string\",\n" +
                "  \"enum\": [\"a\", \"b\"],\n" +
                "  \"const\": \"a\"\n" +
                "}\n";
        JsonSchema schema = JsonSchema.fromJson(json);
        SchemaPlan plan = schema.createPlan();

        assertTrue(plan.isValid("a"));
        assertFalse(plan.isValid("b"));
        assertFalse(plan.isValid(1));
    }

    @Test
    public void testNumberKeywords() {
        String json =
                "{\n" +
                "  \"type\": \"number\",\n" +
                "  \"minimum\": 0,\n" +
                "  \"exclusiveMaximum\": 10,\n" +
                "  \"multipleOf\": 2\n" +
                "}\n";
        JsonSchema schema = JsonSchema.fromJson(json);
        SchemaPlan plan = schema.createPlan();

        assertTrue(plan.isValid(2));
        assertTrue(plan.isValid(8));
        assertFalse(plan.isValid(10));
        assertFalse(plan.isValid(3));
    }

    @Test
    public void testStringKeywords() {
        String json =
                "{\n" +
                    "  \"type\": \"string\",\n" +
                    "  \"minLength\": 2,\n" +
                    "  \"maxLength\": 4,\n" +
                    "  \"pattern\": \"^[a-z]+$\"\n" +
                    "}\n";
        JsonSchema schema = JsonSchema.fromJson(json);
        SchemaPlan plan = schema.createPlan();

        assertTrue(plan.isValid("ab"));
        assertTrue(plan.isValid("abcd"));
        assertFalse(plan.isValid("a"));
        assertFalse(plan.isValid("abcde"));
        assertFalse(plan.isValid("AB"));
    }

    @Test
    public void testStringLengthCountsCodePoints() {
        String json =
                "{\n" +
                "  \"type\": \"string\",\n" +
                "  \"minLength\": 2,\n" +
                "  \"maxLength\": 2\n" +
                "}\n";
        JsonSchema schema = JsonSchema.fromJson(json);
        SchemaPlan plan = schema.createPlan();

        assertTrue(plan.isValid("e\u0301"));
    }

    @Test
    public void testArrayKeywords() {
        String json =
                "{\n" +
                        "  \"type\": \"array\",\n" +
                        "  \"minItems\": 1,\n" +
                        "  \"maxItems\": 3,\n" +
                        "  \"uniqueItems\": true,\n" +
                        "  \"items\": { \"type\": \"integer\" }\n" +
                        "}\n";
        JsonSchema schema = JsonSchema.fromJson(json);
        SchemaPlan plan = schema.createPlan();

        assertTrue(plan.isValid(Arrays.asList(1, 2)));
        assertFalse(plan.isValid(Collections.emptyList()));
        assertFalse(plan.isValid(Arrays.asList(1, 1)));
        assertFalse(plan.isValid(Arrays.asList(1, "a")));
    }

    @Test
    public void testTupleItems() {
        String json =
                "{\n" +
                "  \"type\": \"array\",\n" +
                "  \"prefixItems\": [\n" +
                "    { \"type\": \"string\" },\n" +
                "    { \"type\": \"number\" }\n" +
                "  ],\n" +
                "  \"items\": false\n" +
                "}\n";
        JsonSchema schema = JsonSchema.fromJson(json);
        SchemaPlan plan = schema.createPlan();

        assertTrue(plan.isValid(Arrays.asList("a", 1)));
        assertFalse(plan.isValid(Arrays.asList("a", 1, 2)));
    }

    @Test
    public void testObjectProperties() {
        String json =
                "{\n" +
                        "  \"type\": \"object\",\n" +
                        "  \"required\": [\"a\"],\n" +
                        "  \"properties\": {\n" +
                        "    \"a\": { \"type\": \"number\" },\n" +
                        "    \"b\": { \"type\": \"string\" }\n" +
                        "  }\n" +
                        "}\n";
        JsonSchema schema = JsonSchema.fromJson(json);
        SchemaPlan plan = schema.createPlan();

        Map<String, Object> ok = new HashMap<String, Object>();
        ok.put("a", 1);
        ok.put("b", "x");

        Map<String, Object> bad = new HashMap<String, Object>();
        bad.put("b", "x");

        assertTrue(plan.isValid(ok));
        assertFalse(plan.isValid(bad));
    }

    @Test
    public void testAdditionalAndPatternProperties() {
        String json =
                "{\n" +
                "  \"type\": \"object\",\n" +
                "  \"patternProperties\": {\n" +
                "    \"^x-\": { \"type\": \"number\" }\n" +
                "  },\n" +
                "  \"additionalProperties\": false\n" +
                "}\n";
        JsonSchema schema = JsonSchema.fromJson(json);
        SchemaPlan plan = schema.createPlan();

        Map<String, Object> ok = new HashMap<String, Object>();
        ok.put("x-a", 1);

        Map<String, Object> bad = new HashMap<String, Object>();
        bad.put("y", 1);

        assertTrue(plan.isValid(ok));
        assertFalse(plan.isValid(bad));
    }

    @Test
    public void testAdditionalPropertiesFalseErrorMessage() {
        String json =
                "{\n" +
                "  \"type\": \"object\",\n" +
                "  \"additionalProperties\": false\n" +
                "}\n";
        JsonSchema schema = JsonSchema.fromJson(json);
        SchemaPlan plan = schema.createPlan();

        Map<String, Object> bad = new HashMap<String, Object>();
        bad.put("y", 1);

        ValidationResult result = plan.validate(bad, ValidationOptions.FAILFAST);
        assertFalse(result.isValid());
        assertEquals("false", result.getLastMessage().getKeyword());
        assertEquals("/y", result.getLastMessage().getInstancePath());
        assertEquals("/additionalProperties", result.getLastMessage().getKeywordPath());
        assertEquals("Schema 'false' always fails",
                result.getLastMessage().getMessage());

        ValidationException ex = assertThrows(ValidationException.class, () -> plan.requireValid(bad));
        assertTrue(ex.getMessage().contains("/additionalProperties"));
        assertTrue(ex.getMessage().contains("instance '/y'"));
        assertTrue(ex.getMessage().contains("Schema 'false' always fails"));
    }

    @Test
    public void testNestedKeywordPathForTypeError() {
        String json =
                "{\n" +
                "  \"type\": \"object\",\n" +
                "  \"properties\": {\n" +
                "    \"name\": { \"type\": \"string\" }\n" +
                "  }\n" +
                "}\n";
        JsonSchema schema = JsonSchema.fromJson(json);
        SchemaPlan plan = schema.createPlan();

        Map<String, Object> bad = new HashMap<String, Object>();
        bad.put("name", 1);

        ValidationResult result = plan.validate(bad, ValidationOptions.FAILFAST);
        assertFalse(result.isValid());
        assertEquals("type", result.getLastMessage().getKeyword());
        assertEquals("/name", result.getLastMessage().getInstancePath());
        assertEquals("/properties/name/type", result.getLastMessage().getKeywordPath());
    }

    @Test
    public void testPropertyNamesErrorUsesMemberInstancePath() {
        String json =
                "{\n" +
                "  \"type\": \"object\",\n" +
                "  \"propertyNames\": { \"pattern\": \"^[a-z]+$\" }\n" +
                "}\n";
        JsonSchema schema = JsonSchema.fromJson(json);
        SchemaPlan plan = schema.createPlan();

        Map<String, Object> bad = new HashMap<String, Object>();
        bad.put("Bad-Name", 1);

        ValidationResult result = plan.validate(bad, ValidationOptions.FAILFAST);
        assertFalse(result.isValid());
        assertEquals("propertyNames", result.getLastMessage().getKeyword());
        assertEquals("/Bad-Name", result.getLastMessage().getInstancePath());
        assertEquals("/propertyNames", result.getLastMessage().getKeywordPath());
    }

    @Test
    public void testDependentRequired() {
        String json =
                "{\n" +
                "  \"type\": \"object\",\n" +
                "  \"dependentRequired\": {\n" +
                "    \"a\": [\"b\"]\n" +
                "  }\n" +
                "}\n";
        JsonSchema schema = JsonSchema.fromJson(json);
        SchemaPlan plan = schema.createPlan();

        Map<String, Object> ok = new HashMap<>();
        ok.put("a", 1);
        ok.put("b", 2);

        Map<String, Object> bad = new HashMap<>();
        bad.put("a", 1);

        assertTrue(plan.isValid(ok));
        assertFalse(plan.isValid(bad));
    }

    @Test
    public void testLogicKeywords() {
        String json =
                "{\n" +
                "  \"oneOf\": [\n" +
                "    { \"type\": \"string\" },\n" +
                "    { \"type\": \"number\" }\n" +
                "  ]\n" +
                "}\n";
        JsonSchema schema = JsonSchema.fromJson(json);
        SchemaPlan plan = schema.createPlan();

        assertTrue(plan.isValid("a"));
        assertTrue(plan.isValid(1));
        assertFalse(plan.isValid(true));
    }

    @Test
    public void testIfThenElse() {
        String json =
                "{\n" +
                "  \"if\": { \"properties\": { \"a\": { \"const\": 1 } } },\n" +
                "  \"then\": { \"required\": [\"b\"] },\n" +
                "  \"else\": { \"required\": [\"c\"] }\n" +
                "}\n";
        JsonSchema schema = JsonSchema.fromJson(json);
        SchemaPlan plan = schema.createPlan();

        Map<String, Object> ok1 = new HashMap<>();
        ok1.put("a", 1);
        ok1.put("b", 2);

        Map<String, Object> ok2 = new HashMap<>();
        ok2.put("a", 2);
        ok2.put("c", 3);

        Map<String, Object> ok3 = new HashMap<>();
        ok3.put("a", 2);
        ok3.put("d", 4);

        assertTrue(plan.isValid(ok1));
        assertTrue(plan.isValid(ok2));
        assertFalse(plan.isValid(ok3));
    }

    @Test
    public void testDependentSchemas1() {
        String json =
                "{\n" +
                        "  \"type\": \"object\",\n" +
                        "  \"properties\": {\n" +
                        "    \"a\": { \"type\": \"number\" },\n" +
                        "    \"b\": { \"type\": \"string\" }\n" +
                        "  },\n" +
                        "  \"dependentSchemas\": {\n" +
                        "    \"a\": {\n" +
                        "      \"required\": [\"b\"]\n" +
                        "    }\n" +
                        "  }\n" +
                        "}";

        JsonSchema schema = JsonSchema.fromJson(json);
        SchemaPlan plan = schema.createPlan();

        assertTrue(plan.isValid(Sjf4j.global().fromJson("{\"a\":1,\"b\":\"x\"}")));
        assertFalse(plan.isValid(Sjf4j.global().fromJson("{\"a\":1}")));
        assertTrue(plan.isValid(Sjf4j.global().fromJson("{\"b\":\"x\"}")));
    }

    @Test
    public void testPropertyNames1() {
        String json =
                "{\n" +
                        "  \"type\": \"object\",\n" +
                        "  \"propertyNames\": {\n" +
                        "    \"pattern\": \"^[a-z]+$\"\n" +
                        "  }\n" +
                        "}";

        JsonSchema schema = JsonSchema.fromJson(json);
        SchemaPlan plan = schema.createPlan();

        assertTrue(plan.isValid(Sjf4j.global().fromJson("{\"abc\":1}")));
        assertFalse(plan.isValid(Sjf4j.global().fromJson("{\"Abc\":1}")));
    }

    @Test
    public void testContains1() {
        String json =
                "{\n" +
                        "  \"type\": \"array\",\n" +
                        "  \"contains\": { \"type\": \"number\" }\n" +
                        "}";

        JsonSchema schema = JsonSchema.fromJson(json);
        SchemaPlan plan = schema.createPlan();

        assertTrue(plan.isValid(JsonArray.fromJson("[\"a\",1]")));
        assertFalse(plan.isValid(JsonArray.fromJson("[\"a\",\"b\"]")));
    }

    @Test
    public void testContainsMinMax() {
        String json =
                "{\n" +
                        "  \"type\": \"array\",\n" +
                        "  \"contains\": { \"type\": \"number\" },\n" +
                        "  \"minContains\": 2\n" +
                        "}";

        JsonSchema schema = JsonSchema.fromJson(json);
        SchemaPlan plan = schema.createPlan();

        assertTrue(plan.isValid(JsonArray.fromJson("[1,2]")));
        assertFalse(plan.isValid(JsonArray.fromJson("[1,\"a\"]")));
    }

    @Test
    public void testUnevaluatedProperties1() {
        String json =
                "{\n" +
                        "  \"type\": \"object\",\n" +
                        "  \"properties\": {\n" +
                        "    \"a\": { \"type\": \"number\" }\n" +
                        "  },\n" +
                        "  \"unevaluatedProperties\": false\n" +
                        "}";

        JsonSchema schema = JsonSchema.fromJson(json);
        SchemaPlan plan = schema.createPlan();

        assertTrue(plan.isValid(Sjf4j.global().fromJson("{\"a\":1}")));
        assertFalse(plan.isValid(Sjf4j.global().fromJson("{\"a\":1,\"b\":2}")));
    }

    @Test
    public void testUnevaluatedPropertiesAllOf() {
        String json =
                "{\n" +
                        "  \"allOf\": [\n" +
                        "    { \"properties\": { \"a\": { \"type\": \"number\" } } },\n" +
                        "    { \"properties\": { \"b\": { \"type\": \"string\" } } }\n" +
                        "  ],\n" +
                        "  \"unevaluatedProperties\": false\n" +
                        "}";

        JsonSchema schema = JsonSchema.fromJson(json);
        SchemaPlan plan = schema.createPlan();

        assertTrue(plan.isValid(Sjf4j.global().fromJson("{\"a\":1,\"b\":\"x\"}")));
        assertFalse(plan.isValid(Sjf4j.global().fromJson("{\"a\":1,\"b\":\"x\",\"c\":3}")));
    }

    @Test
    public void testUnevaluatedItems1() {
        String json =
                "{\n" +
                "  \"type\": \"array\",\n" +
                "  \"prefixItems\": [\n" +
                "    { \"type\": \"number\" }\n" +
                "  ],\n" +
                "  \"unevaluatedItems\": false\n" +
                "}";
        JsonSchema schema = JsonSchema.fromJson(json);
        SchemaPlan plan = schema.createPlan();

        assertTrue(plan.isValid(JsonArray.fromJson("[1]")));
        assertFalse(plan.isValid(JsonArray.fromJson("[1,2]")));
    }

    @Test
    public void testFormatEmail() {
        String json =
                "{\n" +
                "  \"type\": \"string\",\n" +
                "  \"format\": \"email\"\n" +
                "}";

        JsonSchema schema = JsonSchema.fromJson(json);
        SchemaPlan plan = schema.createPlan();

        assertTrue(plan.isValid("a@b.com"));
        assertTrue(plan.isValid("not-email"));

        ValidationResult result = plan.validate("not-email", new ValidationOptions.Builder().strictFormats(true).build());
        assertFalse(result.isValid());
    }

    @Test
    public void testUnknownKeywordIgnored() {
        String json =
                "{\n" +
                "  \"type\": \"string\",\n" +
                "  \"x-internal\": {\n" +
                "    \"$id\": \"ignored\",\n" +
                "    \"type\": 123\n" +
                "  }\n" +
                "}\n";
        JsonSchema schema = JsonSchema.fromJson(json);

        SchemaPlan plan = assertDoesNotThrow(() -> schema.createPlan());
        assertTrue(plan.isValid("ok"));
        assertFalse(plan.isValid(1));
    }

    @Test
    public void testUnknownFormatIgnoredInStrictMode() {
        JsonSchema schema = JsonSchema.fromJson("{\"type\":\"string\",\"format\":\"unknown\"}");
        SchemaPlan plan = assertDoesNotThrow(() -> schema.createPlan());

        ValidationOptions options = new ValidationOptions.Builder().strictFormats(true).build();
        assertTrue(plan.validate("still-valid", options).isValid());
    }

    @Test
    public void testStrictFormatValidators() {
        ValidationOptions options = new ValidationOptions.Builder().strictFormats(true).build();

        JsonSchema hostname = JsonSchema.fromJson("{\"type\":\"string\",\"format\":\"hostname\"}");
        SchemaPlan hostnamePlan = hostname.createPlan();
        assertTrue(hostnamePlan.validate("hostname", options).isValid());

        JsonSchema ipv6 = JsonSchema.fromJson("{\"type\":\"string\",\"format\":\"ipv6\"}");
        SchemaPlan ipv6Plan = ipv6.createPlan();
        assertTrue(ipv6Plan.validate("::1", options).isValid());

        JsonSchema relativeJsonPointer = JsonSchema.fromJson("{\"type\":\"string\",\"format\":\"relative-json-pointer\"}");
        SchemaPlan relativeJsonPointerPlan = relativeJsonPointer.createPlan();
        assertTrue(relativeJsonPointerPlan.validate("0#", options).isValid());

        JsonSchema uriTemplate = JsonSchema.fromJson("{\"type\":\"string\",\"format\":\"uri-template\"}");
        SchemaPlan uriTemplatePlan = uriTemplate.createPlan();
        assertTrue(uriTemplatePlan.validate("http://example.com/dictionary", options).isValid());
    }

    @Test
    public void testNullSubschemaRejected() {
        JsonSchema schema = JsonSchema.fromJson("{\"items\":null}");
        assertThrows(SchemaException.class, () -> schema.createPlan());
    }

    @Test
    public void testContainsUnevaluatedItems() {
        String json =
                "{\n" +
                "  \"type\": \"array\",\n" +
                "  \"contains\": { \"type\": \"number\" },\n" +
                "  \"unevaluatedItems\": false\n" +
                "}";
        JsonSchema schema = JsonSchema.fromJson(json);
        SchemaPlan plan = schema.createPlan();

        plan.requireValid(JsonArray.fromJson("[1]"));
        assertTrue(plan.isValid(JsonArray.fromJson("[1]")));
        assertFalse(plan.isValid(JsonArray.fromJson("[1,\"x\"]")));
    }


}
