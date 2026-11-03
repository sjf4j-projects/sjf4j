package org.sjf4j.testbench.schema.official;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.Nodes;
import org.sjf4j.Sjf4j;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * All non-optional official Draft 2020-12 instances, bound to schemas at
 * annotation-processing time rather than interpreted by a runtime-only plan.
 *
 * <p>Compilation failures are reported per group and do not prevent other
 * groups from compiling. No invalid instances (including JSON null) are skipped.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public final class OfficialCompiledTest {
    private static final String TEST_ROOT = "sjf4j.schema.officialTestRoot";
    private static final String GENERATED_ROOT = "sjf4j.schema.officialGeneratedRoot";
    private static final String DIALECT_DIR = "tests/draft2020-12";
    private static final String SELECTION = "sjf4j.schema.officialCategory";
    private static final String PROBE_DIRECT = "sjf4j.schema.officialProbeDirect";
    private static final String PHASE2_BASELINE_SHA =
            "ed0ebd31d0a83fd79e99a32aa0c884b966ada057";

    private OfficialCaseRunner runner;
    private OfficialCaseReport report;
    private int expectedTests;
    private Path reportDir;
    private String revision;
    private String selection;

    @TestFactory
    Stream<DynamicNode> draft2020_12() throws Exception {
        Path suiteRoot = Paths.get(System.getProperty(TEST_ROOT,
                "build/official-json-schema-tests/suite"));
        Path generated = Paths.get(System.getProperty(GENERATED_ROOT,
                "build/generated/officialCompiled"));
        Path officialDir = suiteRoot.resolve(DIALECT_DIR);
        if (!Files.isDirectory(officialDir)) {
            throw new IllegalStateException("Official suite missing: " + officialDir);
        }

        selection = OfficialCaseCategory.selection(System.getProperty(SELECTION, "all"));
        List<OfficialCaseRunner.Spec> allSpecs = OfficialCaseRunner.readIndex(generated);
        List<OfficialCaseRunner.Spec> specs = new ArrayList<>();
        Map<String, List<OfficialCaseRunner.Spec>> byFile = new LinkedHashMap<>();
        for (OfficialCaseRunner.Spec spec : allSpecs) {
            if (!OfficialCaseCategory.selected(selection, spec.file)) continue;
            specs.add(spec);
            byFile.computeIfAbsent(spec.file, unused -> new ArrayList<>()).add(spec);
        }

        List<Path> jsonFiles;
        try (Stream<Path> entries = Files.list(officialDir)) {
            jsonFiles = entries.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        }
        // Check the entire upstream inventory before filtering: unknown mandatory
        // official files must never silently disappear from a phase's coverage.
        java.util.Set<String> upstreamFiles = new java.util.LinkedHashSet<>();
        for (Path file : jsonFiles) upstreamFiles.add(file.getFileName().toString());
        OfficialCaseCategory.assertComplete(upstreamFiles);
        java.util.Set<String> indexedFiles = new java.util.LinkedHashSet<>();
        for (OfficialCaseRunner.Spec spec : allSpecs) indexedFiles.add(spec.file);
        assertEquals(upstreamFiles, indexedFiles, "Official compiled manifest does not cover every file");
        jsonFiles.removeIf(p -> !OfficialCaseCategory.selected(selection,
                p.getFileName().toString()));
        assertEquals(jsonFiles.size(), byFile.size(), "Selected official file coverage mismatch");

        Properties totals = new Properties();
        try (java.io.Reader reader = Files.newBufferedReader(
                generated.resolve("counts.properties"), StandardCharsets.UTF_8)) {
            totals.load(reader);
        }
        assertEquals(upstreamFiles.size(), Integer.parseInt(totals.getProperty("files")));
        assertEquals(allSpecs.size(), Integer.parseInt(totals.getProperty("groups")));

        List<DynamicNode> fileNodes = new ArrayList<>();
        int assertions = 0;
        for (Path file : jsonFiles) {
            String fileName = file.getFileName().toString();
            JsonArray groups = Sjf4j.global().fromJson(Files.readAllBytes(file), JsonArray.class);
            List<OfficialCaseRunner.Spec> fileSpecs = byFile.get(fileName);
            assertEquals(groups.size(), fileSpecs.size(), fileName + ": group coverage mismatch");
            List<DynamicNode> groupNodes = new ArrayList<>();
            for (int i = 0; i < fileSpecs.size(); i++) {
                OfficialCaseRunner.Spec spec = fileSpecs.get(i);
                assertEquals(i, spec.group, fileName + ": missing or reordered group");
                JsonObject group = groups.getJsonObject(i);
                Object officialSchema = group.getNode("schema");
                String stem = fileName.substring(0, fileName.length() - ".json".length());
                Path schemaResource = generated.resolve("resources/official")
                        .resolve(stem).resolve(String.format(java.util.Locale.ROOT,
                                "%03d.json", i));
                Object generatedSchema = Sjf4j.global().fromJson(Files.readAllBytes(schemaResource));
                assertTrue(Nodes.equals(officialSchema, generatedSchema),
                        fileName + " #" + i + ": generated schema differs from upstream");

                String groupDescription = group.getString("description", "");
                JsonArray examples = group.getJsonArray("tests");
                assertEquals(examples.size(), spec.tests, spec.key() + ": test count changed");
                assertions += examples.size();
                List<DynamicNode> tests = new ArrayList<>();
                for (int j = 0; j < examples.size(); j++) {
                    JsonObject example = examples.getJsonObject(j);
                    Object data = example.get("data"); // Never skip JSON null.
                    boolean expected = example.getBoolean("valid");
                    String description = example.getString("description", "");
                    final int testIndex = j;
                    String label = "test" + String.format(java.util.Locale.ROOT, "%03d", j)
                            + " - " + description;
                    tests.add(DynamicTest.dynamicTest(label, () -> {
                        String compilationError = runner.compilationError(spec);
                        String mode = runner.compilationMode(spec);
                        String directError = runner.directCompilationError(spec);
                        if (compilationError != null) {
                            report.record(spec, groupDescription, testIndex, description, expected,
                                    null, "COMPILE_ERROR", mode, directError, compilationError);
                            fail(spec.key() + ": " + compilationError);
                        }
                        final boolean actual;
                        try {
                            actual = runner.validate(spec, data);
                        } catch (Throwable e) {
                            report.record(spec, groupDescription, testIndex, description, expected,
                                    null, "EXEC_ERROR", mode, directError, e.toString());
                            throw e;
                        }
                        report.record(spec, groupDescription, testIndex, description,
                                expected, actual, expected == actual ? "PASS" : "FAIL",
                                mode, directError, null);
                        assertEquals(expected, actual, spec.key() + " / " + description);
                    }));
                }
                groupNodes.add(DynamicContainer.dynamicContainer(
                        "group" + String.format(java.util.Locale.ROOT, "%03d", i)
                                + " - " + groupDescription,
                        tests.stream()));
            }
            fileNodes.add(DynamicContainer.dynamicContainer(fileName, groupNodes.stream()));
        }

        expectedTests = assertions;
        int indexedAssertions = 0;
        for (OfficialCaseRunner.Spec spec : allSpecs) indexedAssertions += spec.tests;
        assertEquals(indexedAssertions, Integer.parseInt(totals.getProperty("tests")),
                "Generated manifest assertion count differs from official files");
        revision = Files.readString(suiteRoot.resolve("OFFICIAL_TEST_SUITE_REV"),
                StandardCharsets.UTF_8).trim();
        if ("phase2".equals(selection) && revision.contains(PHASE2_BASELINE_SHA)) {
            assertEquals(26, jsonFiles.size(), "Phase 2 baseline file count changed");
            assertEquals(138, specs.size(), "Phase 2 baseline schema group count changed");
            assertEquals(609, assertions, "Phase 2 baseline assertion count changed");
        }
        reportDir = Paths.get("build/reports/officialCompiled").resolve(selection);
        report = new OfficialCaseReport(selection, specs, assertions);
        runner = new OfficialCaseRunner(generated,
                Paths.get("build/officialCompiled/compile").resolve(selection));
        runner.compileAll(specs, Boolean.parseBoolean(System.getProperty(PROBE_DIRECT, "false")));
        return fileNodes.stream();
    }

    @AfterAll
    void writeReport() throws Exception {
        try {
            if (report != null) {
                report.write(reportDir, revision);
                assertEquals(expectedTests, report.recordedTests(),
                        "Official compiled test executions were not all accounted for");
            }
        } finally {
            if (runner != null) runner.close();
        }
    }
}
