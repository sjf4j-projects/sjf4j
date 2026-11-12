package org.sjf4j.testbench.schema.official;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sjf4j.schema.SchemaDialect;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Local fixtures cover the annotation suite's external resources and accounting.
 * The upstream annotation tests currently contain no externalSchemas groups.
 */
final class OfficialAnnotationSuiteCoverageTest {
    @TempDir
    Path directory;

    @Test
    void isolatesSameRetrievalUriAcrossGroupsAndAccountsForEachDialect() throws Exception {
        Path file = directory.resolve("external-resource-isolation.json");
        Files.writeString(file, """
                {
                  "description": "annotation resource isolation",
                  "suite": [
                    {
                      "description": "first group",
                      "compatibility": "7",
                      "schema": {"$ref": "https://example.test/shared.json"},
                      "externalSchemas": {
                        "https://example.test/shared.json": {"title": "First"}
                      },
                      "tests": [
                        {
                          "instance": 42,
                          "assertions": [
                            {"location": "", "keyword": "title", "expected": {"#": "First"}},
                            {"location": "", "keyword": "description", "expected": {}}
                          ]
                        },
                        {
                          "instance": null,
                          "assertions": [
                            {"location": "", "keyword": "title", "expected": {"#": "First"}}
                          ]
                        }
                      ]
                    },
                    {
                      "description": "second group, same URI but different schema",
                      "schema": {"$ref": "https://example.test/shared.json"},
                      "externalSchemas": {
                        "https://example.test/shared.json": {"title": "Second"}
                      },
                      "tests": [
                        {
                          "instance": "abc",
                          "assertions": [
                            {"location": "", "keyword": "title", "expected": {"#": "Second"}}
                          ]
                        }
                      ]
                    },
                    {
                      "description": "future dialect",
                      "compatibility": "9999",
                      "schema": {"title": "Future"},
                      "tests": [
                        {"instance": 1, "assertions": [
                          {"location": "", "keyword": "title", "expected": {"#": "Future"}}
                        ]}
                      ]
                    },
                    {
                      "description": "2020-12 only",
                      "compatibility": "=2020",
                      "schema": {"title": "2020"},
                      "tests": [
                        {"instance": true, "assertions": [
                          {"location": "", "keyword": "title", "expected": {"#": "2020"}}
                        ]}
                      ]
                    }
                  ]
                }
                """);

        OfficialAnnotationTest.Report d7 = OfficialAnnotationTest.runFile(
                file, SchemaDialect.DRAFT_07, null, null);
        assertEquals(4, d7.sourceGroups);
        assertEquals(2, d7.eligibleGroups);
        assertEquals(2, d7.skippedGroups);
        assertEquals(5, d7.sourceTests);
        assertEquals(3, d7.total);
        assertEquals(3, d7.passed);
        assertEquals(2, d7.skipped);
        assertEquals(0, d7.failed + d7.errors);

        OfficialAnnotationTest.Report d20 = OfficialAnnotationTest.runFile(
                file, SchemaDialect.DRAFT_2020_12, null, null);
        assertEquals(4, d20.sourceGroups);
        assertEquals(3, d20.eligibleGroups);
        assertEquals(1, d20.skippedGroups);
        assertEquals(5, d20.sourceTests);
        assertEquals(4, d20.total);
        assertEquals(4, d20.passed);
        assertEquals(1, d20.skipped);
        assertEquals(0, d20.failed + d20.errors);

        // Filters must keep the original, unfiltered upstream group index.
        OfficialAnnotationTest.Report selected = OfficialAnnotationTest.runFile(
                file, SchemaDialect.DRAFT_07, 1, 0);
        assertEquals(1, selected.sourceGroups);
        assertEquals(1, selected.eligibleGroups);
        assertEquals(1, selected.sourceTests);
        assertEquals(1, selected.total);
        assertEquals(1, selected.passed);
        assertEquals(0, selected.skipped);
    }

    @Test
    void rejectsEmptySourceGroupsInsteadOfReportingFullCoverage() throws Exception {
        Path file = directory.resolve("empty.json");
        Files.writeString(file, """
                {
                  "description": "no assertions",
                  "suite": [{
                    "description": "empty group",
                    "schema": true,
                    "tests": []
                  }]
                }
                """);
        assertThrows(IllegalArgumentException.class, () ->
                OfficialAnnotationTest.runFile(file, SchemaDialect.DRAFT_2020_12, null, null));
    }

    @Test
    void detectsIncompleteResultAccounting() {
        OfficialAnnotationTest.Report report = new OfficialAnnotationTest.Report();
        report.sourceGroups = 1;
        report.eligibleGroups = 1;
        report.sourceTests = 1;
        report.total = 1;
        assertThrows(AssertionError.class, () -> report.requireComplete("incomplete"));
    }
}
