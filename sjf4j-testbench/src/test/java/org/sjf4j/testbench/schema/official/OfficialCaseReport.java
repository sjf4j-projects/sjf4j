package org.sjf4j.testbench.schema.official;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Reports every selected official assertion, including compile and execution blockers. */
final class OfficialCaseReport {
    private static final class CategoryStats {
        final Set<String> files = new LinkedHashSet<>();
        final Set<String> directGroups = new LinkedHashSet<>();
        final Set<String> fallbackGroups = new LinkedHashSet<>();
        final Set<String> unprobedGroups = new LinkedHashSet<>();
        final Set<String> blockedGroups = new LinkedHashSet<>();
        int groups;
        int expectedTests;
        int passed;
        int failed;
        int compileErrors;
        int executionErrors;
    }

    private final String selection;
    private final List<Map<String, Object>> details = new ArrayList<>();
    private final Map<String, Map<String, Object>> groups = new LinkedHashMap<>();
    private final Map<String, int[]> byFile = new LinkedHashMap<>();
    private final Map<String, CategoryStats> categories = new LinkedHashMap<>();
    private final int expectedFiles;
    private final int expectedGroups;
    private final int expectedTests;
    private final Map<String, Integer> selectedTestCounts;

    OfficialCaseReport(String selection, List<OfficialCaseRunner.Spec> specs, int expectedTests,
                       Map<String, Integer> selectedTestCounts) {
        this.selection = selection;
        this.expectedTests = expectedTests;
        this.expectedGroups = specs.size();
        this.selectedTestCounts = new LinkedHashMap<>(selectedTestCounts);
        Set<String> files = new LinkedHashSet<>();
        int total = 0;
        for (OfficialCaseRunner.Spec spec : specs) {
            files.add(spec.file);
            String category = OfficialCaseCategory.category(spec.file).name();
            CategoryStats c = categories.computeIfAbsent(category, ignored -> new CategoryStats());
            c.files.add(spec.file);
            int count = this.selectedTestCounts.getOrDefault(spec.key(), 0);
            if (count < 1 || count > spec.tests) {
                throw new IllegalArgumentException("Invalid selected test count for " + spec.key()
                        + ": " + count);
            }
            c.groups++;
            c.expectedTests += count;
            total += count;
        }
        if (total != expectedTests) {
            throw new IllegalArgumentException("Manifest count mismatch: "
                    + total + " vs " + expectedTests);
        }
        expectedFiles = files.size();
    }

    synchronized void record(OfficialCaseRunner.Spec spec, String groupDescription,
                             int testIndex, String description, boolean expected, Boolean actual,
                             String status, String mode, String directDiagnostic, String detail) {
        if (!"PASS".equals(status) && !"FAIL".equals(status)
                && !"COMPILE_ERROR".equals(status) && !"EXEC_ERROR".equals(status)) {
            throw new IllegalArgumentException("Unrecognized official result: " + status);
        }
        String category = OfficialCaseCategory.category(spec.file).name();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("category", category);
        row.put("file", spec.file);
        row.put("group", spec.group);
        row.put("groupDescription", groupDescription);
        row.put("test", testIndex);
        row.put("description", description);
        row.put("expected", expected);
        row.put("actual", actual);
        row.put("status", status);
        row.put("mode", mode);
        if (detail != null) row.put("detail", detail);
        details.add(row);

        Map<String, Object> group = groups.get(spec.key());
        if (group == null) {
            group = new LinkedHashMap<>();
            group.put("category", category);
            group.put("file", spec.file);
            group.put("group", spec.group);
            group.put("description", groupDescription);
            group.put("mode", mode);
            group.put("tests", selectedTestCounts.get(spec.key()));
            group.put("passed", 0);
            group.put("failed", 0);
            group.put("compileErrors", 0);
            group.put("executionErrors", 0);
            if (directDiagnostic != null) group.put("directDiagnostic", directDiagnostic);
            groups.put(spec.key(), group);
        } else if (!mode.equals(group.get("mode"))) {
            throw new IllegalStateException("Compilation mode changed within group " + spec.key());
        }

        CategoryStats c = categories.get(category);
        if (c == null) throw new IllegalStateException("Unknown category " + category);
        switch (mode) {
            case "DIRECT": c.directGroups.add(spec.key()); break;
            case "FALLBACK": c.fallbackGroups.add(spec.key()); break;
            case "UNPROBED": c.unprobedGroups.add(spec.key()); break;
            case "COMPILE_ERROR": c.blockedGroups.add(spec.key()); break;
            default: throw new IllegalArgumentException("Unknown mode " + mode);
        }
        int[] fileCounts = byFile.computeIfAbsent(spec.file, unused -> new int[4]);
        int index;
        switch (status) {
            case "PASS": index = 0; c.passed++; break;
            case "FAIL": index = 1; c.failed++; break;
            case "COMPILE_ERROR": index = 2; c.compileErrors++; break;
            case "EXEC_ERROR": index = 3; c.executionErrors++; break;
            default: throw new AssertionError(status);
        }
        fileCounts[index]++;
        String key = index == 0 ? "passed" : index == 1 ? "failed"
                : index == 2 ? "compileErrors" : "executionErrors";
        group.put(key, ((Integer) group.get(key)) + 1);
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
            row.put("category", OfficialCaseCategory.category(e.getKey()).name());
            row.put("file", e.getKey());
            row.put("passed", n[0]);
            row.put("failed", n[1]);
            row.put("compileErrors", n[2]);
            row.put("executionErrors", n[3]);
            files.add(row);
        }

        Map<String, Object> categoryRows = new LinkedHashMap<>();
        int directGroups = 0, fallbackGroups = 0, blockedGroups = 0, unprobedGroups = 0;
        for (Map.Entry<String, CategoryStats> entry : categories.entrySet()) {
            CategoryStats c = entry.getValue();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("files", c.files.size());
            row.put("groups", c.groups);
            row.put("expectedTests", c.expectedTests);
            row.put("passed", c.passed);
            row.put("failed", c.failed);
            row.put("compileErrors", c.compileErrors);
            row.put("executionErrors", c.executionErrors);
            row.put("directGroups", c.directGroups.size());
            row.put("fallbackGroups", c.fallbackGroups.size());
            row.put("unprobedGroups", c.unprobedGroups.size());
            row.put("blockedGroups", c.blockedGroups.size());
            categoryRows.put(entry.getKey(), row);
            directGroups += c.directGroups.size();
            fallbackGroups += c.fallbackGroups.size();
            unprobedGroups += c.unprobedGroups.size();
            blockedGroups += c.blockedGroups.size();
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("suite", "JSON Schema Draft 2020-12 (excluding optional)");
        summary.put("selection", selection);
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
        summary.put("directGroups", directGroups);
        summary.put("fallbackGroups", fallbackGroups);
        summary.put("unprobedGroups", unprobedGroups);
        summary.put("blockedGroups", blockedGroups);
        summary.put("categories", categoryRows);
        summary.put("files", files);
        List<Map<String, Object>> gaps = new ArrayList<>();
        for (Map<String, Object> group : groups.values()) {
            if ("FALLBACK".equals(group.get("mode"))
                    || "COMPILE_ERROR".equals(group.get("mode"))
                    || ((Integer) group.get("failed")) > 0
                    || ((Integer) group.get("executionErrors")) > 0) {
                gaps.add(group);
            }
        }
        ObjectMapper mapper = new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValue(
                output.resolve("summary.json").toFile(), summary);
        mapper.writerWithDefaultPrettyPrinter().writeValue(
                output.resolve("groups.json").toFile(), new ArrayList<>(groups.values()));
        mapper.writerWithDefaultPrettyPrinter().writeValue(
                output.resolve("gaps.json").toFile(), gaps);
        mapper.writerWithDefaultPrettyPrinter().writeValue(
                output.resolve("details.json").toFile(), details);

        System.out.printf(
                "Official Compiled [%s]: %d/%d passed, %d failed, %d compile-blocked, "
                        + "%d execution errors; groups direct=%d fallback=%d unprobed=%d blocked=%d; "
                        + "%d of %d assertions accounted for%n",
                selection, passed, expectedTests, failed, compileErrors, executionErrors,
                directGroups, fallbackGroups, unprobedGroups, blockedGroups,
                details.size(), expectedTests);
    }

    synchronized int recordedTests() {
        return details.size();
    }
}
