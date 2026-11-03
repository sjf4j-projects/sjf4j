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

    // Only the standalone main entrypoint uses these filters. JUnit continues
    // to execute every mandatory example in the selected category.
    private String mainCategory;
    private String mainFile;
    private int mainGroup = -1;
    private int mainCase = -1;

    /**
     * Standalone IntelliJ entrypoint (run the test class as an Application).
     *
     * <p>Arguments: [category [file.json [groupIndex [testIndex]]]].
     * Category defaults to "all"; available categories include phase2, phase3,
     * basic, scalar, object, array, applicator, unevaluated, reference.
     * Requires an existing local suite and generated bindings (Gradle's
     * downloadOfficialSchemaTests + generateOfficialCompiledCases once).
     */
    public static void main(String[] args) throws Exception {
        if (args.length > 4) {
            throw new IllegalArgumentException(
                    "Usage: OfficialCompiledTest [category [file.json [groupIndex [testIndex]]]]");
        }
        OfficialCompiledTest instance = new OfficialCompiledTest();
        instance.mainCategory = args.length > 0 ? args[0] : "all";
        if (args.length > 1) {
            instance.mainFile = args[1];
            if (!instance.mainFile.endsWith(".json")) {
                throw new IllegalArgumentException("Expected an official file ending in .json: "
                        + instance.mainFile);
            }
        }
        if (args.length > 2) instance.mainGroup = Integer.parseInt(args[2]);
        if (args.length > 3) instance.mainCase = Integer.parseInt(args[3]);
        if (instance.mainGroup < -1 || instance.mainCase < -1) {
            throw new IllegalArgumentException("Group and test indexes must be nonnegative");
        }

        int[] counts = new int[2]; // executed, failed
        try {
            try (Stream<DynamicNode> nodes = instance.draft2020_12()) {
                nodes.forEach(node -> executeMainNode(node, counts, ""));
            }
        } finally {
            instance.writeReport();
        }
        System.out.printf("OfficialCompiledTest.main: %d executed, %d failed%n",
                counts[0], counts[1]);
        if (counts[1] > 0) {
            throw new AssertionError(counts[1] + " official compiled case(s) failed");
        }
    }

    private static void executeMainNode(DynamicNode node, int[] counts, String prefix) {
        String label = prefix.isEmpty() ? node.getDisplayName()
                : prefix + " / " + node.getDisplayName();
        if (node instanceof DynamicTest) {
            counts[0]++;
            try {
                ((DynamicTest) node).getExecutable().execute();
            } catch (Throwable e) {
                counts[1]++;
                System.err.println("FAIL " + label + ": " + e);
            }
        } else if (node instanceof DynamicContainer) {
            try (Stream<? extends DynamicNode> children = ((DynamicContainer) node).getChildren()) {
                children.forEach(child -> executeMainNode(child, counts, label));
            }
        }
    }

    private static Path localBuildPath(String suffix) {
        Path module = Paths.get("build").resolve(suffix);
        if (Files.exists(module)) return module;
        Path repository = Paths.get("sjf4j-testbench", "build").resolve(suffix);
        return Files.exists(repository) ? repository : module;
    }

    @TestFactory
    Stream<DynamicNode> draft2020_12() throws Exception {
        Path suiteRoot = Paths.get(System.getProperty(TEST_ROOT,
                localBuildPath("official-json-schema-tests/suite").toString()));
        Path generated = Paths.get(System.getProperty(GENERATED_ROOT,
                localBuildPath("generated/officialCompiled").toString()));
        Path officialDir = suiteRoot.resolve(DIALECT_DIR);
        if (!Files.isDirectory(officialDir)) {
            throw new IllegalStateException("Official suite missing: " + officialDir);
        }

        selection = OfficialCaseCategory.selection(mainCategory == null
                ? System.getProperty(SELECTION, "all") : mainCategory);
        List<OfficialCaseRunner.Spec> allSpecs = OfficialCaseRunner.readIndex(generated);
        List<OfficialCaseRunner.Spec> specs = new ArrayList<>();
        Map<String, List<OfficialCaseRunner.Spec>> byFile = new LinkedHashMap<>();
        for (OfficialCaseRunner.Spec spec : allSpecs) {
            if (!OfficialCaseCategory.selected(selection, spec.file)
                    || (mainFile != null && !mainFile.equals(spec.file))
                    || (mainGroup >= 0 && mainGroup != spec.group)) continue;
            specs.add(spec);
            byFile.computeIfAbsent(spec.file, unused -> new ArrayList<>()).add(spec);
        }

        List<Path> jsonFiles;
        try (Stream<Path> entries = Files.list(officialDir)) {
            jsonFiles = new ArrayList<>(entries
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .filter(Files::isRegularFile)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList());
        }
        // Check the entire upstream inventory before filtering: unknown mandatory
        // official files must never silently disappear from a phase's coverage.
        java.util.Set<String> upstreamFiles = new java.util.LinkedHashSet<>();
        for (Path file : jsonFiles) upstreamFiles.add(file.getFileName().toString());
        OfficialCaseCategory.assertComplete(upstreamFiles);
        java.util.Set<String> indexedFiles = new java.util.LinkedHashSet<>();
        for (OfficialCaseRunner.Spec spec : allSpecs) indexedFiles.add(spec.file);
        assertEquals(upstreamFiles, indexedFiles, "Official compiled manifest does not cover every file");
        jsonFiles.removeIf(p -> !byFile.containsKey(p.getFileName().toString()));
        if (specs.isEmpty()) {
            throw new IllegalArgumentException("No official cases matched: category="
                    + selection + ", file=" + mainFile + ", group=" + mainGroup);
        }
        assertEquals(jsonFiles.size(), byFile.size(), "Selected official file coverage mismatch");

        Properties totals = new Properties();
        try (java.io.Reader reader = Files.newBufferedReader(
                generated.resolve("counts.properties"), StandardCharsets.UTF_8)) {
            totals.load(reader);
        }
        assertEquals(upstreamFiles.size(), Integer.parseInt(totals.getProperty("files")));
        assertEquals(allSpecs.size(), Integer.parseInt(totals.getProperty("groups")));

        List<DynamicNode> fileNodes = new ArrayList<>();
        Map<String, Integer> selectedCounts = new LinkedHashMap<>();
        int assertions = 0;
        for (Path file : jsonFiles) {
            String fileName = file.getFileName().toString();
            JsonArray groups = Sjf4j.global().fromJson(Files.readAllBytes(file), JsonArray.class);
            List<OfficialCaseRunner.Spec> fileSpecs = byFile.get(fileName);
            if (mainGroup < 0) {
                assertEquals(groups.size(), fileSpecs.size(), fileName + ": group coverage mismatch");
            }
            List<DynamicNode> groupNodes = new ArrayList<>();
            for (OfficialCaseRunner.Spec spec : fileSpecs) {
                int i = spec.group;
                assertTrue(i < groups.size(), spec.key() + ": group not present");
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
                if (mainCase >= examples.size()) {
                    throw new IllegalArgumentException(spec.key()
                            + ": test index does not exist: " + mainCase);
                }
                int selected = mainCase >= 0 ? 1 : examples.size();
                assertions += selected;
                selectedCounts.put(spec.key(), selected);
                List<DynamicNode> tests = new ArrayList<>();
                for (int j = 0; j < examples.size(); j++) {
                    if (mainCase >= 0 && j != mainCase) continue;
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
        if (mainFile == null && mainGroup < 0 && mainCase < 0
                && revision.contains(PHASE2_BASELINE_SHA)) {
            // Pin category totals as well as the manifest. These checks must detect
            // missing/reordered official cases, not assume every new upstream revision
            // has the same group counts as the pinned fixture.
            switch (selection) {
                case "phase2":
                    assertBaseline(jsonFiles.size(), specs.size(), assertions, 26, 138, 609);
                    break;
                case "phase3":
                    assertBaseline(jsonFiles.size(), specs.size(), assertions, 15, 169, 525);
                    break;
                case "array":
                    assertBaseline(jsonFiles.size(), specs.size(), assertions, 9, 73, 251);
                    break;
                case "applicator":
                    assertBaseline(jsonFiles.size(), specs.size(), assertions, 6, 96, 274);
                    break;
                case "unevaluated":
                    assertBaseline(jsonFiles.size(), specs.size(), assertions, 2, 73, 200);
                    break;
                default:
                    // Full-suite and other category baselines are manifest-derived.
                    break;
            }
        }
        Path moduleBuild = generated.getParent().getParent();
        reportDir = moduleBuild.resolve("reports/officialCompiled").resolve(selection);
        report = new OfficialCaseReport(selection, specs, assertions, selectedCounts);
        runner = new OfficialCaseRunner(generated,
                moduleBuild.resolve("officialCompiled/compile").resolve(selection));
        runner.compileAll(specs, Boolean.parseBoolean(System.getProperty(PROBE_DIRECT, "false")));
        return fileNodes.stream();
    }

    private void assertBaseline(int files, int groups, int tests,
                                int expectedFiles, int expectedGroups, int expectedTests) {
        assertEquals(expectedFiles, files, selection + " pinned file count changed");
        assertEquals(expectedGroups, groups, selection + " pinned group count changed");
        assertEquals(expectedTests, tests, selection + " pinned assertion count changed");
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
