package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.Sjf4j;

import java.net.URI;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
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
    public void testValidateSuccessReusesSharedResult() {
        JsonSchema schema = JsonSchema.fromJson("{\"type\":\"string\"}");
        SchemaPlan plan = schema.createPlan();

        ValidationResult result = plan.validate("ok");
        assertSame(ValidationResult.SUCCESS, result);
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
    public void testDraft2019TupleItemsCompatibility() {
        JsonSchema schema = JsonSchema.fromJson("{" +
                "\"$schema\":\"https://json-schema.org/draft/2019-09/schema\"," +
                "\"type\":\"array\"," +
                "\"items\":[{" +
                "\"type\":\"string\"},{\"type\":\"number\"}]," +
                "\"additionalItems\":false" +
                "}");
        SchemaPlan plan = schema.createPlan();

        assertTrue(plan.isValid(Arrays.asList("a", 1)));
        assertFalse(plan.isValid(Arrays.asList("a", 1, 2)));
        assertFalse(plan.isValid(Arrays.asList(1, 2)));
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
        assertEquals("/y", result.getLastMessage().getInstancePs().rootedPointerExpr());
        assertEquals("/additionalProperties", result.getLastMessage().getKeywordPs().rootedPointerExpr());
        assertEquals("<inline>", result.getLastMessage().getSchemaUriText());
        assertEquals("schema 'false' always fails",
                result.getLastMessage().getMessage());

        ValidationException ex = assertThrows(ValidationException.class, () -> plan.requireValid(bad));
        assertTrue(ex.getMessage().contains("/additionalProperties"));
        assertTrue(ex.getMessage().contains("instance=/y"));
        assertTrue(ex.getMessage().contains("schema=<inline>"));
        assertTrue(ex.getMessage().contains("schema 'false' always fails"));
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
        assertEquals("/name", result.getLastMessage().getInstancePs().rootedPointerExpr());
        assertEquals("/properties/name/type", result.getLastMessage().getKeywordPs().rootedPointerExpr());
    }

    @Test
    public void testNestedKeywordPathForNullLeafTypeError() {
        String json =
                "{\n" +
                "  \"type\": \"object\",\n" +
                "  \"properties\": {\n" +
                "    \"name\": { \"type\": \"string\" }\n" +
                "  }\n" +
                "}\n";
        JsonSchema schema = JsonSchema.fromJson(json);
        SchemaPlan plan = schema.createPlan();

        Map<String, Object> bad = new HashMap<>();
        bad.put("name", null);

        ValidationResult result = plan.validate(bad, ValidationOptions.FAILFAST);
        assertFalse(result.isValid());
        assertEquals("type", result.getLastMessage().getKeyword());
        assertEquals("/name", result.getLastMessage().getInstancePs().rootedPointerExpr());
        assertEquals("/properties/name/type", result.getLastMessage().getKeywordPs().rootedPointerExpr());
    }

    @Test
    public void testArrayItemTypeErrorUsesIndexedInstancePathForNullLeaf() {
        JsonSchema schema = JsonSchema.fromJson("{\"type\":\"array\",\"items\":{\"type\":\"string\"}}");
        SchemaPlan plan = schema.createPlan();

        ValidationResult result = plan.validate(Arrays.asList("ok", null), ValidationOptions.FAILFAST);
        assertFalse(result.isValid());
        assertEquals("type", result.getLastMessage().getKeyword());
        assertEquals("/1", result.getLastMessage().getInstancePs().rootedPointerExpr());
        assertEquals("/items/type", result.getLastMessage().getKeywordPs().rootedPointerExpr());
    }

    @Test
    public void testReferencedPatternErrorPathForConvertedLeaf() {
        String json =
                "{\n" +
                "  \"type\": \"object\",\n" +
                "  \"properties\": {\n" +
                "    \"site\": { \"$ref\": \"#/$defs/httpsUri\" }\n" +
                "  },\n" +
                "  \"$defs\": {\n" +
                "    \"httpsUri\": { \"type\": \"string\", \"pattern\": \"^https://\" }\n" +
                "  }\n" +
                "}\n";
        JsonSchema schema = JsonSchema.fromJson(json);
        SchemaPlan plan = schema.createPlan();

        Map<String, Object> bad = new HashMap<>();
        bad.put("site", URI.create("ftp://example.com"));

        ValidationResult result = plan.validate(bad, ValidationOptions.FAILFAST);
        assertFalse(result.isValid());
        assertEquals("pattern", result.getLastMessage().getKeyword());
        assertEquals("/site", result.getLastMessage().getInstancePs().rootedPointerExpr());
        assertEquals("/$defs/httpsUri/pattern", result.getLastMessage().getKeywordPs().rootedPointerExpr());
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
        assertEquals("/Bad-Name", result.getLastMessage().getInstancePs().rootedPointerExpr());
        assertEquals("/propertyNames", result.getLastMessage().getKeywordPs().rootedPointerExpr());
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
    public void testDraft2019DependenciesCompatibility() {
        JsonSchema schema = JsonSchema.fromJson("{" +
                "\"$schema\":\"https://json-schema.org/draft/2019-09/schema\"," +
                "\"type\":\"object\"," +
                "\"properties\":{" +
                "\"credit_card\":{\"type\":\"number\"}," +
                "\"billing_address\":{\"type\":\"string\"}," +
                "\"name\":{\"type\":\"string\"}" +
                "}," +
                "\"dependencies\":{" +
                "\"credit_card\":[\"billing_address\"]," +
                "\"name\":{\"required\":[\"billing_address\"]}" +
                "}" +
                "}");
        SchemaPlan plan = schema.createPlan();

        assertTrue(plan.isValid(Sjf4j.global().fromJson("{\"credit_card\":1,\"billing_address\":\"x\"}")));
        assertFalse(plan.isValid(Sjf4j.global().fromJson("{\"credit_card\":1}")));
        assertTrue(plan.isValid(Sjf4j.global().fromJson("{\"name\":\"x\",\"billing_address\":\"y\"}")));
        assertFalse(plan.isValid(Sjf4j.global().fromJson("{\"name\":\"x\"}")));
    }

    @Test
    public void testDraft2020DependenciesCompatibility() {
        JsonSchema schema = JsonSchema.fromJson("{" +
                "\"$schema\":\"https://json-schema.org/draft/2020-12/schema\"," +
                "\"type\":\"object\"," +
                "\"dependencies\":{\"a\":[\"b\"]}" +
                "}");
        SchemaPlan plan = schema.createPlan();

        assertTrue(plan.isValid(Sjf4j.global().fromJson("{\"a\":1,\"b\":2}")));
        assertFalse(plan.isValid(Sjf4j.global().fromJson("{\"a\":1}")));
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
    public void testFormatAssertionVocabularyForcesValidation() {
        ObjectSchema metaSchema = SchemaUtil.loadSchemaFromLocalUri(
                URI.create("classpath:///json-schemas/remotes/draft2020-12/format-assertion-true.json"));
        SchemaRegistry registry = new SchemaRegistry().index(metaSchema);

        JsonSchema schema = JsonSchema.fromJson("{\"$schema\":\"http://localhost:1234/draft2020-12/format-assertion-true.json\",\"type\":\"string\",\"format\":\"email\"}");
        SchemaPlan plan = schema.createPlan(registry);

        assertTrue(plan.isValid("a@b.com"));
        assertFalse(plan.isValid("not-email"));
    }

    @Test
    public void testValidationKeywordRemainsActiveWhenVocabularyIsOptional() {
        ObjectSchema metaSchema = (ObjectSchema) JsonSchema.fromJson("{" +
                "\"$id\":\"https://example.com/meta/no-validation\"," +
                "\"$vocabulary\":{" +
                "\"https://json-schema.org/draft/2020-12/vocab/core\":true," +
                "\"https://json-schema.org/draft/2020-12/vocab/validation\":false" +
                "}" +
                "}");
        SchemaRegistry registry = new SchemaRegistry().index(metaSchema);

        JsonSchema schema = JsonSchema.fromJson("{" +
                "\"$schema\":\"https://example.com/meta/no-validation\"," +
                "\"type\":\"string\"" +
                "}");

        SchemaPlan plan = schema.createPlan(registry);

        assertTrue(plan.isValid("ok"));
        assertFalse(plan.isValid(1));
    }

    @Test
    public void testMissingValidationVocabularyLeavesValidationKeywordsInactive() {
        ObjectSchema metaSchema = (ObjectSchema) JsonSchema.fromJson("{" +
                "\"$id\":\"https://example.com/meta/core-only\"," +
                "\"$vocabulary\":{" +
                "\"https://json-schema.org/draft/2020-12/vocab/core\":true" +
                "}" +
                "}");
        SchemaRegistry registry = new SchemaRegistry().index(metaSchema);

        JsonSchema schema = JsonSchema.fromJson("{" +
                "\"$schema\":\"https://example.com/meta/core-only\"," +
                "\"type\":\"string\"" +
                "}");
        SchemaPlan plan = schema.createPlan(registry);

        assertTrue(plan.isValid("ok"));
        assertTrue(plan.isValid(1));
    }

    @Test
    public void testOptionalFormatAssertionVocabularyStillForcesAssertionWhenRecognized() {
        ObjectSchema metaSchema = (ObjectSchema) JsonSchema.fromJson("{" +
                "\"$id\":\"https://example.com/meta/format-optional\"," +
                "\"$vocabulary\":{" +
                "\"https://json-schema.org/draft/2020-12/vocab/core\":true," +
                "\"https://json-schema.org/draft/2020-12/vocab/format-assertion\":false" +
                "}" +
                "}");
        SchemaRegistry registry = new SchemaRegistry().index(metaSchema);

        JsonSchema schema = JsonSchema.fromJson("{" +
                "\"$schema\":\"https://example.com/meta/format-optional\"," +
                "\"type\":\"string\"," +
                "\"format\":\"email\"" +
                "}");
        SchemaPlan plan = schema.createPlan(registry);

        assertTrue(plan.isValid("a@b.com"));
        assertFalse(plan.isValid("not-email"));
    }

    @Test
    public void testFormatAnnotationVocabularyAloneDoesNotForceAssertion() {
        ObjectSchema metaSchema = (ObjectSchema) JsonSchema.fromJson("{" +
                "\"$id\":\"https://example.com/meta/format-annotation-only\"," +
                "\"$vocabulary\":{" +
                "\"https://json-schema.org/draft/2020-12/vocab/core\":true," +
                "\"https://json-schema.org/draft/2020-12/vocab/validation\":true," +
                "\"https://json-schema.org/draft/2020-12/vocab/format-annotation\":true" +
                "}" +
                "}");
        SchemaRegistry registry = new SchemaRegistry().index(metaSchema);

        JsonSchema schema = JsonSchema.fromJson("{" +
                "\"$schema\":\"https://example.com/meta/format-annotation-only\"," +
                "\"type\":\"string\"," +
                "\"format\":\"email\"" +
                "}");
        SchemaPlan plan = schema.createPlan(registry);

        assertTrue(plan.isValid("a@b.com"));
        assertTrue(plan.isValid("not-email"));
    }

    @Test
    public void testDraft2019FormatVocabularyActivatesFormatWithoutAssertion() {
        ObjectSchema metaSchema = SchemaUtil.loadSchemaFromLocalUri(
                URI.create("classpath:///json-schemas/draft2019-09/schema.json"));
        SchemaRegistry registry = new SchemaRegistry().index(metaSchema);

        JsonSchema schema = JsonSchema.fromJson("{" +
                "\"$schema\":\"https://json-schema.org/draft/2019-09/schema\"," +
                "\"type\":\"string\"," +
                "\"format\":\"email\"" +
                "}");
        SchemaPlan plan = schema.createPlan(registry);

        assertTrue(plan.isValid("a@b.com"));
        assertTrue(plan.isValid("not-email"));
        assertFalse(plan.validate("not-email", new ValidationOptions.Builder().strictFormats(true).build()).isValid());
    }

    @Test
    public void testDraft2019RecursiveKeywordsCompatibility() {
        JsonSchema schema = JsonSchema.fromJson("{" +
                "\"$schema\":\"https://json-schema.org/draft/2019-09/schema\"," +
                "\"$recursiveAnchor\":true," +
                "\"type\":\"object\"," +
                "\"properties\":{" +
                "\"value\":{\"type\":\"string\"}," +
                "\"child\":{\"$recursiveRef\":\"#\"}" +
                "}" +
                "}");
        SchemaPlan plan = schema.createPlan();

        assertTrue(plan.isValid(Sjf4j.global().fromJson("{\"value\":\"root\",\"child\":{\"value\":\"leaf\"}}")));
        assertFalse(plan.isValid(Sjf4j.global().fromJson("{\"value\":\"root\",\"child\":{\"value\":1}}")));
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
    public void testStrictFormatValidatorsForDraft2020Fixes() {
        ValidationOptions options = new ValidationOptions.Builder().strictFormats(true).build();

        SchemaPlan time = JsonSchema.fromJson("{\"type\":\"string\",\"format\":\"time\"}").createPlan();
        assertTrue(time.validate("23:59:60Z", options).isValid());
        assertTrue(time.validate("01:29:60+01:30", options).isValid());
        assertFalse(time.validate("23:59:60+00:30", options).isValid());
        assertFalse(time.validate("12:00:00", options).isValid());

        SchemaPlan dateTime = JsonSchema.fromJson("{\"type\":\"string\",\"format\":\"date-time\"}").createPlan();
        assertTrue(dateTime.validate("1998-12-31T23:59:60Z", options).isValid());
        assertFalse(dateTime.validate("+11963-06-19T08:30:06.283185Z", options).isValid());
        assertFalse(dateTime.validate("1990-12-31T15:59:59-24:00", options).isValid());

        SchemaPlan duration = JsonSchema.fromJson("{\"type\":\"string\",\"format\":\"duration\"}").createPlan();
        assertTrue(duration.validate("P1Y2M3DT4H5M6S", options).isValid());
        assertFalse(duration.validate("P", options).isValid());
        assertFalse(duration.validate("PT", options).isValid());
        assertFalse(duration.validate("P1YT", options).isValid());
        assertFalse(duration.validate("PT0.5S", options).isValid());
        assertFalse(duration.validate("P1Y2D", options).isValid());
        assertFalse(duration.validate("PT1H2S", options).isValid());

        SchemaPlan email = JsonSchema.fromJson("{\"type\":\"string\",\"format\":\"email\"}").createPlan();
        assertTrue(email.validate("\"joe bloggs\"@example.com", options).isValid());
        assertTrue(email.validate("joe.bloggs@[127.0.0.1]", options).isValid());
        assertTrue(email.validate("joe.bloggs@[IPv6:::1]", options).isValid());
        assertFalse(email.validate("joe bloggs@example.com", options).isValid());

        SchemaPlan idnEmail = JsonSchema.fromJson("{\"type\":\"string\",\"format\":\"idn-email\"}").createPlan();
        assertTrue(idnEmail.validate("실례@실례.테스트", options).isValid());

        SchemaPlan iri = JsonSchema.fromJson("{\"type\":\"string\",\"format\":\"iri\"}").createPlan();
        assertTrue(iri.validate("http://[2001:0db8:85a3:0000:0000:8a2e:0370:7334]", options).isValid());
        assertFalse(iri.validate("http://2001:0db8:85a3:0000:0000:8a2e:0370:7334", options).isValid());

        SchemaPlan iriReference = JsonSchema.fromJson("{\"type\":\"string\",\"format\":\"iri-reference\"}").createPlan();
        assertTrue(iriReference.validate("//[2001:0db8:85a3:0000:0000:8a2e:0370:7334]/path", options).isValid());
        assertFalse(iriReference.validate("//2001:0db8:85a3:0000:0000:8a2e:0370:7334/path", options).isValid());

        SchemaPlan regex = JsonSchema.fromJson("{\"type\":\"string\",\"format\":\"regex\"}").createPlan();
        assertFalse(regex.validate("\\a", options).isValid());

        SchemaPlan hostname = JsonSchema.fromJson("{\"type\":\"string\",\"format\":\"hostname\"}").createPlan();
        assertFalse(hostname.validate("xn--X", options).isValid());
        assertFalse(hostname.validate("xn--al-0ea", options).isValid());
        assertFalse(hostname.validate("xn--S-jib3p", options).isValid());
        assertFalse(hostname.validate("xn--A-2hc5h", options).isValid());
        assertFalse(hostname.validate("xn--ngb6iyr", options).isValid());
        assertFalse(hostname.validate("xn--11b2er09f", options).isValid());
        assertFalse(hostname.validate("XN--aa---o47jg78q", options).isValid());
        assertTrue(hostname.validate("xn--11b2ezcw70k", options).isValid());

        SchemaPlan idnHostname = JsonSchema.fromJson("{\"type\":\"string\",\"format\":\"idn-hostname\"}").createPlan();
        assertTrue(idnHostname.validate("a\u3002b", options).isValid());
        assertFalse(idnHostname.validate("\u3002", options).isValid());
        assertFalse(idnHostname.validate("example\u3002", options).isValid());
        assertFalse(idnHostname.validate("\u0903hello", options).isValid());
        assertFalse(idnHostname.validate("a\u00b7l", options).isValid());
        assertFalse(idnHostname.validate("\u03b1\u0375", options).isValid());
        assertFalse(idnHostname.validate("\u05f3\u05d1", options).isValid());
        assertFalse(idnHostname.validate("def\u30fbabc", options).isValid());
        assertFalse(idnHostname.validate("\u0628\u0660\u06f0", options).isValid());
        assertFalse(idnHostname.validate("\u0915\u200d\u0937", options).isValid());
        assertTrue(idnHostname.validate("\u0628\u064a\u200c\u0628\u064a", options).isValid());
    }

    @Test
    public void testIcu4jAvailableOnSchemaTestRuntime() {
        assertTrue(FormatUtil.isIcu4jAvailable());
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
