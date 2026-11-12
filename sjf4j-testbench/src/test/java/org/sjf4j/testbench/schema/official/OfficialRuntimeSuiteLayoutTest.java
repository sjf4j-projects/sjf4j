package org.sjf4j.testbench.schema.official;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sjf4j.schema.SchemaDialect;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class OfficialRuntimeSuiteLayoutTest {
    @TempDir
    Path suiteDir;

    @Test
    void optionalSuiteIncludesTaggedAndDirectoryCasesAndReportsFailures() throws Exception {
        Files.writeString(suiteDir.resolve("format.json"), """
                {
                  "description": "root validation groups",
                  "tests": [
                    {
                      "description": "required case is not optional",
                      "schema": false,
                      "tests": [{"description": "wrong expectation", "data": 3, "valid": true}]
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

        OfficialRuntimeTest.TestSuiteReport result = OfficialRuntimeTest.runOptionalTestDir(
                SchemaDialect.DRAFT_2020_12, suiteDir);

        assertEquals(3, result.selectedGroups);
        assertEquals(4, result.total);
        assertEquals(3, result.passed);
        assertEquals(1, result.failed); // Diagnostic only: did not throw.
        assertEquals(0, result.error);
        assertEquals(1, result.incompatibleGroups);
        assertEquals(1, result.file("optional/content.json").failed);
    }

    @Test
    void optionalSuiteMustExist() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> OfficialRuntimeTest.runOptionalTestDir(SchemaDialect.DRAFT_07, suiteDir));
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
        assertEquals(1, d7.selectedGroups);
        assertEquals(3, d7.incompatibleGroups);
        assertEquals(1, d7.taggedGroups);
        assertEquals(2, d7.passed);
        assertEquals(2, d7.total);

        OfficialRuntimeTest.TestSuiteReport d19 = OfficialRuntimeTest.runTestDir(
                SchemaDialect.DRAFT_2019_09, suiteDir, false, null, null, null);
        assertEquals(2, d19.selectedGroups);
        assertEquals(4, d19.passed);
        assertEquals(4, d19.total);

        OfficialRuntimeTest.TestSuiteReport d20 = OfficialRuntimeTest.runTestDir(
                SchemaDialect.DRAFT_2020_12, suiteDir, false, null, null, null);
        assertEquals(3, d20.selectedGroups);
        assertEquals(5, d20.passed);
        assertEquals(5, d20.total);

        // Standalone group indices retain their positions in the upstream file.
        OfficialRuntimeTest.TestSuiteReport selected = OfficialRuntimeTest.runTestDir(
                SchemaDialect.DRAFT_2020_12, suiteDir, false, "ref.json", 4, 0);
        assertEquals(1, selected.sourceGroups);
        assertEquals(1, selected.passed);
        assertEquals(1, selected.total);
    }
}
