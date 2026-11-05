package org.sjf4j.testbench.schema.official;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonType;
import org.sjf4j.JsonObject;
import org.sjf4j.Nodes;
import org.sjf4j.Sjf4j;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * All non-optional official Draft-07, 2019-09, and 2020-12 instances,
 * bound to schemas at annotation-processing time rather than a runtime-only plan.
 *
 * <p>Compilation failures are reported per group and do not prevent other
 * groups from compiling. No invalid instances (including JSON null) are skipped.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public final class OfficialCompiledTest {

    private static Set<String> fallbackGroups(String draft) {
        Set<String> groups = new LinkedHashSet<>();
        // Explicit exceptions are per dialect; no old-draft group inherits
        // the Draft 2020-12 fallback list or silently changes its mode.
        if ("draft7".equals(draft)) return Collections.unmodifiableSet(groups);
        if ("draft2019-09".equals(draft)) {
            // Standard metaschema/vocabulary interpretation, not a root-level
            // predicate that can be reduced to a direct Java check.
            fallback(groups, "defs.json", 0);
            fallback(groups, "ref.json", 6);
            fallback(groups, "vocabulary.json", 0, 1);

            // Cross-subschema evaluated-location propagation (including
            // recursiveRef scope in the unevaluatedItems/Properties suites).
            fallback(groups, "not.json", 8);
            fallback(groups, "ref.json", 13, 36);
            fallback(groups, "unevaluatedItems.json",
                    7, 8, 9, 10, 11, 12, 13, 15, 17, 18, 19, 20, 21, 24);
            fallback(groups, "unevaluatedProperties.json",
                    10, 11, 12, 14, 15, 16, 17, 19, 20, 21, 22, 23, 24,
                    25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 39, 40);
            return Collections.unmodifiableSet(groups);
        }
        if (!"draft2020-12".equals(draft)) {
            throw new IllegalArgumentException("Unsupported official compiled draft: " + draft);
        }
        fallback(groups, "defs.json", 0);
        // The remaining dynamicRef case combines cross-resource dynamic scope with unevaluatedProperties.
        fallback(groups, "dynamicRef.json", 13);
        fallback(groups, "not.json", 8);
        fallback(groups, "ref.json", 6, 13);
        // Only groups that require cross-subschema evaluated-location tracking
        // remain fallback. Isolated root-local groups compile directly.
        fallback(groups, "unevaluatedItems.json", 8, 10, 11, 12, 14, 16, 17, 18, 19, 20, 21, 22, 23, 24, 27);
        fallback(groups, "unevaluatedProperties.json", 10, 11, 12, 14, 15, 16, 17, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32, 33, 34, 35, 39, 40);
        // Custom metaschemas need vocabulary-aware interpretation, even when
        // the schema uses a familiar validation keyword such as type.
        fallback(groups, "vocabulary.json", 0, 1);
        return Collections.unmodifiableSet(groups);
    }



    private static final String TEST_ROOT = "sjf4j.schema.officialTestRoot";
    private static final String GENERATED_ROOT = "sjf4j.schema.officialGeneratedRoot";
    private static final List<String> ALL_DRAFTS = List.of(
            "draft7", "draft2019-09", "draft2020-12");

    /**
     * Explicit exceptions for official schema groups that cannot yet
     * compile with fallback=false. New groups are always direct by default.
     *
     * <p>Add an entry only after checking the direct javac diagnostic and
     * confirming the missing compiler support. Do not auto-populate this list.
     * Existing entries were initially collected by a direct-compile probe;
     * they still need individual review before being considered permanent.
     */
    private String draft;
    private final List<OfficialCompiledTest> runs = new ArrayList<>();

    private List<String> selectedDrafts() {
        String selected = mainDialect == null ? "all" : mainDialect;
        if ("all".equals(selected)) return ALL_DRAFTS;
        if (!ALL_DRAFTS.contains(selected)) {
            throw new IllegalArgumentException("Invalid official compiled draft: " + selected);
        }
        return List.of(selected);
    }

    private static void fallback(Set<String> groups, String file, int... indices) {
        for (int index : indices) {
            String key = file + "#" + index;
            if (index < 0 || !groups.add(key)) {
                throw new IllegalArgumentException("Invalid or duplicate fallback group: " + key);
            }
        }
    }

    private OfficialCaseRunner runner;
    private OfficialCaseReport report;
    private int expectedTests;
    private Path reportDir;
    private String revision;
    private String selection;

    // Standalone Main arguments; the JUnit/Gradle entrypoint always runs every draft and case.
    private String mainDialect;
    private String mainFile;
    private int mainGroup = -1;
    private int mainCase = -1;

    /**
     * Standalone runner invoked by OfficialCompiledMain (run that class as an Application).
     *
     * <p>Arguments: [dialect [file.json [groupIndex [testIndex]]]].
     * Dialect defaults to "all"; available dialects: all, draft7,
     * draft2019-09, draft2020-12. No system properties select tests.
     * Requires an existing local suite and generated bindings (Gradle's
     * downloadOfficialSchemaTests + generateOfficialCompiledCases once).
     */
    static void runStandalone(String[] args) throws Exception {
        if (args.length > 4) {
            throw new IllegalArgumentException(
                    "Usage: OfficialCompiledMain [dialect [file.json [groupIndex [testIndex]]]]");
        }
        OfficialCompiledTest instance = new OfficialCompiledTest();
        instance.mainDialect = args.length > 0 ? args[0] : "all";
        instance.selectedDrafts(); // Validate before reading any test resources.
        if (args.length > 1) {
            instance.mainFile = args[1];
            if (!instance.mainFile.endsWith(".json")
                    || instance.mainFile.contains("/") || instance.mainFile.contains("\\")) {
                throw new IllegalArgumentException("Expected an official file ending in .json: "
                        + instance.mainFile);
            }
        }
        if (args.length > 2) instance.mainGroup = Integer.parseInt(args[2]);
        if (args.length > 3) instance.mainCase = Integer.parseInt(args[3]);
        if ((args.length > 2 && instance.mainGroup < 0)
                || (args.length > 3 && instance.mainCase < 0)) {
            throw new IllegalArgumentException("Group and test indexes must be nonnegative");
        }
        if (instance.mainGroup >= 0 && instance.mainFile == null) {
            throw new IllegalArgumentException("Specify file.json before groupIndex");
        }
        if (instance.mainCase >= 0 && instance.mainGroup < 0) {
            throw new IllegalArgumentException("Specify groupIndex before testIndex");
        }

        int[] counts = new int[2]; // executed, failed
        try {
            try (Stream<DynamicNode> nodes = instance.officialDrafts()) {
                nodes.forEach(node -> executeMainNode(node, counts, ""));
            }
        } finally {
            instance.writeReport();
        }
        System.out.printf("OfficialCompiledMain: %d executed, %d failed%n",
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
                e.printStackTrace(System.err);
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
    Stream<DynamicNode> officialDrafts() throws Exception {
        List<DynamicNode> drafts = new ArrayList<>();
        for (String selected : selectedDrafts()) {
            OfficialCompiledTest test = new OfficialCompiledTest();
            test.draft = selected;
            test.mainDialect = mainDialect;
            test.mainFile = mainFile;
            test.mainGroup = mainGroup;
            test.mainCase = mainCase;
            runs.add(test);
            drafts.add(DynamicContainer.dynamicContainer(selected, test.officialDraft()));
        }
        return drafts.stream();
    }

    private Stream<DynamicNode> officialDraft() throws Exception {
        Path suiteRoot = Paths.get(System.getProperty(TEST_ROOT,
                localBuildPath("official-json-schema-tests/suite").toString()));
        Path generated = Paths.get(System.getProperty(GENERATED_ROOT,
                localBuildPath("generated/officialCompiled").toString())).resolve(draft);
        Path officialDir = suiteRoot.resolve("tests").resolve(draft);
        if (!Files.isDirectory(officialDir)) {
            throw new IllegalStateException("Official suite missing: " + officialDir);
        }

        selection = "all";
        List<OfficialCaseRunner.Spec> allSpecs = OfficialCaseRunner.readIndex(generated, fallbackGroups(draft));
        List<OfficialCaseRunner.Spec> specs = new ArrayList<>();
        Map<String, List<OfficialCaseRunner.Spec>> byFile = new LinkedHashMap<>();
        for (OfficialCaseRunner.Spec spec : allSpecs) {
            if ((mainFile != null && !mainFile.equals(spec.file))
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
        // Compare the full upstream suite with the generated manifest before
        // selection, including new mandatory files introduced upstream.
        java.util.Set<String> upstreamFiles = new java.util.LinkedHashSet<>();
        for (Path file : jsonFiles) upstreamFiles.add(file.getFileName().toString());
        java.util.Set<String> indexedFiles = new java.util.LinkedHashSet<>();
        for (OfficialCaseRunner.Spec spec : allSpecs) indexedFiles.add(spec.file);
        assertEquals(upstreamFiles, indexedFiles, "Official compiled manifest does not cover every file");
        jsonFiles.removeIf(p -> !byFile.containsKey(p.getFileName().toString()));
        if (specs.isEmpty()) {
            throw new IllegalArgumentException("No official cases matched: draft="
                    + draft + ", file=" + mainFile + ", group=" + mainGroup);
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
                Path schemaResource = generated.resolve("resources/official").resolve(draft)
                        .resolve(stem).resolve(String.format(java.util.Locale.ROOT,
                                "%03d.json", i));
                Object generatedSchema = Sjf4j.global().fromJson(Files.readAllBytes(schemaResource));
                if (!"draft2020-12".equals(draft)
                        && JsonType.of(officialSchema) == JsonType.OBJECT
                        && !Nodes.toJsonObject(officialSchema).containsKey("$schema")
                        && JsonType.of(generatedSchema) == JsonType.OBJECT) {
                    JsonObject original = Nodes.toJsonObject(officialSchema);
                    JsonObject compiled = Nodes.toJsonObject(generatedSchema);
                    String dialect = "draft7".equals(draft)
                            ? "http://json-schema.org/draft-07/schema#"
                            : "https://json-schema.org/draft/2019-09/schema";
                    assertEquals(dialect, compiled.getString("$schema"), spec.key());
                    assertEquals(original.size() + 1, compiled.size(), spec.key());
                    for (String key : original.keySet()) {
                        assertTrue(compiled.containsKey(key)
                                        && Nodes.equals(original.getNode(key), compiled.getNode(key)),
                                spec.key() + ": generated schema changed keyword " + key
                                        + ": original=" + original.getNode(key)
                                        + ", generated=" + compiled.getNode(key));
                    }
                } else {
                    assertTrue(Nodes.equals(officialSchema, generatedSchema),
                            fileName + " #" + i + ": generated schema differs from upstream");
                }

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
                        // A known fallback is never allowed to regress to an
                        // uninformative root-level failure. The processor must
                        // identify the keyword/combination or runtime-only mode.
                        if (directError != null) {
                            assertTrue(!directError.contains("unsupported schema construct or Java value projection")
                                            && !directError.contains("no complete direct predicate is available")
                                            && !directError.contains("unsupported schema construct requires runtime evaluation"),
                                    spec.key() + ": missing specific fallback reason: " + directError);
                        }
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
        Path moduleBuild = generated.getParent().getParent().getParent();
        Path reportBase = moduleBuild.resolve("reports/officialCompiled");
        Path compileBase = moduleBuild.resolve("officialCompiled/compile");
        if (!"draft2020-12".equals(draft)) {
            reportBase = reportBase.resolve(draft);
            compileBase = compileBase.resolve(draft);
        }
        reportDir = reportBase.resolve(selection);
        report = new OfficialCaseReport(selection, specs, assertions, selectedCounts);
        runner = new OfficialCaseRunner(generated, compileBase.resolve(selection));
        runner.compileAll(specs, true);
        return fileNodes.stream();
    }

    @AfterAll
    void writeReport() throws Exception {
        try {
            for (OfficialCompiledTest run : runs) run.writeSingleReport();
        } finally {
            for (OfficialCompiledTest run : runs) {
                if (run.runner != null) run.runner.close();
            }
        }
    }

    private void writeSingleReport() throws Exception {
        if (report == null) return;
        report.write(reportDir, revision);
        assertEquals(expectedTests, report.recordedTests(),
                draft + ": official compiled test executions were not all accounted for");
    }
}
