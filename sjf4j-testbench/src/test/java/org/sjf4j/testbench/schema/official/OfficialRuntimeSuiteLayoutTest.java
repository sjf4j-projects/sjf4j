package org.sjf4j.testbench.schema.official;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.sjf4j.schema.SchemaDialect;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OfficialRuntimeSuiteLayoutTest {
    @TempDir
    Path suiteDir;

    @Test
    @ResourceLock(Resources.SYSTEM_OUT)
    void unifiedSuiteIncludesTaggedAndDirectoryCasesAndReportsOnlyFailures() throws Exception {
        Files.writeString(suiteDir.resolve("format.json"), """
                {
                  "description": "root validation groups",
                  "tests": [
                    {
                      "description": "required case",
                      "schema": false,
                      "tests": [{"description": "correct expectation", "data": 3, "valid": false}]
                    },
                    {
                      "description": "optional format assertion",
                      "tags": ["format-assertion"],
                      "schema": {"format": "ipv4"},
                      "tests": [{"description": "invalid IPv4", "data": "not-ipv4", "valid": false}]
                    }
                  ]
                }
                """);

        Path optional = Files.createDirectories(suiteDir.resolve("optional"));
        Files.writeString(optional.resolve("content.json"), """
                {
                  "description": "optional standalone cases",
                  "tests": [
                    {
                      "description": "expected optional mismatch",
                      "schema": {"type": "integer"},
                      "tests": [
                        {"description": "intentionally fails", "data": 4, "valid": false},
                        {"description": "correct invalid", "data": "text", "valid": false}
                      ]
                    },
                    {
                      "description": "optional format annotation should remain non-strict",
                      "tags": ["format-annotation"],
                      "schema": {"format": "ipv4"},
                      "tests": [{"description": "annotation", "data": "not-ipv4", "valid": true}]
                    },
                    {
                      "description": "incompatible future",
                      "compatibility": "9999",
                      "schema": false,
                      "tests": [{"description": "future", "data": 4, "valid": false}]
                    }
                  ]
                }
                """);

        Files.writeString(suiteDir.resolve("passing.json"), """
                {"tests": [{"schema": true, "tests": [{"data": null, "valid": true}]}]}
                """);
        Files.writeString(optional.resolve("invalid.json"), "not JSON");
        Path proposals = Files.createDirectories(suiteDir.resolve("proposals"));
        Files.writeString(proposals.resolve("ignored.json"), "not JSON");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream original = System.out;
        OfficialRuntimeTest.TestSuiteReport result;
        try (PrintStream captured = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(captured);
            try {
                result = OfficialRuntimeTest.runTestDir(SchemaDialect.DRAFT_2020_12,
                        suiteDir, false, null, null, null);
            } finally {
                System.setOut(original);
            }
        }
        String log = output.toString(StandardCharsets.UTF_8);
        assertTrue(log.contains("JSON Schema Runtime [DRAFT_2020_12]"));
        assertTrue(log.contains("REQUIRED"));
        assertTrue(log.contains("OPTIONAL"));
        assertTrue(log.contains("[OPTIONAL] optional/content.json"));
        assertTrue(log.contains("[OPTIONAL] optional/invalid.json"));
        assertFalse(log.contains("[REQUIRED]"));
        assertFalse(log.contains("passing.json"));
        assertFalse(log.contains("ignored.json"));
        assertFalse(log.contains("format.json"));

        assertEquals(6, result.sourceGroups);
        assertEquals(1, result.incompatibleGroups);
        assertEquals(2, result.required.selectedGroups);
        assertEquals(new OfficialRuntimeTest.Counts(2, 2, 0, 0), result.required.totals());
        assertEquals(3, result.optional.selectedGroups);
        assertEquals(new OfficialRuntimeTest.Counts(5, 3, 1, 1), result.optional.totals());
        assertEquals(1, result.optional.file("optional/content.json").failed);

        // Optional files can be selected by their relative path for debugging.
        OfficialRuntimeTest.TestSuiteReport selected = OfficialRuntimeTest.runTestDir(
                SchemaDialect.DRAFT_2020_12, suiteDir, false, "optional/content.json", 0, 0);
        assertEquals(new OfficialRuntimeTest.Counts(1, 0, 1, 0), selected.optional.totals());
        assertEquals(0, selected.required.totals().total());
    }

    @Test
    @ResourceLock(Resources.SYSTEM_OUT)
    void requiredFailureStillGatesAfterReportingBothCategories() throws Exception {
        Files.writeString(suiteDir.resolve("mixed.json"), """
                {"tests": [
                    {"tags": ["format-assertion"], "schema": true},
                    {"schema": false, "tests": [{"data": 1, "valid": true}]}
                ]}
                """);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try (PrintStream captured = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(captured);
            try {
                IllegalStateException failure = assertThrows(IllegalStateException.class,
                        () -> OfficialRuntimeTest.runTestDir(SchemaDialect.DRAFT_2020_12,
                                suiteDir, false, null, null, null));
                assertTrue(failure.getMessage().contains("1 failed and 0 errors"));
            } finally {
                System.setOut(original);
            }
        }
        String log = output.toString(StandardCharsets.UTF_8);
        assertTrue(log.contains("[REQUIRED] mixed.json"));
        assertTrue(log.contains("[OPTIONAL] mixed.json"));
        assertTrue(log.contains("group#1"));
        assertTrue(log.contains("group#0"));
    }

    @Test
    void readsUnifiedGroupsAndIsolatesExternalSchemasPerGroup() throws Exception {
        Files.writeString(suiteDir.resolve("ref.json"), """
                {
                  "description": "Unified validation smoke fixture",
                  "tests": [
                    {
                      "description": "integer schema",
                      "compatibility": "7",
                      "schema": {"$ref": "http://localhost:1234/shared"},
                      "externalSchemas": {
                        "http://localhost:1234/shared": {"type": "integer"}
                      },
                      "tests": [
                        {"description": "valid", "data": 3, "valid": true},
                        {"description": "invalid", "data": "text", "valid": false}
                      ]
                    },
                    {
                      "description": "string schema with same external URI",
                      "compatibility": "2019",
                      "schema": {"$ref": "http://localhost:1234/shared"},
                      "externalSchemas": {
                        "http://localhost:1234/shared": {"type": "string"}
                      },
                      "tests": [
                        {"description": "valid", "data": "text", "valid": true},
                        {"description": "invalid", "data": 3, "valid": false}
                      ]
                    },
                    {
                      "description": "future-only case",
                      "compatibility": "9999",
                      "schema": true,
                      "tests": [{"description": "future", "data": null, "valid": true}]
                    },
                    {
                      "description": "optional format assertion",
                      "tags": ["format-assertion"],
                      "schema": {"format": "ipv4"},
                      "tests": [{"description": "not required", "data": "not-ip", "valid": false}]
                    },
                    {
                      "description": "boolean false with null instance",
                      "compatibility": "=2020",
                      "schema": false,
                      "tests": [{"description": "null is still an instance", "data": null, "valid": false}]
                    }
                  ]
                }
                """);

        OfficialRuntimeTest.TestSuiteReport d7 = OfficialRuntimeTest.runTestDir(
                SchemaDialect.DRAFT_07, suiteDir, false, null, null, null);
        assertEquals(5, d7.sourceGroups);
        assertEquals(1, d7.required.selectedGroups);
        assertEquals(3, d7.incompatibleGroups);
        assertEquals(1, d7.optional.selectedGroups);
        assertEquals(new OfficialRuntimeTest.Counts(2, 2, 0, 0), d7.required.totals());
        assertEquals(new OfficialRuntimeTest.Counts(1, 1, 0, 0), d7.optional.totals());

        OfficialRuntimeTest.TestSuiteReport d19 = OfficialRuntimeTest.runTestDir(
                SchemaDialect.DRAFT_2019_09, suiteDir, false, null, null, null);
        assertEquals(2, d19.required.selectedGroups);
        assertEquals(new OfficialRuntimeTest.Counts(4, 4, 0, 0), d19.required.totals());

        OfficialRuntimeTest.TestSuiteReport d20 = OfficialRuntimeTest.runTestDir(
                SchemaDialect.DRAFT_2020_12, suiteDir, false, null, null, null);
        assertEquals(3, d20.required.selectedGroups);
        assertEquals(new OfficialRuntimeTest.Counts(5, 5, 0, 0), d20.required.totals());

        // Standalone group indices retain their positions in the upstream file.
        OfficialRuntimeTest.TestSuiteReport selected = OfficialRuntimeTest.runTestDir(
                SchemaDialect.DRAFT_2020_12, suiteDir, false, "ref.json", 4, 0);
        assertEquals(1, selected.sourceGroups);
        assertEquals(new OfficialRuntimeTest.Counts(1, 1, 0, 0), selected.required.totals());
    }
}
