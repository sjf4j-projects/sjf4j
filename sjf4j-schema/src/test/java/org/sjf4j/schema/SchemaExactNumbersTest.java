package org.sjf4j.schema;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class SchemaExactNumbersTest {

    private static final String DECIMAL = "0.123456789123456789";
    private static final BigDecimal EXACT = new BigDecimal(DECIMAL);
    private static final BigDecimal LOWER = new BigDecimal("0.123456789123456788");
    private static final BigDecimal UPPER = new BigDecimal("0.123456789123456790");

    @Test
    void schemaDocumentsAndLiteralsUseFixedPrecision() {
        ObjectSchema schema = (ObjectSchema) JsonSchema.fromJson("{\"minimum\":" + DECIMAL + "}");
        assertEquals(EXACT, schema.getNode("minimum"));
        assertEquals(EXACT, SchemaSupport.readJsonValue(SchemaSupport.writeJsonValue(EXACT)));
        ObjectSchema exponent = (ObjectSchema) JsonSchema.fromJson("{\"minimum\":1.23e-400}");
        assertEquals(0, new BigDecimal("1.23e-400").compareTo(
                assertInstanceOf(BigDecimal.class, exponent.getNode("minimum"))));
        assertTrue(JsonSchema.fromJson("true").formulate().isValid(EXACT));
    }

    @Test
    void exactBoundsAndMultipleOf() {
        check("minimum", LOWER, false);
        check("minimum", EXACT, true);
        check("maximum", UPPER, false);
        check("maximum", EXACT, true);
        check("exclusiveMinimum", EXACT, false);
        check("exclusiveMinimum", UPPER, true);
        check("exclusiveMaximum", EXACT, false);
        check("exclusiveMaximum", LOWER, true);
        SchemaPlan multiple = JsonSchema.fromJson("{\"multipleOf\":" + DECIMAL + "}").formulate();
        assertTrue(multiple.isValid(EXACT));
        assertTrue(multiple.isValid(EXACT.multiply(BigDecimal.valueOf(3))));
        assertFalse(multiple.isValid(UPPER));
    }

    private static void check(String keyword, BigDecimal value, boolean valid) {
        SchemaPlan plan = JsonSchema.fromJson("{\"" + keyword + "\":" + DECIMAL + "}").formulate();
        assertEquals(valid, plan.isValid(value), keyword);
    }

    @Test
    void compoundLiteralsAndUniqueItemsUseNumericEquality() {
        SchemaPlan constant = JsonSchema.fromJson("{\"const\":{\"n\":[" + DECIMAL + "]}}").formulate();
        SchemaPlan enumeration = JsonSchema.fromJson("{\"enum\":[{\"n\":[" + DECIMAL + "]}]}").formulate();
        for (SchemaPlan plan : Arrays.asList(constant, enumeration)) {
            assertTrue(plan.isValid(java.util.Collections.singletonMap("n", Arrays.asList(EXACT))));
            assertFalse(plan.isValid(java.util.Collections.singletonMap("n", Arrays.asList(LOWER))));
        }
        SchemaPlan unique = JsonSchema.fromJson("{\"uniqueItems\":true}").formulate();
        assertTrue(unique.isValid(Arrays.asList(EXACT, LOWER)));
        assertFalse(unique.isValid(Arrays.asList(EXACT, EXACT.setScale(EXACT.scale() + 1))));
    }

    @Test
    void resourceAndExternalMappingsRetainPrecision() throws Exception {
        URI uri = URI.create("https://example.test/exact");
        String json = "{\"minimum\":" + DECIMAL + "}";
        ObjectSchema loaded = (ObjectSchema) SchemaLoader.parse(new StringReader(json));
        assertEquals(EXACT, loaded.getNode("minimum"));
        JsonSchema external = SchemaLoader.parseExternalSchemas("{\"" + uri + "\":" + json + "}").get(uri);
        assertEquals(EXACT, ((ObjectSchema) external).getNode("minimum"));
        SchemaRegistry registry = new SchemaRegistry();
        registry.index(uri, external);
        SchemaPlan plan = JsonSchema.fromJson("{\"$ref\":\"" + uri + "\"}").formulate(registry);
        assertTrue(plan.isValid(EXACT));
        assertFalse(plan.isValid(LOWER));
    }
}
