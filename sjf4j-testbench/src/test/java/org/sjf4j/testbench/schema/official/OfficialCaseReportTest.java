package org.sjf4j.testbench.schema.official;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class OfficialCaseReportTest {
    @TempDir
    Path output;

    @Test
    @ResourceLock(Resources.SYSTEM_OUT)
    void reportsSelectedCasesByRecordedCompilationModeAndSeparatesErrors() throws Exception {
        var direct = new OfficialCaseRunner.Spec("type.json", 0, "OfficialTypeG000", 3, false);
        var fallback = new OfficialCaseRunner.Spec("type.json", 1, "OfficialTypeG001", 4, true);
        var blocked = new OfficialCaseRunner.Spec("type.json", 2, "OfficialTypeG002", 2, true);
        var missing = new OfficialCaseRunner.Spec("type.json", 3, "OfficialTypeG003", 1, false);
        // A filtered run selects only two of the four fallback cases.
        var report = new OfficialCaseReport("draft2020-12", List.of(direct, fallback, blocked, missing), 8,
                Map.of(direct.key(), 3, fallback.key(), 2, blocked.key(), 2, missing.key(), 1));

        record(report, direct, 0, "PASS", "DIRECT");
        record(report, direct, 1, "FAIL", "DIRECT");
        record(report, direct, 2, "PASS", "DIRECT");
        record(report, fallback, 1, "PASS", "FALLBACK");
        record(report, fallback, 3, "EXEC_ERROR", "FALLBACK");
        record(report, blocked, 0, "COMPILE_ERROR", "COMPILE_ERROR");
        record(report, blocked, 1, "COMPILE_ERROR", "COMPILE_ERROR");

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        PrintStream original = System.out;
        try (PrintStream captured = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            System.setOut(captured);
            try {
                report.write(output, "test-revision");
            } finally {
                System.setOut(original);
            }
        }
        var summary = OfficialFixtures.readObject(output.resolve("summary.json"));
        assertEquals(8, summary.getInt("expectedTests"));
        assertEquals(7, summary.getInt("recordedTests"));
        assertEquals(3, summary.getInt("passed"));
        assertEquals(1, summary.getInt("failed"));
        assertEquals(2, summary.getInt("compileErrors"));
        assertEquals(1, summary.getInt("executionErrors"));
        assertEquals(1, summary.getInt("unaccounted"));
        assertEquals(1, summary.getInt("directGroups"));
        assertEquals(1, summary.getInt("fallbackGroups"));
        assertEquals(1, summary.getInt("blockedGroups"));
        assertEquals(3, summary.getInt("directTests"));
        assertEquals(2, summary.getInt("fallbackTests"));
        assertEquals(2, summary.getInt("blockedTests"));
        var category = summary.getJsonObject("categories").getJsonObject("BASIC");
        assertEquals(2, category.getInt("fallbackTests"));
        String log = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(log.contains("JSON Schema Compiled [REQUIRED] [draft2020-12]"));
        assertTrue(log.lines().anyMatch(line -> line.trim().matches("FALLBACK\\s+1\\s+2")));
        assertTrue(log.lines().anyMatch(line -> line.trim().matches("COMPILE_ERROR\\s+1\\s+2")));
        assertTrue(log.contains("Unaccounted: 1"));
    }

    private static void record(OfficialCaseReport report, OfficialCaseRunner.Spec spec, int test,
                               String status, String mode) {
        Boolean actual = switch (status) {
            case "PASS" -> Boolean.TRUE;
            case "FAIL" -> Boolean.FALSE;
            default -> null;
        };
        report.record(spec, "group", test, "case", true, actual, status, mode, null, null);
    }
}
