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

    private OfficialCaseRunner runner;
    private OfficialCaseReport report;
    private int expectedTests;
    private Path reportDir;
    private String revision;

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

        List<OfficialCaseRunner.Spec> specs = OfficialCaseRunner.readIndex(generated);
        Map<String, List<OfficialCaseRunner.Spec>> byFile = new LinkedHashMap<>();
        for (OfficialCaseRunner.Spec spec : specs) {
            byFile.computeIfAbsent(spec.file, unused -> new ArrayList<>()).add(spec);
        }

        List<Path> jsonFiles;
        try (Stream<Path> entries = Files.list(officialDir)) {
            jsonFiles = entries.filter(p -> p.getFileName().toString().endsWith(".json"))
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        }
        // The generator must cover exactly the official root files, never optional/.
        assertEquals(jsonFiles.size(), byFile.size(), "Official file coverage mismatch");
        for (Path file : jsonFiles) {
            assertTrue(byFile.containsKey(file.getFileName().toString()),
                    "Missing official compiled file: " + file.getFileName());
        }

        Properties totals = new Properties();
        try (java.io.Reader reader = Files.newBufferedReader(
                generated.resolve("counts.properties"), StandardCharsets.UTF_8)) {
            totals.load(reader);
        }
        assertEquals(jsonFiles.size(), Integer.parseInt(totals.getProperty("files")));
        assertEquals(specs.size(), Integer.parseInt(totals.getProperty("groups")));

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
                        if (compilationError != null) {
                            report.record(spec, testIndex, description, expected,
                                    null, "COMPILE_ERROR", compilationError);
                            fail(spec.key() + ": " + compilationError);
                        }
                        final boolean actual;
                        try {
                            actual = runner.validate(spec, data);
                        } catch (Throwable e) {
                            report.record(spec, testIndex, description, expected,
                                    null, "EXEC_ERROR", e.toString());
                            throw e;
                        }
                        report.record(spec, testIndex, description, expected, actual,
                                expected == actual ? "PASS" : "FAIL", null);
                        assertEquals(expected, actual, spec.key() + " / " + description);
                    }));
                }
                groupNodes.add(DynamicContainer.dynamicContainer(
                        "group" + String.format(java.util.Locale.ROOT, "%03d", i)
                                + " - " + group.getString("description", ""),
                        tests.stream()));
            }
            fileNodes.add(DynamicContainer.dynamicContainer(fileName, groupNodes.stream()));
        }

        expectedTests = assertions;
        assertEquals(assertions, Integer.parseInt(totals.getProperty("tests")),
                "Generated manifest assertion count differs from official files");
        revision = Files.readString(suiteRoot.resolve("OFFICIAL_TEST_SUITE_REV"),
                StandardCharsets.UTF_8).trim();
        reportDir = Paths.get("build/reports/officialCompiled");
        report = new OfficialCaseReport(jsonFiles.size(), specs.size(), assertions);
        runner = new OfficialCaseRunner(generated, Paths.get("build/officialCompiled/compile"));
        runner.compileAll(specs);
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
