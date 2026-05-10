package org.sjf4j.schema;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.sjf4j.Sjf4j;
import org.sjf4j.exception.SchemaException;

import java.net.URI;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Slf4j
public class SchemaCoreTest {

    @Test
    public void testCompile1() {
        String json = "{  \"type\": \"string\" }";
        JsonSchema schema = JsonSchema.fromJson(json);
        SchemaPlan plan = schema.createPlan();
        log.info("schema={}", schema);
//        assertEquals("", schema.getUri().toString());
        assertTrue(plan.isValid("abc"));
    }

    @Test
    public void testAny1() {
        String json = "{}";
        JsonSchema schema = JsonSchema.fromJson(json);
        log.info("schema={}", schema);
        SchemaPlan plan = schema.createPlan();
        assertTrue(plan.isValid(1));
        assertTrue(plan.isValid("a"));
        assertTrue(plan.isValid(null));
        assertTrue(plan.isValid(LocalDate.now()));
        assertTrue(plan.isValid(false));
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
        SchemaPlan plan = schema.createPlan();
        assertEquals("json-schema/base.json", schema.getCanonicalUri().toString());
        assertTrue(plan.isValid(13));
    }

    @Test
    public void testRetrievalAndCanonicalUri() {
        ObjectSchema schema = (ObjectSchema) JsonSchema.fromJson("{\"$id\":\"defs/user.json\",\"type\":\"string\"}");
        schema.setRetrievalUri(URI.create("file:///schemas/root.json"));

        assertEquals("file:///schemas/root.json", schema.getRetrievalUri().toString());
        assertEquals("defs/user.json", schema.getCanonicalUri().toString());

        schema.createPlan();

        assertEquals("file:///schemas/root.json", schema.getRetrievalUri().toString());
        assertEquals("defs/user.json", schema.getCanonicalUri().toString());
    }

    @Test
    public void testCompiledSchemaBecomesReadOnly() {
        ObjectSchema schema = (ObjectSchema) JsonSchema.fromJson("{\"type\":\"string\"}");
        schema.createPlan();

        assertDoesNotThrow(() -> schema.put("title", "name"));
        assertDoesNotThrow(() -> schema.remove("type"));
        assertDoesNotThrow(schema::clear);
        assertDoesNotThrow(schema::prune);
        assertDoesNotThrow(() -> schema._dynamicMap(new java.util.LinkedHashMap<>()));
        assertDoesNotThrow(() -> schema._dynamicMap().put("x", 1));
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
    public void testWithoutFragment() {
        assertEquals("https://example.com/a/b?x=1",
                withoutFragment(URI.create("https://example.com/a/b?x=1#frag")).toString());
        assertEquals("classpath:/schemas/user.json",
                withoutFragment(URI.create("classpath:/schemas/user.json#name")).toString());
        assertEquals("urn:uuid:deadbeef",
                withoutFragment(URI.create("urn:uuid:deadbeef#node")).toString());
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
        SchemaPlan plan = schema.createPlan();

        ValidationResult result = plan.validate(1);
        log.info("result={}", result);
        assertTrue(plan.isValid(1));
        assertFalse(plan.isValid("a"));
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
        assertThrows(SchemaException.class, () -> schema.createPlan());
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
        SchemaPlan plan = schema.createPlan();
        assertThrows(SchemaException.class, () -> plan.isValid("StackOverflowError"));
    }

    @Test
    public void testAnchor2() {
        String json = "{\n" +
                "  \"$ref\": \"#missing\"\n" +
                "}\n";
        JsonSchema schema = JsonSchema.fromJson(json);
        log.info("schema={}", schema);
        assertThrows(SchemaException.class, () -> schema.createPlan());
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
        SchemaPlan plan = schema.createPlan();

        ValidationResult result = plan.validate(Sjf4j.global().fromJson("{\n" +
                "  \"value\": 1,\n" +
                "  \"children\": [\n" +
                "    { \"value\": 2 }\n" +
                "  ]\n" +
                "}\n"));
        log.info("result={}", result);
        assertTrue(result.isValid());

        ValidationResult result2 = plan.validate(Sjf4j.global().fromJson("{\n" +
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
        assertThrows(SchemaException.class, () -> schema.createPlan().validate("Not Found dynamicRef"));
    }

    @Test
    public void testDynamic3() {
        String json = "{ \"$dynamicRef\": \"a.json#node\" }\n";
        JsonSchema schema = JsonSchema.fromJson(json);
        log.info("schema={}", schema);
        assertThrows(SchemaException.class, () -> schema.createPlan());
    }


    @Test
    public void testStore1() {
        String json1 = "{\n" +
                "  \"$id\": \"https://example.org/base.json\",\n" +
                "  \"$dynamicAnchor\": \"node\",\n" +
                "  \"type\": \"number\"\n" +
                "}\n";
        JsonSchema schema1 = JsonSchema.fromJson(json1);
        schema1.createPlan();

        String json2 = "{\n" +
                "  \"$id\": \"https://example.org/child.json\",\n" +
                "  \"$dynamicAnchor\": \"node\",\n" +
                "  \"type\": \"string\"\n" +
                "}\n";
        JsonSchema schema2 = JsonSchema.fromJson(json2);
        schema2.createPlan();

        String json3 = "{\n" +
                "  \"$ref\": \"https://example.org/base.json\",\n" +
                "  \"allOf\": [\n" +
                "    { \"$ref\": \"https://example.org/child.json\" }\n" +
                "  ]\n" +
                "}\n";
        JsonSchema schema3 = JsonSchema.fromJson(json3);

        SchemaRegistry store = new SchemaRegistry();
        store.register(schema1);
        store.register(schema2);
        SchemaPlan plan3 = schema3.createPlan(store);
        log.info("schema3={}", schema3);

        ValidationResult result = plan3.validate("a");
        log.info("result={}", result);
        assertFalse(result.isValid());

        ValidationResult result2 = plan3.validate(1);
        log.info("result2={}", result2);
        assertFalse(result2.isValid());
    }

    @Test
    public void testToJsonSchema1() {
        JsonSchema schema1 = JsonSchema.fromJson("true");
        log.info("schema1={}", schema1);
        assertInstanceOf(BooleanSchema.class, schema1);
        assertTrue(schema1.createPlan().validate(100).isValid());

        JsonSchema schema2 = JsonSchema.fromJson("{\"type\": \"number\"}");
        SchemaPlan plan2 = schema2.createPlan();
        log.info("schema2={}", schema2);
        assertInstanceOf(ObjectSchema.class, schema2);
        assertTrue(plan2.validate(100).isValid());
        assertFalse(plan2.validate("100").isValid());
    }


    private static URI withoutFragment(URI uri) {
        return URI.create(uri.toString().replaceFirst("#.*$", ""));
    }

}
