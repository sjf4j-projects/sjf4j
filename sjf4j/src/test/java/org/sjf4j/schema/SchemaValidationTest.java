package org.sjf4j.schema;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.Sjf4j;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
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
        schema.compileOrThrow();

        assertTrue(schema.isValid("a"));
        assertFalse(schema.isValid("b"));
        assertFalse(schema.isValid(1));
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
        schema.compileOrThrow();

        assertTrue(schema.isValid(2));
        assertTrue(schema.isValid(8));
        assertFalse(schema.isValid(10));
        assertFalse(schema.isValid(3));
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
        schema.compileOrThrow();

        assertTrue(schema.isValid("ab"));
        assertTrue(schema.isValid("abcd"));
        assertFalse(schema.isValid("a"));
        assertFalse(schema.isValid("abcde"));
        assertFalse(schema.isValid("AB"));
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
        schema.compileOrThrow();

        assertTrue(schema.isValid(Arrays.asList(1, 2)));
        assertFalse(schema.isValid(Collections.emptyList()));
        assertFalse(schema.isValid(Arrays.asList(1, 1)));
        assertFalse(schema.isValid(Arrays.asList(1, "a")));
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
        schema.compileOrThrow();

        assertTrue(schema.isValid(Arrays.asList("a", 1)));
        assertFalse(schema.isValid(Arrays.asList("a", 1, 2)));
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
        schema.compileOrThrow();

        Map<String, Object> ok = new HashMap<String, Object>();
        ok.put("a", 1);
        ok.put("b", "x");

        Map<String, Object> bad = new HashMap<String, Object>();
        bad.put("b", "x");

        assertTrue(schema.isValid(ok));
        assertFalse(schema.isValid(bad));
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
        schema.compileOrThrow();

        Map<String, Object> ok = new HashMap<String, Object>();
        ok.put("x-a", 1);

        Map<String, Object> bad = new HashMap<String, Object>();
        bad.put("y", 1);

        assertTrue(schema.isValid(ok));
        assertFalse(schema.isValid(bad));
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
        schema.compileOrThrow();

        Map<String, Object> ok = new HashMap<>();
        ok.put("a", 1);
        ok.put("b", 2);

        Map<String, Object> bad = new HashMap<>();
        bad.put("a", 1);

        assertTrue(schema.isValid(ok));
        assertFalse(schema.isValid(bad));
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
        schema.compileOrThrow();

        assertTrue(schema.isValid("a"));
        assertTrue(schema.isValid(1));
        assertFalse(schema.isValid(true));
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
        schema.compileOrThrow();

        Map<String, Object> ok1 = new HashMap<>();
        ok1.put("a", 1);
        ok1.put("b", 2);

        Map<String, Object> ok2 = new HashMap<>();
        ok2.put("a", 2);
        ok2.put("c", 3);

        Map<String, Object> ok3 = new HashMap<>();
        ok2.put("a", 2);
        ok2.put("d", 4);

        assertTrue(schema.isValid(ok1));
        assertTrue(schema.isValid(ok2));
        assertFalse(schema.isValid(ok3));
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
        schema.compileOrThrow();

        assertTrue(schema.isValid(Sjf4j.fromJson("{\"a\":1,\"b\":\"x\"}")));
        assertFalse(schema.isValid(Sjf4j.fromJson("{\"a\":1}")));
        assertTrue(schema.isValid(Sjf4j.fromJson("{\"b\":\"x\"}")));
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
        schema.compileOrThrow();

        assertTrue(schema.isValid(Sjf4j.fromJson("{\"abc\":1}")));
        assertFalse(schema.isValid(Sjf4j.fromJson("{\"Abc\":1}")));
    }

    @Test
    public void testContains1() {
        String json =
                "{\n" +
                        "  \"type\": \"array\",\n" +
                        "  \"contains\": { \"type\": \"number\" }\n" +
                        "}";

        JsonSchema schema = JsonSchema.fromJson(json);
        schema.compileOrThrow();

        assertTrue(schema.isValid(JsonArray.fromJson("[\"a\",1]")));
        assertFalse(schema.isValid(JsonArray.fromJson("[\"a\",\"b\"]")));
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
        schema.compileOrThrow();

        assertTrue(schema.isValid(JsonArray.fromJson("[1,2]")));
        assertFalse(schema.isValid(JsonArray.fromJson("[1,\"a\"]")));
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
        schema.compileOrThrow();

        assertTrue(schema.isValid(Sjf4j.fromJson("{\"a\":1}")));
        assertFalse(schema.isValid(Sjf4j.fromJson("{\"a\":1,\"b\":2}")));
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
        schema.compileOrThrow();

        assertTrue(schema.isValid(Sjf4j.fromJson("{\"a\":1,\"b\":\"x\"}")));
        assertFalse(schema.isValid(Sjf4j.fromJson("{\"a\":1,\"b\":\"x\",\"c\":3}")));
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
        schema.compileOrThrow();

        assertTrue(schema.isValid(JsonArray.fromJson("[1]")));
        assertFalse(schema.isValid(JsonArray.fromJson("[1,2]")));
    }

    @Test
    public void testFormatEmail() {
        String json =
                "{\n" +
                        "  \"type\": \"string\",\n" +
                        "  \"format\": \"email\"\n" +
                        "}";

        JsonSchema schema = JsonSchema.fromJson(json);
        schema.compileOrThrow();

        assertTrue(schema.isValid("a@b.com"));
        assertFalse(schema.isValid("not-email"));
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
        schema.compileOrThrow();

        assertTrue(schema.isValid(JsonArray.fromJson("[1]")));
        assertFalse(schema.isValid(JsonArray.fromJson("[1,\"x\"]")));
    }


}
