package org.sjf4j.testbench.schema.official;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Records every official instance, including compilation-blocked and null instances. */
final class OfficialCaseReport {
    private final List<Map<String, Object>> details = new ArrayList<>();
    private final Map<String, int[]> byFile = new LinkedHashMap<>();
    private final int expectedFiles;
    private final int expectedGroups;
    private final int expectedTests;

    OfficialCaseReport(int expectedFiles, int expectedGroups, int expectedTests) {
        this.expectedFiles = expectedFiles;
        this.expectedGroups = expectedGroups;
        this.expectedTests = expectedTests;
    }

    synchronized void record(OfficialCaseRunner.Spec spec, int testIndex,
                             String description, boolean expected, Boolean actual,
                             String status, String detail) {
        if (!"PASS".equals(status) && !"FAIL".equals(status)
                && !"COMPILE_ERROR".equals(status) && !"EXEC_ERROR".equals(status)) {
            throw new IllegalArgumentException("Unrecognized official result: " + status);
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("file", spec.file);
        row.put("group", spec.group);
        row.put("test", testIndex);
        row.put("description", description);
        row.put("expected", expected);
        row.put("actual", actual);
        row.put("status", status);
        if (detail != null) row.put("detail", detail);
        details.add(row);
        int[] values = byFile.computeIfAbsent(spec.file, unused -> new int[4]);
        switch (status) {
            case "PASS": values[0]++; break;
            case "FAIL": values[1]++; break;
            case "COMPILE_ERROR": values[2]++; break;
            case "EXEC_ERROR": values[3]++; break;
            default: throw new AssertionError(status);
        }
    }

    synchronized void write(Path output, String revision) throws IOException {
        Files.createDirectories(output);
        int passed = 0, failed = 0, compileErrors = 0, executionErrors = 0;
        List<Map<String, Object>> files = new ArrayList<>();
        for (Map.Entry<String, int[]> e : byFile.entrySet()) {
            int[] n = e.getValue();
            passed += n[0];
            failed += n[1];
            compileErrors += n[2];
            executionErrors += n[3];
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("file", e.getKey());
            row.put("passed", n[0]);
            row.put("failed", n[1]);
            row.put("compileErrors", n[2]);
            row.put("executionErrors", n[3]);
            files.add(row);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("suite", "JSON Schema Draft 2020-12 (excluding optional)");
        summary.put("revision", revision);
        summary.put("expectedFiles", expectedFiles);
        summary.put("expectedGroups", expectedGroups);
        summary.put("expectedTests", expectedTests);
        summary.put("recordedTests", details.size());
        summary.put("passed", passed);
        summary.put("failed", failed);
        summary.put("compileErrors", compileErrors);
        summary.put("executionErrors", executionErrors);
        summary.put("unaccounted", expectedTests - details.size());
        summary.put("files", files);
        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValue(
                output.resolve("summary.json").toFile(), summary);
        mapper.writerWithDefaultPrettyPrinter().writeValue(
                output.resolve("details.json").toFile(), details);

        System.out.printf(
                "Official Compiled Draft 2020-12: %d/%d passed, %d failed, "
                        + "%d compile-blocked, %d runtime errors; %d of %d assertions accounted for%n",
                passed, expectedTests, failed, compileErrors, executionErrors,
                details.size(), expectedTests);
    }

    synchronized int recordedTests() {
        return details.size();
    }
}
