package org.sjf4j.schema;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.Sjf4j;
import org.sjf4j.exception.SchemaException;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class SchemaCoreTest {

    @Test
    public void testCompile1() {
        String json = "{  \"type\": \"string\" }";
        JsonSchema schema = JsonSchema.fromJson(json);
        schema.compile();
        log.info("schema={}", schema);
//        assertEquals("", schema.getUri().toString());
        assertTrue(schema.isValid("abc"));
    }

    @Test
    public void testAny1() {
        String json = "{}";
        JsonSchema schema = JsonSchema.fromJson(json);
        log.info("schema={}", schema);
        schema.compile();
        assertTrue(schema.isValid(1));
        assertTrue(schema.isValid("a"));
        assertTrue(schema.isValid(null));
        assertTrue(schema.isValid(LocalDate.now()));
        assertTrue(schema.isValid(false));
    }


    @Test
    public void testId1() {
        String json =
                "{\n" +
                "  \"$id\": \"json-schema/base.json\",\n" +
                "  \"type\": \"number\"\n" +
                "}\n";
        ObjectSchema schema = (ObjectSchema) JsonSchema.fromJson(json);
        log.info("schema={}", schema);
        schema.compile();
        assertEquals("json-schema/base.json", schema.getUri().toString());
        assertTrue(schema.isValid(13));
    }

    @Test
    public void testId2() {
        String json =
                "{\n" +
                "  \"$id\": \"base.json#foo\",\n" +
                "  \"type\": \"string\"\n" +
                "}\n";
        JsonSchema schema = JsonSchema.fromJson(json);
        log.info("schema={}", schema);
    }

    @Test
    public void testRef1() {
        String json =
                "{\n" +
                "  \"$defs\": {\n" +
                "    \"num\": { \"type\": \"number\" }\n" +
                "  },\n" +
                "  \"$ref\": \"#/$defs/num\"\n" +
                "}\n";
        JsonSchema schema = JsonSchema.fromJson(json);
        log.info("schema={}", schema);
        schema.compile();

        ValidationResult result = schema.validate(1);
        log.info("result={}", result);
        assertTrue(schema.isValid(1));
        assertFalse(schema.isValid("a"));
    }

    @Test
    public void testRef2() {
        String json = "{\n" +
                        "  \"$defs\": {\n" +
                        "    \"x\": 123\n" +
                        "  },\n" +
                        "  \"$ref\": \"#/$defs/x\"\n" +
                        "}\n";
        JsonSchema schema = JsonSchema.fromJson(json);
        log.info("schema={}", schema);
        assertThrows(SchemaException.class, schema::compile);
    }


    @Test
    public void testAnchor1() {
        String json = "{\n" +
                        "  \"$anchor\": \"root\",\n" +
                        "  \"type\": \"string\",\n" +
                        "  \"$ref\": \"#root\"\n" +
                        "}\n";
        JsonSchema schema = JsonSchema.fromJson(json);
        log.info("schema={}", schema);
        schema.compile();
        schema.isValid("StackOverflowError");
    }

    @Test
    public void testAnchor2() {
        String json = "{\n" +
                "  \"$ref\": \"#missing\"\n" +
                "}\n";
        JsonSchema schema = JsonSchema.fromJson(json);
        log.info("schema={}", schema);
        schema.compile();
        assertFalse(schema.isValid("Miss"));
    }

    @Test
    public void testDynamic1() {
        String json = "{\n" +
                "  \"$defs\": {\n" +
                "    \"node\": {\n" +
                "      \"$dynamicAnchor\": \"node\",\n" +
                "      \"type\": \"object\",\n" +
                "      \"properties\": {\n" +
                "        \"value\": { \"type\": \"number\" },\n" +
                "        \"children\": {\n" +
                "          \"type\": \"array\",\n" +
                "          \"items\": { \"$dynamicRef\": \"#node\" }\n" +
                "        }\n" +
                "      }\n" +
                "    }\n" +
                "  },\n" +
                "  \"$ref\": \"#/$defs/node\"\n" +
                "}\n";
        JsonSchema schema = JsonSchema.fromJson(json);
        log.info("schema={}", schema);
        schema.compile();

        ValidationResult result = schema.validate(Sjf4j.fromJson("{\n" +
                "  \"value\": 1,\n" +
                "  \"children\": [\n" +
                "    { \"value\": 2 }\n" +
                "  ]\n" +
                "}\n"));
        log.info("result={}", result);
        assertTrue(result.isValid());

        ValidationResult result2 = schema.validate(Sjf4j.fromJson("{\n" +
                "  \"value\": \"x\"\n" +
                "}\n"));
        log.info("result2={}", result2);
        assertFalse(result2.isValid());
    }

    @Test
    public void testDynamic2() {
        String json = "{ \"$dynamicRef\": \"#/a/b\" }\n";
        JsonSchema schema = JsonSchema.fromJson(json);
        log.info("schema={}", schema);
        schema.compile();
    }

    @Test
    public void testDynamic3() {
        String json = "{ \"$dynamicRef\": \"a.json#node\" }\n";
        JsonSchema schema = JsonSchema.fromJson(json);
        log.info("schema={}", schema);
        schema.compile();
    }


    @Test
    public void testStore1() {
        String json1 = "{\n" +
                "  \"$id\": \"https://example.org/base.json\",\n" +
                "  \"$dynamicAnchor\": \"node\",\n" +
                "  \"type\": \"number\"\n" +
                "}\n";
        JsonSchema schema1 = JsonSchema.fromJson(json1);
        schema1.compile();

        String json2 = "{\n" +
                "  \"$id\": \"https://example.org/child.json\",\n" +
                "  \"$dynamicAnchor\": \"node\",\n" +
                "  \"type\": \"string\"\n" +
                "}\n";
        JsonSchema schema2 = JsonSchema.fromJson(json2);
        schema2.compile();

        String json3 = "{\n" +
                "  \"$ref\": \"https://example.org/base.json\",\n" +
                "  \"allOf\": [\n" +
                "    { \"$ref\": \"https://example.org/child.json\" }\n" +
                "  ]\n" +
                "}\n";
        JsonSchema schema3 = JsonSchema.fromJson(json3);

        SchemaStore store = new SchemaStore(schema1, schema2);
        schema3.compile(store);
        log.info("schema3={}", schema3);

        ValidationResult result = schema3.validate("a");
        log.info("result={}", result);
        assertFalse(result.isValid());

        ValidationResult result2 = schema3.validate(1);
        log.info("result2={}", result2);
        assertFalse(result2.isValid());
    }




}
