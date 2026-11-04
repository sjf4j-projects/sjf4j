package org.sjf4j.testbench.schema.official;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.Nodes;
import org.sjf4j.Sjf4j;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaDialect;
import org.sjf4j.schema.SchemaPlan;
import org.sjf4j.schema.SchemaRegistry;
import org.sjf4j.schema.ValidationOptions;
import org.sjf4j.schema.ValidationResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Official JSON-Schema-Test-Suite annotation conformance.
 *
 * First stage: the metadata vocabulary, independently of standard output formats.
 * Other annotation vocabularies are intentionally not included in this stage.
 */
public final class OfficialAnnotationTest {
    private static final ValidationOptions OPTIONS = ValidationOptions.builder()
            .collectAnnotations(true).build();

    @Test
    void metadataVocabulary() throws Exception {
        String root = System.getProperty("sjf4j.schema.officialTestRoot");
        assertNotNull(root, "official suite root must be supplied by officialAnnotationTest");
        Path path = Paths.get(root, "annotations", "tests", "meta-data.json");
        assertTrue(Files.isRegularFile(path), "official annotation suite missing: " + path);

        JsonObject suite = Sjf4j.global().fromJson(Files.readAllBytes(path), JsonObject.class);
        SchemaRegistry registry = new SchemaRegistry(SchemaDialect.DRAFT_2020_12);
        JsonArray groups = suite.getJsonArray("suite");
        assertEquals(7, groups.size(), "official metadata test group count changed");

        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            JsonObject group = groups.getJsonObject(groupIndex);
            SchemaPlan plan = JsonSchema.fromNode(group.getNode("schema")).createPlan(registry);
            JsonArray tests = group.getJsonArray("tests");
            for (int testIndex = 0; testIndex < tests.size(); testIndex++) {
                JsonObject test = tests.getJsonObject(testIndex);
                ValidationResult result = plan.validate(test.getNode("instance"), OPTIONS);
                String label = group.getString("description") + " #" + testIndex;
                assertTrue(result.isValid(), label + ": validation failed");
                JsonArray assertions = test.getJsonArray("assertions");
                for (int i = 0; i < assertions.size(); i++) {
                    JsonObject assertion = assertions.getJsonObject(i);
                    String instanceLocation = assertion.getString("location");
                    String keyword = assertion.getString("keyword");
                    JsonObject expected = assertion.getJsonObject("expected");

                    Map<String, Object> actual = new LinkedHashMap<>();
                    for (ValidationResult.Message message : result.getAnnotations()) {
                        if (!keyword.equals(message.getKeyword())) continue;
                        if (!instanceLocation.equals(message.getInstancePs().rootedPointerExpr())) continue;
                        String pointer = "#" + message.getKeywordPs().rootedPointerExpr();
                        String suffix = "/" + keyword.replace("~", "~0").replace("/", "~1");
                        assertTrue(pointer.endsWith(suffix), label + ": invalid annotation keyword location");
                        String schemaObjectLocation = pointer.substring(0, pointer.length() - suffix.length());
                        assertFalse(actual.containsKey(schemaObjectLocation),
                                label + ": duplicate annotation at " + schemaObjectLocation);
                        actual.put(schemaObjectLocation, message.getValue());
                    }

                    assertEquals(expected.size(), actual.size(), label + ": annotation locations");
                    for (String expectedLocation : expected.keySet()) {
                        assertTrue(actual.containsKey(expectedLocation), label + ": missing " + expectedLocation);
                        assertTrue(Nodes.equals(expected.getNode(expectedLocation), actual.get(expectedLocation)),
                                label + ": value mismatch at " + expectedLocation);
                    }
                }
            }
        }
    }
}
