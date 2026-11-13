package org.sjf4j.testbench.schema.official;

import org.junit.jupiter.api.Test;
import org.sjf4j.JsonArray;
import org.sjf4j.JsonObject;
import org.sjf4j.Nodes;
import org.sjf4j.Sjf4j;
import org.sjf4j.schema.JsonSchema;
import org.sjf4j.schema.SchemaDialect;
import org.sjf4j.schema.SchemaPlan;
import org.sjf4j.schema.SchemaRegistry;
import org.sjf4j.schema.ValidationResult;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Required validation cases in the unified official JSON Schema Test Suite. */
public final class OfficialRuntimeTest {

    private static final String TEST_ROOT_PROPERTY = "sjf4j.schema.officialTestRoot";
    private static final String SUITE_DIRECTORY = "official-json-schema-tests/suite";
    private static final String VALIDATION_TESTS = "validation/tests";

    private enum Selection { REQUIRED, OPTIONAL_TAGGED, OPTIONAL_DIRECTORY }


    /**
     * Program arguments: [dialect [file.json [groupIndex [testIndex]]]].
     * Group indices refer to the original upstream file, before filtering.
     */
    static void runStandalone(String[] args) throws Exception {
        if (args.length > 4) {
            throw new IllegalArgumentException(
                    "Usage: OfficialRuntimeMain [dialect [file.json [groupIndex [testIndex]]]]");
        }
        List<SchemaDialect> dialects = standaloneDialects(args.length == 0 ? null : args[0]);
        String file = args.length > 1 ? args[1] : null;
        Integer group = args.length > 2 ? Integer.parseInt(args[2]) : null;
        Integer test = args.length > 3 ? Integer.parseInt(args[3]) : null;
        if (file != null && (!file.endsWith(".json") || file.contains("/") || file.contains("\\"))) {
            throw new IllegalArgumentException("Expected a root official file.json: " + file);
        }
        if (group != null && (group < 0 || file == null)) {
            throw new IllegalArgumentException("Specify file.json and a nonnegative group index");
        }
        if (test != null && (test < 0 || group == null)) {
            throw new IllegalArgumentException("Specify group index and a nonnegative test index");
        }
        Path dir = locatePath(locateTestRoot(), VALIDATION_TESTS);
        int matched = 0;
        for (SchemaDialect dialect : dialects) {
            TestSuiteReport report = runTestDir(dialect, dir, false, file, group, test);
            matched += report.total;
        }
        if (matched == 0) {
            throw new IllegalArgumentException("No compatible official validation tests matched the selection");
        }
    }

    static List<SchemaDialect> standaloneDialects(String dialect) {
        if (dialect == null || "all".equals(dialect)) {
            return List.of(SchemaDialect.DRAFT_07,
                    SchemaDialect.DRAFT_2019_09, SchemaDialect.DRAFT_2020_12);
        }
        switch (dialect) {
            case "draft7": return List.of(SchemaDialect.DRAFT_07);
            case "draft2019-09": return List.of(SchemaDialect.DRAFT_2019_09);
            case "draft2020-12": return List.of(SchemaDialect.DRAFT_2020_12);
            default: throw new IllegalArgumentException("Invalid official dialect: " + dialect);
        }
    }

    @Test
    public void testDraft7() throws Exception {
        runRequired(SchemaDialect.DRAFT_07);
    }

    @Test
    public void testDraft2019_09() throws Exception {
        runRequired(SchemaDialect.DRAFT_2019_09);
    }

    @Test
    public void testDraft2020_12() throws Exception {
        runRequired(SchemaDialect.DRAFT_2020_12);
    }

    private static void runRequired(SchemaDialect dialect) throws Exception {
        TestSuiteReport report = runTestDir(dialect,
                locatePath(locateTestRoot(), VALIDATION_TESTS), false, null, null, null);
        if (report.total == 0) {
            throw new IllegalStateException("Official validation suite matched no required tests for " + dialect);
        }
    }

    private static Path locatePath(Path root, String dir) {
        Path path = root.resolve(dir);
        if (!Files.isDirectory(path)) {
            throw new IllegalStateException("Not found " + dir + " under " + root);
        }
        return path;
    }

    private static Path locateTestRoot() {
        String root = System.getProperty(TEST_ROOT_PROPERTY);
        if (root != null && !root.isEmpty()) return Paths.get(root);
        Path moduleRoot = Paths.get("build", SUITE_DIRECTORY);
        if (Files.isDirectory(moduleRoot)) return moduleRoot;
        Path repositoryRoot = Paths.get("sjf4j-testbench", "build", SUITE_DIRECTORY);
        if (Files.isDirectory(repositoryRoot)) return repositoryRoot;
        throw new IllegalStateException("Official JSON Schema test suite not found. Run "
                + "./gradlew :sjf4j-testbench:downloadOfficialSchemaTests, or supply -D"
                + TEST_ROOT_PROPERTY + "=<suite-root>.");
    }

    /** Package-private to exercise the runner with local miniature suites. */
    static TestSuiteReport runTestDir(SchemaDialect dialect, Path dir, boolean strict,
                                      String fileName, Integer groupIndex, Integer testIndex)
            throws Exception {
        TestSuiteReport suite = new TestSuiteReport(dialect, dir);
        if (fileName != null) {
            Path file = dir.resolve(fileName);
            if (!Files.isRegularFile(file)) {
                throw new IllegalArgumentException("Official test file not found: " + file);
            }
            runTestFile(dialect, file, strict, suite, groupIndex, testIndex, Selection.REQUIRED);
        } else {
            try (Stream<Path> paths = Files.list(dir)) {
                for (Path file : paths.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".json"))
                        .sorted().toList()) {
                    runTestFile(dialect, file, strict, suite, groupIndex, testIndex, Selection.REQUIRED);
                }
            }
        }
        printSuiteReport(suite);
        if (suite.error > 0 || suite.failed > 0 || suite.total != suite.passed + suite.failed + suite.error) {
            throw new IllegalStateException("JSON Schema Official validation failed for " + suite.dialect
                    + " in " + suite.directory + ": " + suite.failed + " failed and "
                    + suite.error + " errors, " + suite.total + " selected");
        }
        return suite;
    }

    /**
     * Diagnostic-only optional suite: include root-level format-assertion cases
     * and every test file in the official validation/tests/optional directory.
     * The return value records mismatches and execution errors without throwing
     * for them. Missing input fixtures remain a test-harness error.
     */
    static TestSuiteReport runOptional(SchemaDialect dialect) throws Exception {
        return runOptionalTestDir(dialect, locatePath(locateTestRoot(), VALIDATION_TESTS));
    }

    static TestSuiteReport runOptionalTestDir(SchemaDialect dialect, Path dir) throws Exception {
        Path optional = dir.resolve("optional");
        if (!Files.isDirectory(optional)) {
            throw new IllegalStateException("Official optional suite missing: " + optional);
        }
        TestSuiteReport suite = new TestSuiteReport(dialect, dir);
        try (Stream<Path> paths = Files.list(dir)) {
            for (Path file : paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted().toList()) {
                runTestFile(dialect, file, false, suite, null, null, Selection.OPTIONAL_TAGGED);
            }
        }
        try (Stream<Path> paths = Files.walk(optional)) {
            for (Path file : paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted().toList()) {
                runTestFile(dialect, file, false, suite, null, null, Selection.OPTIONAL_DIRECTORY);
            }
        }
        printSuiteReport(suite);
        if (suite.total == 0) {
            throw new IllegalStateException("Official optional suite matched no cases for " + dialect);
        }
        return suite;
    }

    private static void runTestFile(SchemaDialect dialect, Path file, boolean strict,
                                    TestSuiteReport suite, Integer groupFilter, Integer testFilter, Selection selection) {
        String fileName = suite.directory.relativize(file).toString().replace('\\', '/');
        TestFileReport report = suite.file(fileName);
        try {
            JsonObject source = Sjf4j.global().fromJson(Files.readAllBytes(file), JsonObject.class);
            JsonArray groups = source.getJsonArray("tests");
            if (groups == null) throw new IllegalArgumentException("Missing root tests array: " + file);
            for (int i = 0; i < groups.size(); i++) {
                if (groupFilter != null && groupFilter != i) continue;
                suite.sourceGroups++;
                JsonObject group = groups.getJsonObject(i);
                String groupDesc = group.getString("description", "no group desc");
                String compatibility = group.getString("compatibility");
                if (!OfficialCompatibility.matches(compatibility, dialect)) {
                    suite.incompatibleGroups++;
                    continue;
                }
                JsonArray tags = group.getJsonArray("tags");
                if (!runWithTags(tags, selection)) {
                    suite.taggedGroups++;
                    continue;
                }
                JsonArray tests = group.getJsonArray("tests");
                if (tests == null || tests.size() == 0) {
                    throw new IllegalArgumentException("Missing tests in " + fileName + " group#" + i);
                }
                if (testFilter != null && testFilter >= tests.size()) {
                    throw new IllegalArgumentException(fileName + " group#" + i
                            + ": test index out of range: " + testFilter);
                }
                suite.selectedGroups++;
                int count = testFilter == null ? tests.size() : 1;
                report.total += count;
                suite.total += count;

                SchemaPlan plan;
                try {
                    // External resources are scoped to a single test group, not an entire
                    // file or dialect: the same URI may have different definitions.
                    SchemaRegistry registry = new SchemaRegistry(dialect);
                    Object external = group.getNode("externalSchemas");
                    if (external != null) {
                        Nodes.forEachObject(external, (uri, schema) ->
                                registry.index(URI.create(uri), JsonSchema.fromNode(schema)));
                    }
                    plan = JsonSchema.fromNode(group.getNode("schema")).createPlan(registry);
                } catch (Exception e) {
                    report.error += count;
                    suite.error += count;
                    report.addFailure(fileName + " group#" + i + " (" + groupDesc + ") compile error", e);
                    continue;
                }

                for (int j = 0; j < tests.size(); j++) {
                    if (testFilter != null && testFilter != j) continue;
                    JsonObject example = tests.getJsonObject(j);
                    String desc = example.getString("description", "no test desc");
                    try {
                        Object data = example.get("data"); // JSON null is a real instance.
                        boolean expected = example.getBoolean("valid");
                        boolean strictFormat = strict || (selection != Selection.REQUIRED
                                && (hasFormatAssertion(tags)
                                    || fileName.equals("optional/format-assertion.json")));
                        ValidationResult result = plan.validate(data, true, strictFormat);
                        if (result.isValid() == expected) {
                            report.passed++;
                            suite.passed++;
                        } else {
                            report.failed++;
                            suite.failed++;
                            report.addFailure(fileName + " group#" + i + " (" + groupDesc
                                    + ") test#" + j + " (" + desc + ")",
                                    "expected=" + expected + ", actual=" + result.isValid() + ", " + result);
                        }
                    } catch (Exception e) {
                        report.error++;
                        suite.error++;
                        report.addFailure(fileName + " group#" + i + " test#" + j + " (" + desc + ")", e);
                    }
                }
            }
        } catch (Exception e) {
            report.error++;
            suite.error++;
            report.total++;
            suite.total++;
            report.addFailure(fileName + " invalid test file", e);
        }

        if (selection == Selection.REQUIRED || report.total > 0) {
            String line = report.failed == 0 && report.error == 0
                    ? String.format("✓ %-35s [ %4d / %4d ]", fileName, report.passed, report.total)
                    : String.format("✗ %-35s [ %4d / %4d ] Failed: %d Error: %d",
                        fileName, report.passed, report.total, report.failed, report.error);
            System.out.println(line);
            for (String failure : report.failures) System.out.println("   - " + failure);
        }
    }

    private static boolean hasFormatAssertion(JsonArray tags) {
        if (tags == null) return false;
        boolean assertion = false;
        for (int i = 0; i < tags.size(); i++) {
            Object tag = tags.get(i);
            if ("format-assertion".equals(tag)) assertion = true;
            else if (!"format-annotation".equals(tag)) {
                throw new IllegalArgumentException("Unknown official validation tag: " + tag);
            }
        }
        return assertion;
    }

    private static boolean runWithTags(JsonArray tags, Selection selection) {
        boolean assertion = hasFormatAssertion(tags);
        switch (selection) {
            case REQUIRED: return !assertion;
            case OPTIONAL_TAGGED: return assertion;
            case OPTIONAL_DIRECTORY: return true;
            default: throw new IllegalStateException("Unexpected official selection: " + selection);
        }
    }

    static final class TestSuiteReport {
        final SchemaDialect dialect;
        final Path directory;
        int sourceGroups, selectedGroups, incompatibleGroups, taggedGroups;
        int total, passed, failed, error;
        final Map<String, TestFileReport> fileMap = new LinkedHashMap<>();

        TestSuiteReport(SchemaDialect dialect, Path directory) {
            this.dialect = dialect;
            this.directory = directory;
        }

        TestFileReport file(String name) {
            return fileMap.computeIfAbsent(name, TestFileReport::new);
        }

        Collection<TestFileReport> files() {
            return fileMap.values();
        }
    }

    static final class TestFileReport {
        final String fileName;
        int total, passed, failed, error;
        final List<String> failures = new ArrayList<>();

        TestFileReport(String name) {
            this.fileName = name;
        }

        void addFailure(String desc, Object error) {
            failures.add(desc + " : " + error);
        }
    }

    private static void printSuiteReport(TestSuiteReport suite) {
        System.out.println("================================");
        System.out.println(" JSON Schema Test Summary [" + suite.dialect + "]");
        System.out.println("================================");
        System.out.println("Directory: " + suite.directory);
        System.out.println("Groups   : " + suite.sourceGroups + " source, " + suite.selectedGroups
                + " selected, " + suite.incompatibleGroups + " incompatible, "
                + suite.taggedGroups + " tagged");
        System.out.println("Total    : " + suite.total);
        System.out.println("Passed   : " + suite.passed);
        System.out.println("Failed   : " + suite.failed);
        if (suite.error > 0) System.out.println("Error    : " + suite.error);
        System.out.println("================================\n");
    }
}
