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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Required and diagnostic optional cases in the unified official JSON Schema Test Suite. */
public final class OfficialRuntimeTest {

    private static final String TEST_ROOT_PROPERTY = "sjf4j.schema.officialTestRoot";
    private static final String SUITE_DIRECTORY = "official-json-schema-tests/suite";
    private static final String VALIDATION_TESTS = "validation/tests";

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
        if (file != null) {
            Path path = Paths.get(file);
            if (!file.endsWith(".json") || file.contains("\\") || path.isAbsolute()
                    || !path.normalize().equals(path) || path.startsWith("..")
                    || (path.getNameCount() > 1 && !path.startsWith("optional"))) {
                throw new IllegalArgumentException("Expected a root or optional official file.json: " + file);
            }
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
            matched += report.required.totals().total() + report.supportedOptional.totals().total()
                    + report.optional.totals().total();
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
        runSuite(SchemaDialect.DRAFT_07);
    }

    @Test
    public void testDraft2019_09() throws Exception {
        runSuite(SchemaDialect.DRAFT_2019_09);
    }

    @Test
    public void testDraft2020_12() throws Exception {
        runSuite(SchemaDialect.DRAFT_2020_12);
    }

    private static void runSuite(SchemaDialect dialect) throws Exception {
        TestSuiteReport report = runTestDir(dialect,
                locatePath(locateTestRoot(), VALIDATION_TESTS), false, null, null, null);
        if (report.required.totals().total() == 0) {
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
            runTestFile(file, strict, suite, groupIndex, testIndex,
                    dir.relativize(file).startsWith("optional"));
        } else {
            try (Stream<Path> paths = Files.list(dir)) {
                for (Path file : paths.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".json"))
                        .sorted().toList()) {
                    runTestFile(file, strict, suite, groupIndex, testIndex, false);
                }
            }
            Path optional = dir.resolve("optional");
            if (Files.exists(optional)) {
                // Optional fixture I/O failures are diagnostic too.
                try (Stream<Path> paths = Files.walk(optional)) {
                    for (Path file : paths.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().endsWith(".json"))
                            .sorted().toList()) {
                        runTestFile(file, strict, suite, groupIndex, testIndex, true);
                    }
                } catch (Exception e) {
                    suite.optional.file("optional/").recordInputError("Cannot read optional directory", e);
                }
            }
        }
        printSuiteReport(suite);
        requirePassed(suite, "REQUIRED", suite.required.totals());
        requirePassed(suite, "SUPPORTED_OPTIONAL", suite.supportedOptional.totals());
        return suite;
    }

    private static void requirePassed(TestSuiteReport suite, String name, Counts required) {
        if (required.error() > 0 || required.failed() > 0
                || required.total() != required.passed() + required.failed() + required.error()) {
            throw new IllegalStateException("JSON Schema " + name + " validation failed for " + suite.dialect
                    + " in " + suite.directory + ": " + required.failed() + " failed and "
                    + required.error() + " errors, " + required.total() + " selected");
        }
    }

    private static void runTestFile(Path file, boolean strict, TestSuiteReport suite,
                                    Integer groupFilter, Integer testFilter, boolean optionalDirectory) {
        String fileName = suite.directory.relativize(file).toString().replace('\\', '/');
        // An unreadable root fixture may contain required cases; fail closed.
        CategoryReport defaultCategory = OfficialTestPolicy.FORMAT_VOCABULARY.equals(fileName)
                ? suite.supportedOptional : optionalDirectory ? suite.optional : suite.required;
        JsonArray groups;
        try {
            JsonObject source = Sjf4j.global().fromJson(Files.readAllBytes(file), JsonObject.class);
            groups = source.getJsonArray("tests");
            if (groups == null) throw new IllegalArgumentException("Missing root tests array: " + file);
        } catch (Exception e) {
            defaultCategory.file(fileName).recordInputError(fileName + " invalid test file", e);
            return;
        }
        for (int i = 0; i < groups.size(); i++) {
            if (groupFilter != null && groupFilter != i) continue;
            suite.sourceGroups++;
            CategoryReport category = defaultCategory;
            try {
                JsonObject group = groups.getJsonObject(i);
                String compatibility = group.getString("compatibility");
                if (!OfficialCompatibility.matches(compatibility, suite.dialect)) {
                    suite.incompatibleGroups++;
                    continue;
                }
                boolean assertion = OfficialTestPolicy.formatAssertion(group.getJsonArray("tags"));
                if (OfficialTestPolicy.supportedOptional(fileName, assertion)) category = suite.supportedOptional;
                // Vocabulary tests must assert without the override, exercising the schema's own dialect.
                boolean strictFormat = strict || assertion;
                JsonArray tests = group.getJsonArray("tests");
                if (tests == null || tests.size() == 0) {
                    throw new IllegalArgumentException("Missing tests in " + fileName + " group#" + i);
                }
                if (testFilter != null && testFilter >= tests.size()) {
                    throw new IllegalArgumentException(fileName + " group#" + i
                            + ": test index out of range: " + testFilter);
                }
                String groupDesc = group.getString("description", "no group desc");
                category.selectedGroups++;
                runGroup(suite.dialect, group, tests, category.file(fileName), i, groupDesc,
                        testFilter, strictFormat);
            } catch (Exception e) {
                // Do not let a malformed optional group hide subsequent required groups.
                category.file(fileName).recordInputError(fileName + " group#" + i + " invalid test group", e);
            }
        }
    }

    private static void runGroup(SchemaDialect dialect, JsonObject group, JsonArray tests,
                                 TestFileReport report, int groupIndex, String groupDesc,
                                 Integer testFilter, boolean strictFormat) {
        String label = report.fileName + " group#" + groupIndex + " (" + groupDesc + ")";
        int count = testFilter == null ? tests.size() : 1;
        report.total += count;
        SchemaPlan plan;
        try {
            // External resources are scoped to a single group, never the whole file.
            SchemaRegistry registry = new SchemaRegistry(dialect);
            Object external = group.getNode("externalSchemas");
            if (external != null) {
                Nodes.forEachObject(external, (uri, schema) ->
                        registry.index(URI.create(uri), JsonSchema.fromNode(schema)));
            }
            plan = JsonSchema.fromNode(group.getNode("schema")).createPlan(registry);
        } catch (Exception e) {
            report.error += count;
            report.addFailure(label + " compile error", e);
            return;
        }
        for (int j = 0; j < tests.size(); j++) {
            if (testFilter != null && testFilter != j) continue;
            String testLabel = label + " test#" + j;
            try {
                JsonObject example = tests.getJsonObject(j);
                testLabel += " (" + example.getString("description", "no test desc") + ")";
                Object data = example.get("data"); // JSON null is a real instance.
                boolean expected = example.getBoolean("valid");
                ValidationResult result = plan.validate(data, true, strictFormat);
                if (result.isValid() == expected) {
                    report.passed++;
                } else {
                    report.failed++;
                    report.addFailure(testLabel,
                            "expected=" + expected + ", actual=" + result.isValid() + ", " + result);
                }
            } catch (Exception e) {
                report.error++;
                report.addFailure(testLabel, e);
            }
        }
    }

    static final class TestSuiteReport {
        final SchemaDialect dialect;
        final Path directory;
        int sourceGroups, incompatibleGroups;
        final CategoryReport required = new CategoryReport();
        final CategoryReport supportedOptional = new CategoryReport();
        final CategoryReport optional = new CategoryReport();

        TestSuiteReport(SchemaDialect dialect, Path directory) {
            this.dialect = dialect;
            this.directory = directory;
        }
    }

    static final class CategoryReport {
        int selectedGroups;
        final Map<String, TestFileReport> fileMap = new LinkedHashMap<>();

        TestFileReport file(String name) {
            return fileMap.computeIfAbsent(name, TestFileReport::new);
        }

        Counts totals() {
            int total = 0, passed = 0, failed = 0, error = 0;
            for (TestFileReport file : fileMap.values()) {
                total += file.total;
                passed += file.passed;
                failed += file.failed;
                error += file.error;
            }
            return new Counts(total, passed, failed, error);
        }
    }

    record Counts(int total, int passed, int failed, int error) {}

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

        void recordInputError(String desc, Object error) {
            this.total++;
            this.error++;
            addFailure(desc, error);
        }
    }

    private static void printSuiteReport(TestSuiteReport suite) {
        System.out.println("======================================================================");
        System.out.println(" JSON Schema Runtime [" + suite.dialect + "]");
        System.out.println("======================================================================");
        System.out.println("Directory: " + suite.directory);
        System.out.println("Groups   : " + suite.sourceGroups + " source, "
                + suite.incompatibleGroups + " incompatible");
        System.out.printf("%-19s %8s %8s %8s %8s %8s%n",
                "Suite", "Groups", "Total", "Passed", "Failed", "Errors");
        printCategory("REQUIRED", suite.required);
        printCategory("SUPPORTED_OPTIONAL", suite.supportedOptional);
        printCategory("OPTIONAL", suite.optional);
        System.out.println("======================================================================");
        printFailures("REQUIRED", suite.required);
        printFailures("SUPPORTED_OPTIONAL", suite.supportedOptional);
        printFailures("OPTIONAL", suite.optional);
    }

    private static void printCategory(String name, CategoryReport category) {
        Counts counts = category.totals();
        System.out.printf("%-19s %8d %8d %8d %8d %8d%n", name, category.selectedGroups,
                counts.total(), counts.passed(), counts.failed(), counts.error());
    }

    private static void printFailures(String name, CategoryReport category) {
        for (TestFileReport report : category.fileMap.values()) {
            if (report.failed == 0 && report.error == 0) continue;
            System.out.printf("[%s] %s [ %d / %d ] Failed: %d Errors: %d%n",
                    name, report.fileName, report.passed, report.total, report.failed, report.error);
            for (String failure : report.failures) System.out.println("   - " + failure);
        }
    }
}
