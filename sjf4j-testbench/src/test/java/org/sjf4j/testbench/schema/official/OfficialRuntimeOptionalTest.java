package org.sjf4j.testbench.schema.official;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.sjf4j.schema.SchemaDialect;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.EnumMap;
import java.util.Map;

/**
 * Diagnostic-only optional validation suite. Mismatches and evaluation errors
 * are reported, never asserted as required JSON Schema conformance failures.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public final class OfficialRuntimeOptionalTest {

    private final Map<SchemaDialect, OfficialRuntimeTest.TestSuiteReport> results =
            new EnumMap<>(SchemaDialect.class);

    @Test
    void draft7() throws Exception {
        run(SchemaDialect.DRAFT_07);
    }

    @Test
    void draft2019_09() throws Exception {
        run(SchemaDialect.DRAFT_2019_09);
    }

    @Test
    void draft2020_12() throws Exception {
        run(SchemaDialect.DRAFT_2020_12);
    }

    private void run(SchemaDialect dialect) throws Exception {
        OfficialRuntimeTest.TestSuiteReport result = OfficialRuntimeTest.runOptional(dialect);
        results.put(dialect, result);
    }

    @AfterAll
    void writeReport() throws Exception {
        Path directory = Paths.get(System.getProperty(
                "sjf4j.schema.officialOptionalReportDir", "build/reports/officialRuntimeOptional"));
        Files.createDirectories(directory);

        StringBuilder markdown = new StringBuilder();
        markdown.append("# Official Runtime Optional Validation\n\n");
        markdown.append("Non-blocking diagnostics: discrepancies and errors do not fail the required suite.\n\n");
        markdown.append("| Draft | Groups | Assertions | Passed | Failed | Errors |\n");
        markdown.append("| --- | ---: | ---: | ---: | ---: | ---: |\n");

        for (Map.Entry<SchemaDialect, OfficialRuntimeTest.TestSuiteReport> entry : results.entrySet()) {
            SchemaDialect draft = entry.getKey();
            OfficialRuntimeTest.TestSuiteReport summary = entry.getValue();
            markdown.append("| ").append(draft)
                    .append(" | ").append(summary.selectedGroups)
                    .append(" | ").append(summary.total)
                    .append(" | ").append(summary.passed)
                    .append(" | ").append(summary.failed)
                    .append(" | ").append(summary.error)
                    .append(" |\n");

            System.out.println("Official Runtime Optional " + draft + ": "
                    + summary.passed + "/" + summary.total + " passed, "
                    + summary.failed + " failed, " + summary.error + " errors");
        }

        for (Map.Entry<SchemaDialect, OfficialRuntimeTest.TestSuiteReport> entry : results.entrySet()) {
            markdown.append("\n## ").append(entry.getKey()).append("\n\n");
            for (OfficialRuntimeTest.TestFileReport file : entry.getValue().files()) {
                if (file.total == 0) continue;
                markdown.append("### ").append(file.fileName).append("\n\n");
                markdown.append("Passed: ").append(file.passed)
                        .append(" / ").append(file.total)
                        .append("; failed: ").append(file.failed)
                        .append("; errors: ").append(file.error).append("\n\n");
                for (String failure : file.failures) {
                    markdown.append("- ").append(failure.replace('\r', ' ')
                            .replace('\n', ' ')).append("\n");
                }
                if (!file.failures.isEmpty()) markdown.append("\n");
            }
        }

        Path report = directory.resolve("summary.md");
        Files.writeString(report, markdown.toString(), StandardCharsets.UTF_8);
        System.out.println("Official Runtime Optional report: " + report.toAbsolutePath());
    }
}
